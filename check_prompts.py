"""check_prompts.py -- existence and binding check for prompts/.

Answers existence and binding questions only, nothing about content:

  - every RECORD.md entry that records a dispatch (carries a
    **Dispatch-file:** field) names a file that exists under prompts/
  - every file that physically exists under prompts/ is named by
    exactly one such entry -- unclaimed files and duplicate claims on
    the same file are both reported as errors, as distinct shapes
  - counts of prompts/preserved/ and prompts/recovered/ are reported
    separately, by listing each subdirectory, never by trusting a
    claim written inside an entry

This script does NOT and cannot verify that a stored file holds the
text actually dispatched to an agent. That fact lives outside this
repository, in whatever produced the dispatch, and nothing checkable
from inside a git working tree can confirm it. Do not read a passing
run of this script as evidence the stored text is accurate -- it is
evidence only that a file exists where an entry says one should, and
that no entry and no file are left dangling. See RECORD.md entry
2026-08-13-09 for the "recovered versus preserved" distinction this
script depends on: preserved/ holds a prompt captured verbatim at
dispatch time; recovered/ holds one reconstructed afterward from a
transcript. The directory is the single source of truth for that
distinction, not a second field that could drift out of sync with it.

One convention is accepted for a file claimed by more than one entry:
an intent entry and the terminal entry that closes it (its own
**Closes:** field naming the intent's **ID**) may both carry a
**Dispatch-file:** field pointing at the same file -- that is one
dispatch referenced twice, not two claims on one prompt. The pairing
is read from each entry's own Kind and Closes fields, never inferred
from ID adjacency. Any other file claimed by more than one entry,
including two entries that are both intents, both terminals, or a
terminal whose Closes does not name the other claimant, is still
reported as a violation. Decided at RECORD.md entry 2026-08-14-01,
which found 2026-08-13-13 (intent) and 2026-08-13-14 (its terminal)
both legitimately claiming prompts/preserved/2026-08-13-13.md and
this script, unaware of the convention, reporting it as a defect.

Reuses check_record.split_entries and check_record.parse_fields
unmodified, via `import check_record as cr`. This carries forward,
knowingly and not fixed here, the defect documented in check_record.py's
own header: split_entries() finds its '## Entries' heading by raw
substring search, with no markdown awareness, so prose naming that
heading before the real one produces a phantom entry. The same logic
is duplicated in OSCam's ledger_validator_core.py; fixing either is out
of scope for this task. Does not reuse validate_entries -- this script
asks a different question (dispatch-file binding) than that function
answers (intent/terminal shape and sentinel handling), and duplicating
its required-field logic here would only reintroduce a second copy of
rules that already live in check_record.py.

Store names (Claude-kit v0.2 addition): every file under
prompts/preserved/ (not recovered/) must be named YYYY-MM-DD-NN.md, and
the date in its name must equal the UTC date of the Preserved: line in
its header (the lines before "--- verbatim prompt follows ---"). A file
with no such line is an error. Files named before 2026-09-24-05,
compared as (date, number) and not as text, are exempt: they were in
the store before the rule began. The rule reads only names and headers,
never git, so it runs the same in CI and in an adopter.

Re-sends (Claude-kit v0.2 addition, T10): the dispatch hook saves a
prompt identical to a stored dispatch's text with one more header line,
"Repeat-of: preserved/<name>". For every file under prompts/preserved/
whose header (the lines before "--- verbatim prompt follows ---") has a
Repeat-of: line, the file it names must exist under prompts/ and hold
the same bytes after its first delimiter line as this file does after
its own; otherwise it is an error naming both. This applies to every
file, whatever its name's date: the cutoff above does not exempt it.
"""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import check_record as cr

RECORD_NAME = "RECORD.md"
PROMPTS_DIR = "prompts"
DISPATCH_FIELD = "Dispatch-file"
PROVENANCE_DIRS = ("preserved", "recovered")

# Claude-kit v0.2 addition: the store-name rule (see module docstring).
STORE_NAME_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})-(\d{2,})\.md$")
HEADER_DELIMITER = "--- verbatim prompt follows ---"
PRESERVED_PREFIX = "Preserved: "
# Exempts the files in the store before the rule began: a name whose
# (date, number) is below this pair is not checked against its header.
STORE_NAME_CUTOFF = ("2026-09-24", 5)
# Claude-kit v0.2 addition (T10): the re-send header line.
REPEAT_PREFIX = "Repeat-of: "


