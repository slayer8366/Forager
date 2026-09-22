"""check_record.py -- validator for this repository's RECORD.md.

RECONSTRUCTED, not recovered, and the distinction matters enough to
spell out where the line falls.

A version of this file existed at commit 5d7af96 in this repository.
That commit no longer exists on any branch or remote reachable from
here -- see DURABILITY.md for how. The only surviving fragment is
ledger_validator_core.py in a separate project (OSCam, ~/imx on the
machine this was rebuilt on), which vendored nine functions from
check_record.py at 5d7af96 (plus their real dependencies:
MergeCommitEncountered, the ID_RE/FIELD_RE/ENTRY_SPLIT_RE regexes,
SENTINELS, and _first_divergent_line) for its own LEDGER.md format.
That vendoring recorded, at the time, that the nine functions' bodies
were byte-identical to these originals, confirmed by direct diff
against 5d7af96. That diff cannot be re-run -- 5d7af96 is gone -- so
this file's core logic below is copied from the vendored copy on
trust in that prior verification, not on a comparison performed now.
Say that plainly rather than calling this a recovery.

Two module-level constants could not be restored to their original
values and are flagged rather than guessed at. The vendoring header
in ledger_validator_core.py directly quotes two field labels as
originals -- "Prediction (outcome — planner)" and "Prediction
(mechanism — coder)" -- and those are used exactly below. It also
claims TERMINAL_REQUIRED "differs in content from the original" but
never quotes what that content was; and SUPPLIED_FIELD is a real
dependency of validate_entries that was never named in the vendoring
header's own dependency list at all (the same shape of omission that
header itself flags for _first_divergent_line, except that one was
caught and this one wasn't, here, now). TERMINAL_REQUIRED's non-
prediction field names and SUPPLIED_FIELD are therefore carried
forward UNCHANGED from the LEDGER.md-adapted values, not restored --
if this repository's real RECORD.md ever used different text for
either, this file is wrong until someone who remembers or can find
the original corrects it.

The functions below (split_entries, parse_fields, validate_entries,
check_superseded_by, check_duplicate_terminals, check_append_only,
check_history_append_only, _commit_log_for, _show_bytes) plus
MergeCommitEncountered, the three regexes, SENTINELS, and
_first_divergent_line are otherwise unmodified from the vendored
copy: only RECORD_NAME and the two quoted field labels differ from
what ledger_validator_core.py carries, and that difference is
configuration, not logic.

main() and render_check() below are NEW CONSTRUCTION, modeled on
OSCam's ledger_cli.py (itself new construction there, since the
original main()/render_check() were never vendored) but not a copy of
anything that survives. Their argument surface, output format, and
check ordering are this file's own design, not a recovery of what
check_record.py's CLI used to look like.
"""
import re
import subprocess
import sys
import tempfile
from pathlib import Path

RECORD_NAME = "RECORD.md"

# See module docstring: the two prediction field labels are restored
# from a direct quotation in the vendoring header. TERMINAL_REQUIRED's
# other field names are NOT restored -- carried forward unchanged from
# the LEDGER.md-adapted list, flagged as unconfirmed.
INTENT_REQUIRED = [
    "Kind", "ID", "Timestamp", "Title", "Change", "Scope boundary",
    "Baseline", "Prediction (outcome — planner)",
    "Prediction (mechanism — coder)", "Finish line", "Abort conditions",
]
TERMINAL_REQUIRED = [
    "Kind", "ID", "Timestamp", "Closes", "Outcome", "Observed", "Deviations",
]

ID_RE = re.compile(r"^\d{4}-\d{2}-\d{2}-\d{2}$")
FIELD_RE = re.compile(r"^\*\*(.+?):\*\*\s?(.*)$")
ENTRY_SPLIT_RE = re.compile(r"(?m)^---$")

# Sentinels: permitted only as the exact, complete value of a prediction
# field in an INTENT entry. 'withheld' is a timing state -- the value
# exists but has not been released to the executor yet, and is expected
# to be appended later as its own field. 'not authored' is permanent --
# the value was never written and appending one later would mean it was
# fabricated after the outcome, which is the specific act the store
# exists to prevent. Never collapse the two into one "missing" state.
SENTINELS = {"withheld", "not authored"}
SENTINEL_PREDICTION_FIELDS = {
    "Prediction (outcome — planner)",
    "Prediction (mechanism — coder)",
}
OUTCOME_FIELD = "Prediction (outcome — planner)"
# Not restored -- see module docstring. Carried forward unchanged from
# the LEDGER.md-adapted value; no quoted original exists to replace it.
SUPPLIED_FIELD = "Prediction-outcome-supplied"


