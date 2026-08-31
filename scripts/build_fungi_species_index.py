#!/usr/bin/env python3
"""Build data/species-index/fungi-us-species-index.json from iNaturalist.

One record per taxon: scientific name, all iNat common names, taxon ID, and
US research-grade observation count. See data/species-index/README.md for
the field definitions and data/species-index/ascomycete-inclusion.md for the
taxonomic scope this script encodes.

Re-run this to refresh counts or extend scope (e.g. adding another
Ascomycete genus to ASCOMYCETE_GENUS_IDS) as the curated index grows over
time. Pass --report-only to regenerate the distribution report from an
already-fetched data/species-index/_raw/ cache without re-querying the API.

Rate limit: iNaturalist's own docs (the `info.description` field of
https://api.inaturalist.org/v1/swagger.json, fetched live rather than
assumed) say: "we throttle API usage to a max of 100 requests per minute,
though we ask that you try to keep it to 60 requests per minute or lower,
and to keep under 10,000 requests per day." This script paces requests at
roughly 1/1.1s (~55/min) and does a full run (pulls + all bulk name lookups)
in well under 100 requests.
"""
import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

BASE = "https://api.inaturalist.org/v1"
USER_AGENT = (
    "ForagerSpeciesIndexBuilder/1.0 "
    "(+https://github.com/slayer8366/Forager; contact: cultivation.oms@gmail.com)"
)
REQUEST_DELAY_SECONDS = 1.1  # ~55/min, under iNat's own "60/min or lower" ask

PLACE_ID_US = 1  # iNat place "United States" (country level) -- confirmed via
# /v1/places/autocomplete?q=United%20States, not assumed.

# Ancestor-filter scope. Every ID here was resolved against the live API
# (/v1/taxa?q=..., cross-checked by ancestor_ids), not guessed.
BASIDIOMYCOTA_ID = 47169
EXCLUDE_CLASS_IDS = {
    69967: "Pucciniomycetes (rusts)",
    83712: "Ustilaginomycetes (smuts)",
}
ASCOMYCETE_GENUS_IDS = {
    56830: "Morchella (morels)",
    120278: "Tuber (truffles)",
    49136: "Sarcoscypha (cup fungi)",
    55268: "Xylaria",
    58707: "Cordyceps",
}

REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = REPO_ROOT / "data" / "species-index"
RAW_DIR = OUT_DIR / "_raw"

_last_call = [0.0]