def _text_after_delimiter(data):
    """The bytes after the first line equal to HEADER_DELIMITER, or None."""
    marker = HEADER_DELIMITER.encode("utf-8")
    offset = 0
    for line in data.splitlines(keepends=True):
        offset += len(line)
        if line.rstrip(b"\r\n") == marker:
            return data[offset:]
    return None


def repeat_errors(repo_dir, on_disk):
    """Errors for files under prompts/preserved/ whose header names, in a
    Repeat-of: line, a file that does not exist under prompts/ or whose
    text after its delimiter differs from this file's."""
    errors = []
    prompts_root = (Path(repo_dir) / PROMPTS_DIR).resolve()
    prefix = f"{PROVENANCE_DIRS[0]}/"
    for rel in sorted(on_disk):
        if not rel.startswith(prefix):
            continue
        data = (Path(repo_dir) / PROMPTS_DIR / rel).read_bytes()
        named = None
        for line in data.decode("utf-8", errors="replace").splitlines():
            if line == HEADER_DELIMITER:
                break
            if line.startswith(REPEAT_PREFIX):
                named = line[len(REPEAT_PREFIX):].strip()
                break
        if named is None:
            continue
        target = (prompts_root / named).resolve()
        label = (f"{PROMPTS_DIR}/{rel}: its header's "
                 f"{REPEAT_PREFIX.strip()!r} line names {PROMPTS_DIR}/{named}")
        if prompts_root not in target.parents or not target.is_file():
            errors.append(f"{label}, which does not exist under {PROMPTS_DIR}/")
            continue
        body = _text_after_delimiter(data)
        if body is None or _text_after_delimiter(target.read_bytes()) != body:
            errors.append(f"{label}, whose text after {HEADER_DELIMITER!r} "
                          f"differs from this file's")
    return errors


def store_name_errors(repo_dir, on_disk):
    """Errors for files under prompts/preserved/ that break the store-name
    rule: a name not of the form YYYY-MM-DD-NN.md, or, for a name at or
    after STORE_NAME_CUTOFF, a header with no Preserved: line or one whose
    date differs from the name's date."""
    errors = []
    prefix = f"{PROVENANCE_DIRS[0]}/"
    for rel in sorted(on_disk):
        if not rel.startswith(prefix):
            continue
        name = rel[len(prefix):]
        match = STORE_NAME_RE.match(name)
        if not match:
            errors.append(
                f"{PROMPTS_DIR}/{rel}: name does not match YYYY-MM-DD-NN.md")
            continue
        name_date, number = match.group(1), int(match.group(2))
        if (name_date, number) < STORE_NAME_CUTOFF:
            continue
        text = (Path(repo_dir) / PROMPTS_DIR / rel).read_text(
            encoding="utf-8", errors="replace")
        preserved = None
        for line in text.splitlines():
            if line == HEADER_DELIMITER:
                break
            if line.startswith(PRESERVED_PREFIX):
                preserved = line[len(PRESERVED_PREFIX):]
                break
        if preserved is None:
            errors.append(
                f"{PROMPTS_DIR}/{rel}: its header has no "
                f"{PRESERVED_PREFIX.strip()!r} line before "
                f"{HEADER_DELIMITER!r}, so its name's date cannot be checked")
            continue
        header_date = preserved[:10]
        if header_date != name_date:
            errors.append(
                f"{PROMPTS_DIR}/{rel}: name's date {name_date} differs from "
                f"its header's Preserved date {header_date}; a store copy's "
                f"name must carry the UTC date of its Preserved: line")
    return errors


def dispatch_claims(text):
    """List of (entry_id, dispatch_file_value) for every entry in
    RECORD.md that carries a Dispatch-file field, in entry order.
    entry_id is '' if the entry itself has no ID (already a distinct
    defect check_record.py's own validator reports separately)."""
    claims = []
    for block in cr.split_entries(text):
        fields = cr.parse_fields(block)
        value = fields.get(DISPATCH_FIELD, "").strip()
        if not value:
            continue
        claims.append((fields.get("ID", "").strip(), value))
    return claims