def split_entries(text):
    """Raw text blocks for each entry, found after the '## Entries'
    heading and separated by bare '---' lines. Header/format
    documentation above that heading is never entry content."""
    marker = "## Entries"
    idx = text.find(marker)
    if idx == -1:
        return []
    body = text[idx + len(marker):]
    parts = ENTRY_SPLIT_RE.split(body)
    return [p.strip() for p in parts if p.strip()]


def parse_fields(block):
    """One entry block -> ordered dict of label -> value text. A field's
    value runs from immediately after its '**Label:**' line to the next
    such line or the block's end; this is why a value may span multiple
    lines without any indentation convention."""
    fields = {}
    label = None
    value_lines = []
    for line in block.splitlines():
        m = FIELD_RE.match(line)
        if m:
            if label is not None:
                fields[label] = "\n".join(value_lines).strip()
            label = m.group(1).strip()
            rest = m.group(2)
            value_lines = [rest] if rest.strip() else []
        elif label is not None:
            value_lines.append(line)
    if label is not None:
        fields[label] = "\n".join(value_lines).strip()
    return fields


def validate_entries(text):
    """Returns (entries, errors, unterminated_ids, sentinel_reports).
    errors is a list of human-readable violation strings. unterminated_ids
    and sentinel_reports are both reported, never on their own a cause of
    failure -- except one specific combination folded into errors: a
    permanent 'not authored' sentinel paired with a later-supplied value,
    which means a prediction was fabricated after the outcome.

    sentinel_reports is a list of {id, field, sentinel, status} dicts,
    status one of 'pending' or 'resolved'."""
    errors = []
    entries = []
    seen_ids = {}
    sentinel_reports = []

    for i, block in enumerate(split_entries(text)):
        fields = parse_fields(block)
        kind = fields.get("Kind", "").strip()
        entry_id = fields.get("ID", "").strip()
        label_for_errors = entry_id or f"entry #{i + 1} (no ID)"

        if kind not in ("intent", "terminal"):
            errors.append(f"{label_for_errors}: missing or invalid Kind "
                          f"(got {kind!r})")
            continue

        if not entry_id:
            errors.append(f"{kind} {label_for_errors}: missing required "
                          f"field 'ID'")
        elif not ID_RE.match(entry_id):
            errors.append(f"{kind} {entry_id}: malformed ID (expected "
                          f"YYYY-MM-DD-NN)")

        required = INTENT_REQUIRED if kind == "intent" else TERMINAL_REQUIRED
        for req_label in required:
            value = fields.get(req_label, "").strip()
            if not value:
                errors.append(f"{kind} {label_for_errors}: missing required "
                              f"field {req_label!r}")
            elif (kind == "intent" and req_label in SENTINEL_PREDICTION_FIELDS
                  and value in SENTINELS):
                sentinel_reports.append({
                    "id": entry_id, "field": req_label, "sentinel": value,
                    "status": "pending",
                })

        if kind == "terminal":
            for field_name, field_value in fields.items():
                if field_value.strip() in SENTINELS:
                    errors.append(
                        f"terminal {label_for_errors}: field {field_name!r} "
                        f"carries sentinel {field_value.strip()!r} -- "
                        f"sentinels are not permitted in terminal entries, "
                        f"since nothing there can legitimately be "
                        f"outstanding or absent")
            outcome = fields.get("Outcome", "").strip()
            if outcome == "superseded" and not fields.get("Superseded-by", "").strip():
                errors.append(f"terminal {label_for_errors}: outcome "
                              f"'superseded' requires field 'Superseded-by'")
            if outcome == "abandoned" and not fields.get("Working-state", "").strip():
                errors.append(f"terminal {label_for_errors}: outcome "
                              f"'abandoned' requires field 'Working-state'")

        if entry_id:
            if entry_id in seen_ids:
                errors.append(f"duplicate ID {entry_id}: used by entry "
                              f"#{seen_ids[entry_id] + 1} and entry #{i + 1}")
            else:
                seen_ids[entry_id] = i

        entries.append({"kind": kind, "id": entry_id, "fields": fields})

    intent_ids = {e["id"] for e in entries if e["kind"] == "intent" and e["id"]}
    closed_ids = set()
    for e in entries:
        if e["kind"] != "terminal":
            continue
        closes = e["fields"].get("Closes", "").strip()
        if not closes:
            continue
        if closes not in intent_ids:
            errors.append(f"terminal {e['id']}: Closes names no existing "
                          f"intent ID (closes={closes!r})")
        else:
            closed_ids.add(closes)

    # A later-supplied outcome prediction cannot be written into the
    # sentinel's own intent entry -- that entry is already committed, and
    # append-only is enforced by byte-prefix, so nothing can be inserted
    # into an already-committed block; only new blocks may be appended.
    # Prediction-outcome-supplied therefore lives in the TERMINAL entry
    # that closes the intent, cross-referenced via Closes, the same way
    # Closes itself already links a terminal entry back to an intent one.
    intents_by_id = {e["id"]: e for e in entries if e["kind"] == "intent" and e["id"]}
    for e in entries:
        if e["kind"] != "terminal":
            continue
        supplied_value = e["fields"].get(SUPPLIED_FIELD, "").strip()
        if not supplied_value:
            continue
        closes = e["fields"].get("Closes", "").strip()
        intent = intents_by_id.get(closes)
        if intent is None:
            continue  # already reported above as a bad Closes reference
        outcome_value = intent["fields"].get(OUTCOME_FIELD, "").strip()
        if outcome_value not in SENTINELS:
            continue  # nothing to resolve; not validated further, see report
        if outcome_value == "not authored":
            errors.append(
                f"terminal {e['id']}: closes intent {closes} whose "
                f"{OUTCOME_FIELD!r} is 'not authored' (permanent), but "
                f"carries a {SUPPLIED_FIELD!r} field -- a permanent "
                f"sentinel is never resolved; that combination means the "
                f"prediction was fabricated after the outcome")
        else:  # withheld, legitimately resolved
            for report in sentinel_reports:
                if report["id"] == closes and report["field"] == OUTCOME_FIELD:
                    report["status"] = "resolved"

    unterminated = sorted(intent_ids - closed_ids)
    return entries, errors, unterminated, sentinel_reports