def _get(path, params, retries=5):
    qs = urllib.parse.urlencode(params, doseq=True)
    url = f"{BASE}{path}?{qs}"
    for attempt in range(retries):
        elapsed = time.monotonic() - _last_call[0]
        if elapsed < REQUEST_DELAY_SECONDS:
            time.sleep(REQUEST_DELAY_SECONDS - elapsed)
        _last_call[0] = time.monotonic()
        req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read())
        except (urllib.error.URLError, TimeoutError) as e:
            wait = 2 ** attempt
            print(f"  [retry {attempt + 1}/{retries}] {e} -- waiting {wait}s", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError(f"Failed after {retries} retries: {url}")


def fetch_species_counts(taxon_ids, without_taxon_ids, label):
    results = {}
    page = 1
    per_page = 500
    total = None
    while True:
        params = {
            "place_id": PLACE_ID_US,
            "taxon_id": ",".join(str(t) for t in taxon_ids),
            "quality_grade": "research",
            "per_page": per_page,
            "page": page,
        }
        if without_taxon_ids:
            params["without_taxon_id"] = ",".join(str(t) for t in without_taxon_ids)
        data = _get("/observations/species_counts", params)
        total = data["total_results"]
        got = data["results"]
        print(f"[{label}] page {page}: {len(got)} results (total_results={total})", file=sys.stderr)
        for row in got:
            taxon = row["taxon"]
            results[taxon["id"]] = {"id": taxon["id"], "name": taxon["name"], "rank": taxon.get("rank"), "count": row["count"]}
        if len(got) < per_page:
            break
        page += 1
    assert len(results) == total, f"[{label}] expected {total} distinct taxa, got {len(results)}"
    return results


def fetch_all_names(taxon_ids, chunk_size=200):
    names_by_id = {}
    ids = list(taxon_ids)
    for i in range(0, len(ids), chunk_size):
        chunk = ids[i:i + chunk_size]
        params = {"id": ",".join(str(t) for t in chunk), "all_names": "true", "per_page": chunk_size}
        data = _get("/taxa", params)
        got_ids = set()
        for taxon in data["results"]:
            got_ids.add(taxon["id"])
            common, seen = [], set()
            for n in taxon.get("names", []) or []:
                if n.get("lexicon") == "scientific-names":
                    continue
                nm = n.get("name")
                if nm and nm not in seen:
                    seen.add(nm)
                    common.append(nm)
            names_by_id[taxon["id"]] = common
        missing = set(chunk) - got_ids
        if missing:
            print(f"  WARNING: no /taxa result for ids {sorted(missing)}", file=sys.stderr)
        print(f"[names] {i + len(chunk)}/{len(ids)} taxa looked up", file=sys.stderr)
    return names_by_id


def pull_data():
    print("=== Pull A: Basidiomycota minus rusts/smuts ===", file=sys.stderr)
    pull_a = fetch_species_counts([BASIDIOMYCOTA_ID], EXCLUDE_CLASS_IDS.keys(), "basidiomycota")

    print("=== Pull B: Ascomycete inclusion genera ===", file=sys.stderr)
    pull_b = fetch_species_counts(ASCOMYCETE_GENUS_IDS.keys(), None, "ascomycota-included")

    overlap = set(pull_a) & set(pull_b)
    assert not overlap, f"Unexpected overlap between pulls: {overlap}"

    merged = {}
    for tid, rec in pull_a.items():
        merged[tid] = {**rec, "source_group": "basidiomycota"}
    for tid, rec in pull_b.items():
        merged[tid] = {**rec, "source_group": "ascomycota-included"}

    print(f"=== Merged: {len(merged)} distinct taxa ===", file=sys.stderr)

    print("=== Fetching full common-names lists ===", file=sys.stderr)
    names_by_id = fetch_all_names(merged.keys())

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    (RAW_DIR / "merged_counts.json").write_text(json.dumps(merged))
    (RAW_DIR / "names_by_id.json").write_text(json.dumps(names_by_id))
    return merged, names_by_id


def build_index(merged, names_by_id):
    records = []
    for tid, rec in merged.items():
        records.append({
            "taxon_id": tid,
            "scientific_name": rec["name"],
            "common_names": names_by_id.get(str(tid), names_by_id.get(tid, [])),
            "observation_count": rec["count"],
        })
    records.sort(key=lambda r: (-r["observation_count"], r["scientific_name"]))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUT_DIR / "fungi-us-species-index.json", "w") as f:
        json.dump(records, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return records


def write_distribution_report(records, merged):
    counts = [r["observation_count"] for r in records]
    counts_sorted = sorted(counts, reverse=True)
    n = len(counts)
    lines = [
        "# Observation-count distribution -- US fungi species index",
        "",
        f"Generated from {n} taxa (US, research-grade). Regenerate with "
        "`python3 scripts/build_fungi_species_index.py`.",
        "",
        "## Summary",
        "",
        f"- Total taxa: {n}",
        f"- min={min(counts)} max={max(counts)} mean={statistics.mean(counts):.1f} "
        f"median={statistics.median(counts)}",
        "",
        "## Percentiles (count at each percentile, ranked by observation count)",
        "",
        "| percentile | count |",
        "| --- | ---: |",
    ]
    for pct in [1, 5, 10, 25, 50, 75, 90, 95, 99]:
        idx = min(n - 1, int(n * pct / 100))
        lines.append(f"| p{pct} | {counts_sorted[idx]} |")

    lines += ["", "## How many taxa survive each candidate threshold", "", "| threshold (>=) | taxa kept | % of index |", "| ---: | ---: | ---: |"]
    for thresh in [1, 2, 5, 10, 25, 50, 100, 200, 500, 1000]:
        kept = sum(1 for c in counts if c >= thresh)
        lines.append(f"| {thresh} | {kept} | {100 * kept / n:.1f}% |")

    lines += ["", "## What sits just above/below a few candidate cutoffs", ""]
    for thresh in [5, 10, 25, 50]:
        above = sorted((r for r in records if r["observation_count"] >= thresh), key=lambda r: r["observation_count"])[:5]
        below = sorted((r for r in records if r["observation_count"] < thresh), key=lambda r: -r["observation_count"])[:5]
        lines.append(f"### Threshold {thresh}")
        lines.append("")
        lines.append(f"Kept just above/at the line ({sum(1 for c in counts if c >= thresh)} total kept):")
        for r in above:
            cn = r["common_names"][0] if r["common_names"] else "(no common name on file)"
            lines.append(f"- {r['observation_count']} -- *{r['scientific_name']}* -- {cn}")
        lines.append("")
        lines.append(f"Dropped just below the line ({sum(1 for c in counts if c < thresh)} total dropped):")
        for r in below:
            cn = r["common_names"][0] if r["common_names"] else "(no common name on file)"
            lines.append(f"- {r['observation_count']} -- *{r['scientific_name']}* -- {cn}")
        lines.append("")

    groups = Counter(rec["source_group"] for rec in merged.values())
    lines += ["## Source-group split", ""]
    for k, v in groups.items():
        lines.append(f"- `{k}`: {v} taxa")

    (OUT_DIR / "observation-count-distribution.md").write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report-only", action="store_true", help="Skip API pulls, reuse data/species-index/_raw/ cache")
    args = parser.parse_args()

    if args.report_only:
        merged = json.loads((RAW_DIR / "merged_counts.json").read_text())
        merged = {int(k): v for k, v in merged.items()}
        names_by_id = json.loads((RAW_DIR / "names_by_id.json").read_text())
    else:
        merged, names_by_id = pull_data()

    records = build_index(merged, names_by_id)
    write_distribution_report(records, merged)
    print(f"DONE -- wrote {len(records)} taxa to {OUT_DIR / 'fungi-us-species-index.json'}", file=sys.stderr)


if __name__ == "__main__":
    main()
