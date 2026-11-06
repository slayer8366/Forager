#!/usr/bin/env bash
# The accountant's daily run (dispatch 2026-10-26-02, RECORD -1644).
#
# Started by the systemd user timer forager-accountant.timer at 06:47
# America/Los_Angeles. Starts one Haiku session in the accounting worktree
# that counts the previous UTC day's tokens and pushes the report on the
# branch `accounting`, no PR. Profile: docs/process/accountant-profile.md.
#
# Usage: run-daily.sh [DAY [LABEL]]
#   DAY    UTC day to cover, YYYY-MM-DD; default yesterday UTC.
#   LABEL  report name, default DAY: the report is docs/accounting/LABEL.md and
#          its evidence docs/accounting/evidence/LABEL/. Only for a rerun of a
#          day that already has a report (e.g. 2026-10-25-rerun).
#
# A failed run logs its exit code and is not retried. The run refuses to start
# if the report or its evidence folder already exists, so a report is never
# overwritten.
#
# Tool permissions are given here on the command line only. No Claude Code
# settings file is read from the user level (--setting-sources project,local),
# so the user-level allow list does not widen this run; no MCP server is loaded
# (--strict-mcp-config with no --mcp-config); the built-in tools are cut to
# the six it needs (--tools); anything not allowed below is denied without a
# prompt (--permission-mode dontAsk, --permission-prompts none). Write is
# governed by Edit(...) rules (a Write(...) rule did not allow a Write in a
# test on 2.1.300); "//" starts an absolute path.

set -u

CLAUDE=/home/zynergy-labs/.local/bin/claude
WT=/home/zynergy-labs/.cache/forager-wt/accounting
LOGDIR=/home/zynergy-labs/.cache/accountant
PROFILE_REFS="origin/pre-main origin/accountant-profile"
TIMEOUT=2h

DAY="${1:-$(date -u -d yesterday +%F)}"
LABEL="${2:-$DAY}"

mkdir -p "$LOGDIR"
LOG="$LOGDIR/$LABEL.log"
RUNDIR="$LOGDIR/$LABEL"

log() { echo "[$(date -u +%FT%TZ)] $*" >>"$LOG"; }
fail() { log "FAILED exit=$1: $2"; exit "$1"; }

log "start: day=$DAY label=$LABEL"