def check_superseded_by(entries):
    """(ok, errors, vacuous). Cross-references every terminal entry's
    Superseded-by value (present when Outcome is 'superseded') against
    the set of existing intent entry IDs. Vacuous when no terminal entry
    in the store declares Outcome: superseded at all -- there is
    nothing to cross-reference, not a check that ran and found nothing
    wrong."""
    intent_ids = {e["id"] for e in entries if e["kind"] == "intent" and e["id"]}
    declared = [e for e in entries if e["kind"] == "terminal"
               and e["fields"].get("Outcome", "").strip() == "superseded"]
    if not declared:
        return True, [], True

    errs = []
    for e in declared:
        sup = e["fields"].get("Superseded-by", "").strip()
        if sup and sup not in intent_ids:
            errs.append(f"terminal {e['id']}: Superseded-by names no "
                       f"existing intent ID (superseded-by={sup!r})")
    return not errs, errs, False


def check_duplicate_terminals(entries):
    """(ok, errors). More than one terminal entry closing the same
    intent ID is an error, not a report -- unlike unterminated (a
    normal in-progress state), multiply-terminated is not a state any
    correct process produces. Names every colliding entry ID, not just
    the second one, so an operator seeing a duplicate knows whether
    there are two or five."""
    by_closes = {}
    for e in entries:
        if e["kind"] != "terminal":
            continue
        closes = e["fields"].get("Closes", "").strip()
        if not closes:
            continue
        by_closes.setdefault(closes, []).append(e["id"])

    errs = []
    for closes, ids in sorted(by_closes.items()):
        if len(ids) > 1:
            errs.append(f"intent {closes} is closed by {len(ids)} terminal "
                       f"entries, not one: {', '.join(ids)}")
    return not errs, errs


def check_append_only(repo_dir, record_name=RECORD_NAME):
    """(ok, message, vacuous). Compares the current on-disk file against
    what is committed at HEAD; the current file must start with HEAD's
    content unchanged, with only additions permitted after it. vacuous is
    True only for the one case where no comparison was actually possible
    (the file is not present at HEAD yet, so there is nothing to
    violate) -- a real pass, with real prior content to compare against,
    is not vacuous even though it also has nothing to report."""
    repo_dir = Path(repo_dir)
    current_path = repo_dir / record_name
    if not current_path.is_file():
        return False, f"{record_name} does not exist in the working tree", False

    probe = subprocess.run(
        ["git", "-C", str(repo_dir), "cat-file", "-e", f"HEAD:{record_name}"],
        capture_output=True)
    if probe.returncode != 0:
        return (True, f"{record_name} not present at HEAD yet; nothing to "
               f"violate", True)

    head = subprocess.run(
        ["git", "-C", str(repo_dir), "show", f"HEAD:{record_name}"],
        capture_output=True, text=True, check=True)
    head_lines = head.stdout.splitlines()
    current_lines = current_path.read_text().splitlines()

    if len(current_lines) < len(head_lines):
        idx = len(current_lines)
        return (False, f"line {idx + 1} removed: {head_lines[idx]!r} is no "
               f"longer present in the working tree", False)

    for idx, head_line in enumerate(head_lines):
        if current_lines[idx] != head_line:
            return (False, f"line {idx + 1} changed: HEAD had "
                   f"{head_line!r}, working tree now has "
                   f"{current_lines[idx]!r}", False)

    return True, "current file starts with HEAD's content unchanged", False