def entry_kind_closes(text):
    """Map entry_id -> (kind, closes) for every RECORD.md entry that
    carries an ID field. kind and closes are taken directly from that
    entry's own fields -- closes is '' for intent entries and for any
    terminal with no Closes field. This is the only place ID adjacency
    could have been used to guess a pairing and deliberately is not:
    the mapping is looked up by the literal Closes text, never by
    comparing ID strings to each other."""
    mapping = {}
    for block in cr.split_entries(text):
        fields = cr.parse_fields(block)
        entry_id = fields.get("ID", "").strip()
        if not entry_id:
            continue
        mapping[entry_id] = (
            fields.get("Kind", "").strip(),
            fields.get("Closes", "").strip(),
        )
    return mapping


def is_intent_terminal_pair(ids, kind_closes):
    """True if ids (the claimants of one Dispatch-file value) is
    exactly one intent entry and the terminal entry that closes it.
    ids containing a "(no ID)" placeholder, or any entry_id absent
    from kind_closes, can never match -- both fall through to False,
    so this only ever legalizes a claim between two well-formed,
    genuinely paired entries, not a loosening of the general rule."""
    if len(ids) != 2:
        return False
    a, b = ids
    ka, kb = kind_closes.get(a), kind_closes.get(b)
    if ka is None or kb is None:
        return False
    if ka[0] == "intent" and kb[0] == "terminal" and kb[1] == a:
        return True
    if kb[0] == "intent" and ka[0] == "terminal" and ka[1] == b:
        return True
    return False


def files_on_disk(repo_dir):
    """Set of paths relative to prompts/, for every regular file found
    under prompts/preserved/ and prompts/recovered/. A file placed
    directly under prompts/ (outside either subdirectory) is not
    counted as preserved or recovered by this walk and is reported
    separately -- provenance is mandatory, not inferred."""
    prompts_root = Path(repo_dir) / PROMPTS_DIR
    found = set()
    stray = set()
    if not prompts_root.is_dir():
        return found, stray
    for entry in sorted(prompts_root.rglob("*")):
        if not entry.is_file():
            continue
        rel = entry.relative_to(prompts_root)
        if rel.parts and rel.parts[0] in PROVENANCE_DIRS:
            found.add(rel.as_posix())
        else:
            stray.add(rel.as_posix())
    return found, stray


def check_binding(repo_dir=None):
    """(ok, errors, report_lines, counts). Pure existence/binding check,
    no content comparison. counts is {'preserved': n, 'recovered': n}."""
    repo_dir = Path(repo_dir) if repo_dir else HERE
    record_path = repo_dir / RECORD_NAME
    errors = []
    report_lines = []

    if not record_path.is_file():
        return False, [f"{RECORD_NAME} does not exist in {repo_dir}"], [], {}

    text = record_path.read_text()
    claims = dispatch_claims(text)
    kind_closes = entry_kind_closes(text)
    on_disk, stray = files_on_disk(repo_dir)

    claimed_paths = {}
    duplicate_errors = []
    for entry_id, value in claims:
        claimed_paths.setdefault(value, []).append(entry_id or "(no ID)")
    for value, ids in claimed_paths.items():
        if len(ids) > 1 and not is_intent_terminal_pair(ids, kind_closes):
            duplicate_errors.append(
                f"{value!r} is claimed by more than one entry's "
                f"{DISPATCH_FIELD!r} field: {', '.join(ids)}")
    errors.extend(duplicate_errors)

    # Claude-kit v0.1 addition: a dispatch-note claims
    # exactly one *preserved* prompt -- the hook's verbatim capture -- never
    # a recovered one. Claude-kit v0.2 addition: so does a continuation.
    for entry_id, value in claims:
        kind = kind_closes.get(entry_id, ("", ""))[0]
        if (kind in (cr.NOTE_KIND, cr.CONT_KIND)
                and not value.startswith(f"{PROVENANCE_DIRS[0]}/")):
            errors.append(
                f"entry {entry_id}: a {kind} may only claim a prompt "
                f"under {PROMPTS_DIR}/{PROVENANCE_DIRS[0]}/, but its "
                f"{DISPATCH_FIELD!r} names {value!r}")

    missing = []
    for entry_id, value in claims:
        if value not in on_disk:
            missing.append(
                f"entry {entry_id or '(no ID)'}: {DISPATCH_FIELD!r} names "
                f"{value!r}, which does not exist under {PROMPTS_DIR}/")
    errors.extend(missing)

    claimed_set = set(claimed_paths)
    orphaned_files = sorted(on_disk - claimed_set)
    for rel in orphaned_files:
        errors.append(
            f"{PROMPTS_DIR}/{rel} exists but no RECORD.md entry's "
            f"{DISPATCH_FIELD!r} field names it")

    errors.extend(store_name_errors(repo_dir, on_disk))
    errors.extend(repeat_errors(repo_dir, on_disk))

    if stray:
        errors.append(
            f"file(s) present directly under {PROMPTS_DIR}/, outside "
            f"{PROVENANCE_DIRS!r}, with no provenance directory: "
            f"{sorted(stray)}")

    counts = {name: 0 for name in PROVENANCE_DIRS}
    for rel in on_disk:
        top = rel.split("/", 1)[0]
        if top in counts:
            counts[top] += 1

    report_lines.append(f"dispatch-recording entries found: {len(claims)}")
    report_lines.append(f"files under {PROMPTS_DIR}/: "
                         + ", ".join(f"{k}={v}" for k, v in counts.items()))
    if stray:
        report_lines.append(f"stray files with no provenance directory "
                             f"(reported, always an error): {sorted(stray)}")

    return not errors, errors, report_lines, counts