[[ "$DAY" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail 2 "day is not YYYY-MM-DD: $DAY"
[[ "$LABEL" =~ ^[0-9A-Za-z._-]+$ ]] || fail 2 "label has characters other than letters, digits, . _ -: $LABEL"

REPORT="docs/accounting/$LABEL.md"
EVIDENCE="docs/accounting/evidence/$LABEL"

cd "$WT" || fail 3 "no accounting worktree at $WT"
[ "$(git rev-parse --abbrev-ref HEAD)" = accounting ] || fail 3 "worktree is not on branch accounting"
[ -z "$(git status --porcelain)" ] || fail 3 "accounting worktree is not clean"
git fetch -q origin >>"$LOG" 2>&1 || fail 4 "git fetch failed"
git merge -q --ff-only origin/accounting >>"$LOG" 2>&1 || fail 4 "accounting does not fast-forward to origin/accounting"
[ ! -e "$REPORT" ] || fail 5 "$REPORT already exists; not overwriting (rerun under another LABEL)"
[ ! -e "$EVIDENCE" ] || fail 5 "$EVIDENCE already exists; not overwriting"

# The profile and the RECORD, copied out of git for the session to read.
mkdir -p "$RUNDIR"
PROFILE="$RUNDIR/accountant-profile.md"
PROFILE_FROM=""
for ref in $PROFILE_REFS; do
  if git show "$ref:docs/process/accountant-profile.md" >"$PROFILE" 2>/dev/null; then
    PROFILE_FROM="$ref"; break
  fi
done
[ -n "$PROFILE_FROM" ] || fail 6 "accountant profile not found on: $PROFILE_REFS"
# The RECORD lives on whichever records branch is current, not on pre-main
# until it merges (on 2026-10-26 pre-main lacked -1642 and -1644). Take it from
# the ref among origin/pre-main and origin/records* whose last change to
# RECORD.md is newest, unless ACCOUNTANT_RECORD_REF names one.
RECORD_REF="${ACCOUNTANT_RECORD_REF:-}"
if [ -z "$RECORD_REF" ]; then
  RECORD_REF=$(for r in $(git for-each-ref --format='%(refname:short)' refs/remotes/origin/pre-main 'refs/remotes/origin/records*'); do
      echo "$(git log -1 --format=%ct "$r" -- RECORD.md) $r"; done | sort -n | tail -1 | cut -d' ' -f2)
fi
RECORD="$RUNDIR/RECORD.md"
git show "$RECORD_REF:RECORD.md" >"$RECORD" 2>/dev/null || fail 6 "RECORD.md not found on ${RECORD_REF:-any ref}"
log "profile from $PROFILE_FROM ($(git rev-parse --short "$PROFILE_FROM")); RECORD from $RECORD_REF ($(git rev-parse --short "$RECORD_REF")); worktree at $(git rev-parse --short HEAD)"

PROMPT="You are Thistle Plumbline, the accountant (docs/process/accountant-profile.md).

1. Read CLAUDE.md in this directory, then the profile at $PROFILE. Rulings are in $RECORD (search it; do not read it whole).
2. Count the logged tokens for the UTC day $DAY (00:00 to 24:00) from ~/.claude/projects/, as the profile says: one script in $EVIDENCE/ (the folder exists), its raw output beside it. Only the script reads ~/.claude/projects/; you do not list or read it. You may start from the newest earlier script under docs/accounting/evidence/: read it, then write your copy with the Write tool. Run it as: python3 -I $EVIDENCE/<script>.py > $EVIDENCE/<output>.txt
3. Write the report at $REPORT, in the profile's five parts.
4. Commit only $REPORT and $EVIDENCE/ with git add and git commit -m on plain text (no command substitution), ending the message with the line: Co-Authored-By: Claude <noreply@anthropic.com>
   Then run exactly: git push origin accounting
5. Run shell commands one at a time from this directory, with relative paths, no && or ; chains and no cd: only those forms are permitted, and anything else is refused. No PR. Change nothing else, and start no other session or helper. If something stops you, say what and stop."

# The evidence folder is made here, so the session needs no mkdir; removed
# again below if the session leaves it empty.
mkdir -p "$EVIDENCE"

log "claude: model haiku, prompt ${#PROMPT} chars; session output follows"

timeout "$TIMEOUT" "$CLAUDE" -p "$PROMPT" \
  --model haiku \
  --name "accountant $LABEL" \
  --output-format stream-json --verbose \
  --setting-sources project,local \
  --strict-mcp-config \
  --disable-slash-commands \
  --tools "Bash,Read,Write,Edit,Glob,Grep" \
  --permission-mode dontAsk \
  --permission-prompts none \
  --add-dir "$RUNDIR" \
  --allowedTools \
    "Read(//home/zynergy-labs/.cache/forager-wt/accounting/**)" \
    "Read(/$RUNDIR/**)" \
    "Glob" "Grep" \
    "Edit(//home/zynergy-labs/.cache/forager-wt/accounting/$EVIDENCE/**)" \
    "Edit(//home/zynergy-labs/.cache/forager-wt/accounting/$REPORT)" \
    "Bash(python3 -I $EVIDENCE/*)" "Bash(python3 -I $WT/$EVIDENCE/*)" \
    "Bash(mkdir -p $EVIDENCE)" \
    "Bash(git status)" "Bash(git status *)" \
    "Bash(git diff *)" "Bash(git log *)" \
    "Bash(git add $REPORT $EVIDENCE)" "Bash(git add $REPORT $EVIDENCE/)" \
    "Bash(git add $REPORT)" "Bash(git add $EVIDENCE)" "Bash(git add $EVIDENCE/)" \
    "Bash(git add $EVIDENCE/*)" \
    "Bash(git commit -m *)" \
    "Bash(git push origin accounting)" \
  </dev/null >>"$LOG" 2>&1
RC=$?
echo >>"$LOG"
rmdir "$EVIDENCE" 2>/dev/null
log "claude exit=$RC"

# What the session left behind, checked from outside it.
git fetch -q origin >>"$LOG" 2>&1
log "worktree HEAD $(git rev-parse --short HEAD); origin/accounting $(git rev-parse --short origin/accounting); report present: $([ -e "$REPORT" ] && echo yes || echo no); pushed: $(git merge-base --is-ancestor HEAD origin/accounting && echo yes || echo no)"
log "worktree status: $(git status --porcelain | wc -l) changed or untracked paths"
[ "$RC" -eq 0 ] || fail "$RC" "claude session failed"
[ -e "$REPORT" ] || fail 7 "session ended without writing $REPORT"
[ -z "$(git status --porcelain)" ] || fail 8 "session left uncommitted changes"
git merge-base --is-ancestor HEAD origin/accounting || fail 9 "report commit not pushed"
log "done"