class MergeCommitEncountered(Exception):
    """Raised by check_history_append_only when a commit touching
    RECORD.md has more than one parent. The walk stops rather than
    guessing which parent to diff against -- a merge commit changes
    that, and reconciling it is an operator decision, not an inference
    this module makes on its own."""


def _commit_log_for(repo_dir, record_name=RECORD_NAME):
    """Ordered list of {hash, subject, parents} for every commit that has
    touched record_name, oldest first. parents is a list of parent
    hashes; more than one means a merge commit."""
    repo_dir = Path(repo_dir)
    result = subprocess.run(
        ["git", "-C", str(repo_dir), "log", "--reverse",
         "--format=%H%x00%s%x00%P", "--", record_name],
        capture_output=True, text=True, check=True)
    commits = []
    for line in result.stdout.splitlines():
        if not line:
            continue
        h, subject, parents = line.split("\x00")
        commits.append({
            "hash": h, "subject": subject,
            "parents": parents.split() if parents else [],
        })
    return commits


def _show_bytes(repo_dir, commit_hash, record_name=RECORD_NAME):
    result = subprocess.run(
        ["git", "-C", str(repo_dir), "show", f"{commit_hash}:{record_name}"],
        capture_output=True, check=True)
    return result.stdout


def _first_divergent_line(older_bytes, newer_bytes):
    older_lines = older_bytes.decode("utf-8", errors="replace").splitlines()
    newer_lines = newer_bytes.decode("utf-8", errors="replace").splitlines()
    for idx, ol in enumerate(older_lines):
        if idx >= len(newer_lines) or newer_lines[idx] != ol:
            return idx + 1
    return len(older_lines) + 1


def check_history_append_only(repo_dir, record_name=RECORD_NAME):
    """(ok, violations, commit_count, vacuous). Walks every commit that
    touched record_name, oldest first, and asserts each version's full
    text (compared as raw bytes, not a line-diff heuristic) is a
    byte-prefix of the next. Reports every violating pair found, not
    just the first. vacuous is True when fewer than two commits exist to
    compare (zero or one), so zero prefix comparisons were actually
    performed. Raises MergeCommitEncountered, rather than returning a
    result, if any commit in the walk has more than one parent."""
    commits = _commit_log_for(repo_dir, record_name)

    merge = next((c for c in commits if len(c["parents"]) > 1), None)
    if merge is not None:
        raise MergeCommitEncountered(
            f"commit {merge['hash']} ({merge['subject']!r}) touching "
            f"{record_name} has {len(merge['parents'])} parents; merge "
            f"commits are out of scope for this walk")

    violations = []
    for i in range(1, len(commits)):
        older, newer = commits[i - 1], commits[i]
        older_bytes = _show_bytes(repo_dir, older["hash"], record_name)
        newer_bytes = _show_bytes(repo_dir, newer["hash"], record_name)
        if not newer_bytes.startswith(older_bytes):
            violations.append({
                "older": older["hash"], "older_subject": older["subject"],
                "newer": newer["hash"], "newer_subject": newer["subject"],
                "divergence_line": _first_divergent_line(older_bytes, newer_bytes),
            })

    return not violations, violations, len(commits), len(commits) <= 1


# --------------------------------------------------------------------------
# CLI wrapper. New construction -- see module docstring. Modeled on
# OSCam's ledger_cli.py (also new construction there), not a recovery of
# check_record.py's original main()/render_check().
# --------------------------------------------------------------------------

HERE = Path(__file__).resolve().parent