def main():
    ok, errors, report_lines, counts = check_binding()

    for line in report_lines:
        print(line)
    print()

    if ok:
        print(f"PASS: every dispatch-recording entry's {DISPATCH_FIELD!r} "
              f"resolves to a real file, every file under {PROMPTS_DIR}/ "
              f"is claimed by exactly one entry (or by an intent/terminal "
              f"pair closing it), no orphans in either "
              f"direction. This checks existence and binding only -- it "
              f"does not and cannot verify stored text matches what was "
              f"actually dispatched.")
        return 0

    print(f"FAIL: {len(errors)} binding violation(s)")
    for e in errors:
        print(" -", e)
    return 1


# --------------------------------------------------------------------------
# Self-tests (Claude-kit v0.1 addition). Store-level fixtures under a temp dir,
# checked by both this script's binding check and check_record.py's entry
# validation, because a dispatch-note is only a valid claim if it is also a
# valid entry.
# --------------------------------------------------------------------------

def _store(tmp, files, entries):
    root = Path(tmp)
    for rel in files:
        path = root / PROMPTS_DIR / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"prompt {rel}\n")
    text = cr._minimal_record(*entries)
    (root / RECORD_NAME).write_text(text)
    return text


def _store_errors(tmp, files, entries):
    """Binding errors plus entry-validation errors, for one fixture store."""
    text = _store(tmp, files, entries)
    _, binding_errors, _, _ = check_binding(tmp)
    _, entry_errors, _, _ = cr.validate_entries(text)
    return binding_errors, entry_errors