def main():
    record_path = HERE / RECORD_NAME
    if not record_path.is_file():
        print(f"{RECORD_NAME} does not exist yet in {HERE} -- nothing to "
              f"validate. This is not a failure: the record store has no "
              f"content until a first entry is committed.")
        return 0

    text = record_path.read_text()
    entries, errors, unterminated, sentinel_reports = validate_entries(text)

    print(f"entries: {len(entries)}")
    if unterminated:
        print(f"unterminated intent entries (reported, not a failure): "
              f"{', '.join(unterminated)}")
    if sentinel_reports:
        print("intent entries with prediction sentinels (reported, not a "
              "failure):")
        for r in sentinel_reports:
            note = "resolved" if r["status"] == "resolved" else "pending"
            print(f"  {r['id']}: {r['field']} = {r['sentinel']!r} ({note})")

    print()
    wt_ok, wt_msg, wt_vacuous = check_append_only(HERE)
    vac = " (vacuous: nothing to compare against)" if wt_vacuous else ""
    print(f"[working-tree check -- covers changes since the last commit "
          f"only] {'PASS' if wt_ok else 'FAIL'}: {wt_msg}{vac}")
    if not wt_ok:
        errors.append(f"working-tree append-only: {wt_msg}")

    try:
        hist_ok, violations, commit_count, hist_vacuous = check_history_append_only(HERE)
    except MergeCommitEncountered as e:
        hist_ok = False
        print(f"[history walk -- covers every committed transition in "
              f"{RECORD_NAME}'s history] FAIL: {e}")
        errors.append(f"history walk: {e}")
    else:
        if hist_ok:
            vac = (f" (vacuous: only {commit_count} committed version(s), "
                   f"zero prefix comparisons performed)") if hist_vacuous else ""
            print(f"[history walk -- covers every committed transition in "
                  f"{RECORD_NAME}'s history] PASS: {commit_count} commit(s) "
                  f"checked, each a byte-prefix extension of the one "
                  f"before it{vac}")
        else:
            print(f"[history walk -- covers every committed transition in "
                  f"{RECORD_NAME}'s history] FAIL: {len(violations)} "
                  f"violation(s)")
            for v in violations:
                msg = (f"{v['older'][:10]} ({v['older_subject']!r}) -> "
                      f"{v['newer'][:10]} ({v['newer_subject']!r}): "
                      f"diverges at line {v['divergence_line']}")
                print("    " + msg)
                errors.append(f"history walk: {msg}")

    sup_ok, sup_errors, sup_vacuous = check_superseded_by(entries)
    vac = " (vacuous: no entry declares Outcome: superseded)" if sup_vacuous else ""
    print(f"[Superseded-by cross-reference -- covers terminal entries "
          f"declaring Outcome: superseded] {'PASS' if sup_ok else 'FAIL'}"
          f"{vac}")
    for e in sup_errors:
        print("    " + e)
        errors.append(f"Superseded-by: {e}")

    dup_ok, dup_errors = check_duplicate_terminals(entries)
    print(f"[duplicate-terminal check -- every intent closed by more than "
          f"one terminal entry] {'PASS' if dup_ok else 'FAIL'}")
    for e in dup_errors:
        print("    " + e)
        errors.append(f"duplicate terminal: {e}")

    if errors:
        print("\nFAIL")
        for e in errors:
            print("  " + e)
        return 1
    print(f"\nPASS: entries structurally valid; working-tree check, "
          f"history walk, and Superseded-by cross-reference all clean")
    return 0


# --------------------------------------------------------------------------
# Self-tests. Every fixture lives under tempfile.mkdtemp(). New suite --
# OSCam's six self-tests were written against LEDGER.md's renamed fields;
# this suite is written directly against this file's own RECORD.md field
# set and covers more categories, per this task's own scope.
# --------------------------------------------------------------------------

def _render_entry(fields):
    return "\n".join(f"**{label}:** {value}" for label, value in fields.items()
                     if value is not None)


def _minimal_intent(id_="2026-01-01-01", **overrides):
    fields = {
        "Kind": "intent", "ID": id_, "Timestamp": "2026-01-01T00:00:00Z",
        "Title": "t", "Change": "c", "Scope boundary": "s", "Baseline": "b",
        "Prediction (outcome — planner)": "po",
        "Prediction (mechanism — coder)": "pm",
        "Finish line": "f", "Abort conditions": "a",
    }
    fields.update(overrides)
    return _render_entry(fields)


def _minimal_terminal(id_="2026-01-01-02", closes="2026-01-01-01",
                      outcome="completed", **overrides):
    fields = {
        "Kind": "terminal", "ID": id_, "Timestamp": "2026-01-01T00:01:00Z",
        "Closes": closes, "Outcome": outcome, "Observed": "o", "Deviations": "d",
    }
    fields.update(overrides)
    return _render_entry(fields)


def _minimal_record(*entries):
    parts = ["# RECORD.md", "", "## Entries", "", "---", ""]
    for e in entries:
        parts.append(e)
        parts.append("")
        parts.append("---")
        parts.append("")
    return "\n".join(parts)


def _init_repo(tmp_dir):
    subprocess.run(["git", "-C", str(tmp_dir), "init", "-q"], check=True)


def _commit_record(tmp_dir, content, msg="record"):
    (Path(tmp_dir) / RECORD_NAME).write_text(content)
    subprocess.run(["git", "-C", str(tmp_dir), "add", RECORD_NAME], check=True)
    subprocess.run(["git", "-C", str(tmp_dir), "-c", "user.name=check_record_test",
                    "-c", "user.email=check_record_test@example.invalid",
                    "commit", "-q", "-m", msg], check=True)