def render_check():
    import tempfile
    print("check_prompts.py --render-check")
    failures = []

    def check(name, expect_fail_msg, fn):
        print(f"\n[{name}] expected failure mode if broken: {expect_fail_msg}")
        try:
            fn()
            print(f"[{name}] PASS")
        except Exception as e:
            print(f"[{name}] FAIL: {e!r}")
            failures.append(name)

    pulse = "preserved/2026-01-01-05.md"

    def p1():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, _ = _store_errors(tmp, [pulse], [cr._minimal_intent()])
        assert any(pulse in e and "no RECORD.md entry" in e for e in binding), (
            f"an unclaimed pulse prompt was not reported: {binding}")

    check("p1_unclaimed_pulse_prompt_fails",
          "a preserved prompt no entry claims passes the binding check",
          p1)

    def p2():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = _store_errors(
            tmp, [pulse], [cr._minimal_intent(), cr._minimal_note()])
        assert not binding, f"a dispatch-note's claim was not accepted: {binding}"
        assert not entry, f"the claiming dispatch-note is not a valid entry: {entry}"

    check("p2_pulse_prompt_with_its_note_passes",
          "a pulse prompt claimed by a well-formed dispatch-note fails "
          "either the binding check or entry validation",
          p2)

    def p3():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        stray = "recovered/2026-01-01-05.md"
        binding, _ = _store_errors(
            tmp, [stray],
            [cr._minimal_note(**{"Dispatch-file": stray})])
        assert any("2026-01-01-05" in e and "preserved/" in e for e in binding), (
            f"a dispatch-note claiming a prompt outside preserved/ was "
            f"accepted: {binding}")

    check("p3_sabotaged_note_outside_preserved_fails",
          "a dispatch-note claiming a prompt outside prompts/preserved/ "
          "is accepted as a claim",
          p3)

    def p4():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        _, entry = _store_errors(
            tmp, [pulse], [cr._minimal_note(Outcome="done")])
        assert any("Outcome" in e and "'done'" in e for e in entry), (
            f"a dispatch-note with a sabotaged Outcome was accepted: {entry}")

    check("p4_sabotaged_note_outcome_fails",
          "a dispatch-note with an Outcome outside answered, declined and "
          "exercise is accepted",
          p4)

    def p5():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, _ = _store_errors(
            tmp, [pulse],
            [cr._minimal_intent(**{"Dispatch-file": pulse}), cr._minimal_note()])
        assert any("claimed by more than one" in e for e in binding), (
            f"a prompt claimed by both an intent and a dispatch-note was "
            f"accepted: {binding}")

    check("p5_note_and_intent_on_one_prompt_fails",
          "one prompt claimed by both an intent and a dispatch-note is "
          "accepted",
          p5)

    # p6-p8 (Claude-kit v0.2 addition): the store-name rule. Each fixture
    # file carries a hook-style header and is claimed by a valid
    # dispatch-note, so the naming rule is the only thing under test.
    def hooked(tmp, specs):
        """specs: list of (rel, preserved_value or None). Writes each file
        with a hook-style header and claims it with its own note."""
        notes = []
        for n, (rel, preserved) in enumerate(specs, 5):
            header = ["HEAD: fixture-head", "Target subagent: pulse",
                      "Type: pulse"]
            if preserved is not None:
                header.append(f"Preserved: {preserved} by the dispatch hook")
            header.append("--- verbatim prompt follows ---")
            path = Path(tmp) / PROMPTS_DIR / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(header) + f"\nprompt {rel}\n")
            notes.append(cr._minimal_note(
                id_=f"2026-01-01-{n:02d}", **{"Dispatch-file": rel}))
        text = cr._minimal_record(*notes)
        (Path(tmp) / RECORD_NAME).write_text(text)
        _, binding_errors, _, _ = check_binding(tmp)
        _, entry_errors, _, _ = cr.validate_entries(text)
        return binding_errors, entry_errors

    def p6():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        name = "preserved/2026-09-25-01.md"
        binding, entry = hooked(tmp, [(name, "2026-09-24T10:00:00Z")])
        assert not entry, f"the claiming dispatch-note is not a valid entry: {entry}"
        assert any("2026-09-25-01.md" in e and "2026-09-24" in e
                   and "Preserved" in e for e in binding), (
            f"a copy whose name's date differs from its Preserved date "
            f"was accepted: {binding}")

    check("p6_wrongly_named_copy_fails",
          "a store copy named for a date other than its header's "
          "Preserved date is accepted",
          p6)

    def p7():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = hooked(tmp, [
            ("preserved/2026-09-25-01.md", "2026-09-25T10:00:00Z"),
            ("preserved/2026-09-23-07.md", "2026-09-24T05:44:30Z"),
        ])
        assert not entry, f"a claiming dispatch-note is not a valid entry: {entry}"
        assert not binding, (
            f"a correctly named copy, or a pre-cutoff copy, was rejected: "
            f"{binding}")

    check("p7_matching_and_pre_cutoff_names_pass",
          "a copy whose name's date equals its Preserved date, or one "
          "named before the cutoff, is rejected",
          p7)

    def p8():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        name = "preserved/2026-09-25-02.md"
        binding, entry = hooked(tmp, [(name, None)])
        assert not entry, f"the claiming dispatch-note is not a valid entry: {entry}"
        assert any("2026-09-25-02.md" in e and "Preserved" in e
                   for e in binding), (
            f"a copy with no Preserved line in its header was accepted: "
            f"{binding}")

    check("p8_copy_without_preserved_line_fails",
          "a store copy after the cutoff whose header has no Preserved "
          "line is accepted",
          p8)

    # p9-p10 (Claude-kit v0.2 addition): a continuation's Dispatch-file is
    # an ordinary single claim, and must be under preserved/.
    cont_file = "preserved/2026-01-01-03.md"

    def p9():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = _store_errors(
            tmp, [cont_file],
            [cr._minimal_intent(), cr._minimal_continuation()])
        assert not binding, f"a continuation's claim was not accepted: {binding}"
        assert not entry, f"the claiming continuation is not a valid entry: {entry}"

    check("p9_continuation_claiming_its_preserved_file_passes",
          "a continuation claiming its own preserved prompt fails either "
          "the binding check or entry validation",
          p9)

    def p10():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        stray = "recovered/2026-01-01-03.md"
        binding, _ = _store_errors(
            tmp, [stray],
            [cr._minimal_intent(),
             cr._minimal_continuation(**{"Dispatch-file": stray})])
        assert any("2026-01-01-03" in e and "continuation" in e
                   and "preserved/" in e for e in binding), (
            f"a continuation claiming a prompt outside preserved/ was "
            f"accepted: {binding}")

    check("p10_continuation_outside_preserved_fails",
          "a continuation claiming a prompt outside prompts/preserved/ "
          "is accepted as a claim",
          p10)

    # p11-p13 (Claude-kit v0.2 addition, T10): a store copy whose header
    # carries Repeat-of: must name an existing file with identical text.
    def repeated(tmp, specs):
        """specs: list of (rel, repeat_of or None, body). Writes each file
        with a hook-style header and claims it with its own note."""
        notes = []
        for n, (rel, repeat_of, body) in enumerate(specs, 5):
            header = ["HEAD: fixture-head", "Target subagent: pulse",
                      "Type: pulse",
                      "Preserved: 2026-09-25T10:00:00Z by the dispatch hook"]
            if repeat_of is not None:
                header.append(f"Repeat-of: {repeat_of}")
            header.append("--- verbatim prompt follows ---")
            path = Path(tmp) / PROMPTS_DIR / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(header) + "\n" + body)
            notes.append(cr._minimal_note(
                id_=f"2026-01-01-{n:02d}", **{"Dispatch-file": rel}))
        text = cr._minimal_record(*notes)
        (Path(tmp) / RECORD_NAME).write_text(text)
        _, binding_errors, _, _ = check_binding(tmp)
        _, entry_errors, _, _ = cr.validate_entries(text)
        return binding_errors, entry_errors

    original = "preserved/2026-09-25-01.md"
    repeat = "preserved/2026-09-25-02.md"

    def p11():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = repeated(tmp, [
            (original, None, "# Dispatch\n\nSame text.\n"),
            (repeat, original, "# Dispatch\n\nSame text.\n"),
        ])
        assert not entry, f"a claiming dispatch-note is not a valid entry: {entry}"
        assert not binding, f"a valid re-send was rejected: {binding}"

    check("p11_valid_repeat_passes",
          "a re-send naming an existing file with identical text is rejected",
          p11)

    def p12():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = repeated(tmp, [
            (original, None, "# Dispatch\n\nSame text.\n"),
            (repeat, original, "# Dispatch\n\nSame text!\n"),
        ])
        assert not entry, f"a claiming dispatch-note is not a valid entry: {entry}"
        assert any("2026-09-25-01.md" in e and "2026-09-25-02.md" in e
                   and "Repeat-of" in e for e in binding), (
            f"a re-send whose text differs from the named file was "
            f"accepted: {binding}")

    check("p12_repeat_text_mismatch_fails",
          "a re-send whose text differs from the file it names is accepted",
          p12)

    def p13():
        tmp = tempfile.mkdtemp(prefix="check_prompts_render_check_")
        binding, entry = repeated(tmp, [
            (repeat, original, "# Dispatch\n\nSame text.\n"),
        ])
        assert not entry, f"a claiming dispatch-note is not a valid entry: {entry}"
        assert any("2026-09-25-01.md" in e and "2026-09-25-02.md" in e
                   and "Repeat-of" in e for e in binding), (
            f"a re-send naming a missing file was accepted: {binding}")

    check("p13_repeat_of_missing_file_fails",
          "a re-send naming a file that does not exist is accepted",
          p13)

    total = 13
    print(f"\n{'FAIL' if failures else 'PASS'}: {len(failures)} of "
          f"{total} checks failed{': ' + ', '.join(failures) if failures else ''}")
    return 1 if failures else 0


if __name__ == "__main__":
    if "--render-check" in sys.argv:
        sys.exit(render_check())
    sys.exit(main())