def render_check():
    print("check_record.py --render-check")
    failures = []

    def check(name, expect_fail_msg, fn):
        print(f"\n[{name}] expected failure mode if broken: {expect_fail_msg}")
        try:
            fn()
            print(f"[{name}] PASS")
        except Exception as e:
            print(f"[{name}] FAIL: {e!r}")
            failures.append(name)

    # ---- Check 1: well-formed record passes (positive case) ---------------
    def check1():
        text = _minimal_record(_minimal_intent(), _minimal_terminal())
        entries, errors, unterminated, _ = validate_entries(text)
        assert not errors, f"well-formed record produced errors: {errors}"
        assert not unterminated
        assert len(entries) == 2

    check("check1_well_formed_record_passes",
          "N/A -- positive case, validated by the negative checks below "
          "catching sabotages of the same fixture",
          check1)

    # ---- Check 2: field presence -- missing required field is caught ------
    def check2():
        text = _minimal_record(_minimal_intent(Change=None))
        _, errors, _, _ = validate_entries(text)
        assert any("2026-01-01-01" in e and "'Change'" in e for e in errors), (
            f"missing 'Change' field not reported by ID and field name: {errors}")

    check("check2_missing_field_names_fault",
          "an entry missing a required field is accepted, or the error "
          "does not name both the entry and the missing field",
          check2)

    # ---- Check 3: field presence -- missing restored prediction label -----
    def check3():
        text = _minimal_record(_minimal_intent(**{"Prediction (outcome — planner)": None}))
        _, errors, _, _ = validate_entries(text)
        assert any("Prediction (outcome — planner)" in e for e in errors), (
            f"missing restored outcome-prediction field not reported: {errors}")

    check("check3_missing_restored_prediction_field_caught",
          "an intent entry missing the restored 'Prediction (outcome — "
          "planner)' field is accepted, or the error does not name it",
          check3)

    # ---- Check 4: Closes resolution ----------------------------------------
    def check4():
        text = _minimal_record(_minimal_intent(),
                                _minimal_terminal(closes="2026-01-01-99"))
        _, errors, _, _ = validate_entries(text)
        assert any("2026-01-01-99" in e and "Closes" in e for e in errors), (
            f"terminal closing a nonexistent intent ID not reported: {errors}")

    check("check4_closes_resolution_catches_dangling_reference",
          "a terminal entry's Closes field naming a nonexistent intent ID "
          "is accepted silently",
          check4)

    # ---- Check 5: duplicate IDs --------------------------------------------
    def check5():
        text = _minimal_record(_minimal_intent(id_="2026-01-01-01"),
                                _minimal_intent(id_="2026-01-01-01", Title="second"))
        _, errors, _, _ = validate_entries(text)
        assert any("duplicate ID 2026-01-01-01" in e for e in errors), (
            f"duplicate entry ID not reported: {errors}")

    check("check5_duplicate_id_caught",
          "two entries sharing the same ID are accepted, or the error "
          "does not name the colliding ID",
          check5)

    # ---- Check 6: duplicate terminals --------------------------------------
    def check6():
        text = _minimal_record(
            _minimal_intent(),
            _minimal_terminal(id_="2026-01-01-02"),
            _minimal_terminal(id_="2026-01-01-03"),
        )
        entries, _, _, _ = validate_entries(text)
        ok, errs = check_duplicate_terminals(entries)
        assert not ok, "duplicate terminal closing the same intent not reported"
        assert any("closed by 2 terminal entries" in e and
                   "2026-01-01-02" in e and "2026-01-01-03" in e
                   for e in errs), (
            f"duplicate-terminal error did not name both closing entries: {errs}")

    check("check6_duplicate_terminal_names_both_closers",
          "one intent closed by two terminal entries is accepted, or the "
          "error does not name both closing entries",
          check6)

    # ---- Check 7: sentinel 'withheld' reported pending, not an error ------
    def check7():
        text = _minimal_record(
            _minimal_intent(**{"Prediction (outcome — planner)": "withheld"}))
        _, errors, _, sentinel_reports = validate_entries(text)
        assert not errors, f"a pending 'withheld' sentinel was treated as an error: {errors}"
        assert any(r["sentinel"] == "withheld" and r["status"] == "pending"
                   for r in sentinel_reports), (
            f"'withheld' sentinel not reported pending: {sentinel_reports}")

    check("check7_withheld_sentinel_reported_pending",
          "a 'withheld' outcome-prediction sentinel is either treated as "
          "an error or not surfaced in sentinel_reports as pending",
          check7)

    # ---- Check 8: 'withheld' sentinel resolved by a later terminal --------
    def check8():
        text = _minimal_record(
            _minimal_intent(**{"Prediction (outcome — planner)": "withheld"}),
            _minimal_terminal(**{"Prediction-outcome-supplied": "it worked"}))
        _, errors, _, sentinel_reports = validate_entries(text)
        assert not errors, f"legitimate resolution of a withheld sentinel raised errors: {errors}"
        assert any(r["sentinel"] == "withheld" and r["status"] == "resolved"
                   for r in sentinel_reports), (
            f"withheld sentinel not marked resolved once supplied by the "
            f"closing terminal: {sentinel_reports}")

    check("check8_withheld_sentinel_resolved_by_terminal",
          "a terminal entry supplying the outcome for an intent whose "
          "prediction was legitimately 'withheld' is not recognized as a "
          "resolution",
          check8)

    # ---- Check 9: 'not authored' + later-supplied is fabrication ----------
    def check9():
        text = _minimal_record(
            _minimal_intent(**{"Prediction (outcome — planner)": "not authored"}),
            _minimal_terminal(**{"Prediction-outcome-supplied": "it worked"}))
        _, errors, _, _ = validate_entries(text)
        assert any("fabricated after the outcome" in e for e in errors), (
            f"a permanently 'not authored' sentinel paired with a later-"
            f"supplied value was not flagged as fabrication: {errors}")

    check("check9_not_authored_plus_supplied_is_fabrication",
          "an intent whose outcome prediction is permanently 'not "
          "authored' is not flagged when a terminal entry later supplies "
          "a value anyway",
          check9)

    # ---- Check 10: sentinels forbidden in terminal entries -----------------
    def check10():
        text = _minimal_record(
            _minimal_intent(),
            _minimal_terminal(Observed="withheld"))
        _, errors, _, _ = validate_entries(text)
        assert any("Observed" in e and "withheld" in e for e in errors), (
            f"a sentinel value in a terminal entry field was accepted: {errors}")

    check("check10_sentinel_forbidden_in_terminal_entry",
          "a terminal entry carrying a sentinel value in any field is "
          "accepted, or the error does not name the field and value",
          check10)

    # ---- Check 11: working-tree append-only catches a modified line -------
    def check11():
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        original = _minimal_record(_minimal_intent(), _minimal_terminal())
        _commit_record(tmp, original, "initial record")

        lines = original.splitlines()
        target_idx = next(i for i, l in enumerate(lines) if l.startswith("**Title:**"))
        lines[target_idx] = "**Title:** tampered"
        (tmp / RECORD_NAME).write_text("\n".join(lines))

        ok, msg, vacuous = check_append_only(tmp)
        assert not ok, "a modified existing line was not caught"
        assert not vacuous, "a real comparison was incorrectly reported vacuous"
        assert f"line {target_idx + 1}" in msg

    check("check11_working_tree_append_only_catches_modified_line",
          "modifying an existing, already-committed line in RECORD.md is "
          "not caught, or the failure does not name the changed line",
          check11)

    # ---- Check 12: working-tree append-only, vacuous case -----------------
    def check12():
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        (tmp / RECORD_NAME).write_text(_minimal_record(_minimal_intent()))
        ok, msg, vacuous = check_append_only(tmp)
        assert ok, f"RECORD.md absent from HEAD (nothing committed yet) was reported as a violation: {msg}"
        assert vacuous, "a check with nothing at HEAD to compare against was not reported vacuous"

    check("check12_working_tree_append_only_vacuous_before_first_commit",
          "a RECORD.md that exists in the working tree but has never been "
          "committed is reported as a violation, or the vacuous case is "
          "not flagged as vacuous",
          check12)

    # ---- Check 13: history walk on a real append-only repo ----------------
    def check13():
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        v1 = _minimal_record(_minimal_intent())
        v2 = _minimal_record(_minimal_intent(), _minimal_terminal())
        _commit_record(tmp, v1, "intent")
        _commit_record(tmp, v2, "terminal")
        ok, violations, count, vacuous = check_history_append_only(tmp)
        assert ok, f"a genuinely append-only history was reported violating: {violations}"
        assert count == 2
        assert not vacuous, "two real committed versions was incorrectly reported vacuous"

    check("check13_history_walk_real_repo",
          "a genuinely append-only two-commit history is reported as "
          "violating, or the wrong commit count is walked",
          check13)

    # ---- Check 14: history walk, vacuous case (one commit) ----------------
    def check14():
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        _commit_record(tmp, _minimal_record(_minimal_intent()), "only commit")
        ok, violations, count, vacuous = check_history_append_only(tmp)
        assert ok, f"a single-commit history was reported violating: {violations}"
        assert count == 1
        assert vacuous, "a history with fewer than two versions was not reported vacuous"

    check("check14_history_walk_vacuous_single_commit",
          "a RECORD.md history with only one committed version performs a "
          "comparison anyway, or is not reported vacuous",
          check14)

    # ---- Check 15: history walk catches a removed line ---------------------
    def check15():
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        v1 = "line1\nline2\nline3\n"
        v2 = "line1\nline3\n"  # line2 removed
        (tmp / RECORD_NAME).write_text(v1)
        subprocess.run(["git", "-C", str(tmp), "add", RECORD_NAME], check=True)
        subprocess.run(["git", "-C", str(tmp), "-c", "user.name=x", "-c",
                        "user.email=x@example.invalid", "commit", "-q", "-m", "v1"],
                       check=True)
        (tmp / RECORD_NAME).write_text(v2)
        subprocess.run(["git", "-C", str(tmp), "add", RECORD_NAME], check=True)
        subprocess.run(["git", "-C", str(tmp), "-c", "user.name=x", "-c",
                        "user.email=x@example.invalid", "commit", "-q", "-m", "v2 bad"],
                       check=True)
        ok, violations, count, _ = check_history_append_only(tmp)
        assert not ok, "a commit that removed a line from history was not caught"
        assert violations[0]["divergence_line"] == 2

    check("check15_history_walk_catches_removed_line",
          "a commit that removed an existing line from RECORD.md's "
          "history is accepted by the walk",
          check15)

    # ---- Check 16: merge commit stops the walk -----------------------------
    def check16():
        # A merge that changes an unrelated file (or fast-forwards cleanly)
        # gets pruned out of `git log -- RECORD.md`'s default history
        # simplification entirely -- confirmed by direct reproduction
        # while building this suite, not assumed. Only a merge that
        # genuinely reconciles divergent RECORD.md content on both sides
        # survives that simplification and reaches the walk at all, so
        # that is what this fixture has to construct.
        tmp = Path(tempfile.mkdtemp(prefix="check_record_render_check_"))
        _init_repo(tmp)
        _commit_record(tmp, _minimal_record(_minimal_intent(id_="2026-01-01-01")),
                       "on main")
        subprocess.run(["git", "-C", str(tmp), "checkout", "-q", "-b", "side"], check=True)
        _commit_record(tmp, _minimal_record(
            _minimal_intent(id_="2026-01-01-01"),
            _minimal_intent(id_="2026-01-01-02")), "on side")
        subprocess.run(["git", "-C", str(tmp), "checkout", "-q", "-"], check=True)
        _commit_record(tmp, _minimal_record(
            _minimal_intent(id_="2026-01-01-01"),
            _minimal_intent(id_="2026-01-01-03")), "on main further")
        subprocess.run(["git", "-C", str(tmp), "-c", "user.name=x", "-c",
                        "user.email=x@example.invalid", "merge", "-q",
                        "--no-ff", "side", "-m", "merge side"],
                       capture_output=True)  # expected to conflict; resolved below
        _commit_record(tmp, _minimal_record(
            _minimal_intent(id_="2026-01-01-01"),
            _minimal_intent(id_="2026-01-01-02"),
            _minimal_intent(id_="2026-01-01-03")), "merge side")
        try:
            check_history_append_only(tmp)
        except MergeCommitEncountered:
            pass
        else:
            raise AssertionError("a merge commit touching RECORD.md did not "
                                 "raise MergeCommitEncountered")

    check("check16_merge_commit_stops_the_walk",
          "a merge commit touching RECORD.md is silently walked (wrong "
          "parent guessed) instead of raising MergeCommitEncountered",
          check16)

    # ---- Check 17: Superseded-by cross-reference, vacuous case ------------
    def check17():
        entries, _, _, _ = validate_entries(
            _minimal_record(_minimal_intent(), _minimal_terminal()))
        ok, errs, vacuous = check_superseded_by(entries)
        assert ok and not errs, f"no superseded entries but check reported errors: {errs}"
        assert vacuous, "a store with no Outcome: superseded entries was not reported vacuous"

    check("check17_superseded_by_vacuous_when_none_declared",
          "a record with no terminal entry declaring Outcome: superseded "
          "is not reported vacuous, or is reported as a failure",
          check17)

    # ---- Check 18: Superseded-by cross-reference catches a bad reference --
    def check18():
        entries, _, _, _ = validate_entries(_minimal_record(
            _minimal_intent(),
            _minimal_terminal(outcome="superseded",
                              **{"Superseded-by": "2026-01-01-99"})))
        ok, errs, vacuous = check_superseded_by(entries)
        assert not ok, "a Superseded-by naming a nonexistent intent ID was accepted"
        assert not vacuous
        assert any("2026-01-01-99" in e for e in errs)

    check("check18_superseded_by_catches_dangling_reference",
          "a terminal entry's Superseded-by field naming a nonexistent "
          "intent ID is accepted",
          check18)

    print(f"\n{'FAIL' if failures else 'PASS'}: {len(failures)} of "
          f"{18} checks failed{': ' + ', '.join(failures) if failures else ''}")
    return 1 if failures else 0


if __name__ == "__main__":
    if "--render-check" in sys.argv:
        sys.exit(render_check())
    else:
        sys.exit(main())
