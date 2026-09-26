"""PreToolUse on Bash, every role: history is the operator's.

The merge rule. A Bash command containing `gh pr merge` is denied unless
every condition holds, and each denial names the condition that failed:

(a) Role. The calling agent's role (the config's agent_roles, looked up by
    the payload's agent_type) is `coder`. The main session, with no
    agent_type, is the planner and is denied.
(b) Form. The command is one segment, with no other command in it. Its
    tokens are `gh pr merge <N>`, N all digits, and exactly one method
    flag: `--merge`/`-m` or `--squash`/`-s`. The only other flags allowed
    are `--subject`/`-t <text>`, `--body`/`-b <text>` and
    `--match-head-commit <sha>`. Anything else is denied by name:
    `--rebase`/`-r`, `--auto`, `--admin`, `--delete-branch`/`-d`,
    `--repo`/`-R`, `--disable-auto`, any unknown flag, and a missing N.
(c) Config. backup_dir is set in kit.json (`~` is expanded). Unset, the
    merge is denied with a message saying to set it.
(d) The backup. Exactly one folder directly under backup_dir holds a
    merge.json with `"pr": N`. That file is a JSON object with `pr` (int),
    `branch` (one of the config's protected_branches; any other string is
    denied naming it and the list), `sha` (40 lowercase hex) and `bundle`
    (a file name in that folder). In that folder, MANIFEST.sha256 lists at
    least merge.json and the bundle, and every listed file's sha256
    matches; `git bundle list-heads <bundle>` lists the pair
    `<sha> refs/remotes/origin/<branch>` on one line (`sha` on another
    ref, or that ref at another sha, is denied naming what the bundle
    lists); and backup_dir/INDEX.md has one line holding each of the
    folder's name, `#<N>` and `sha` as a whole token: the name bounded by
    characters that are not letters, digits, `-` or `_`, N not followed by
    a digit, the sha bounded by characters that are not hex digits (the
    pre-merge SHA, as the owner's chosen option put it: "an INDEX.md line
    naming PR N and the pre-merge SHA"). What this does not prove: the
    pull request's base branch is not read, so `branch` is checked against
    the config and not against the PR; and the bundle's content is
    verified only by its listed head and the manifest, not by unpacking
    it.
(e) Freshness. First locally: `git rev-parse origin/<branch>` in the
    payload's cwd equals `sha` (the hook does not fetch; coder.md tells the
    coder to fetch first, and this denial names it). Then on the remote:
    `git ls-remote --quiet origin refs/heads/<branch>` in the same cwd,
    with the kit's command timeout (guardlib.TIMEOUT: 20 seconds by
    default, `<guard_env_prefix>TIMEOUT` overrides), gives `sha`. A
    different sha is denied naming both (the branch moved on the remote
    after the backup). A non-zero exit is denied naming the exit code and
    nothing git printed (its stderr can carry the remote's URL, credentials
    included), and empty output is denied as listing no such ref; both say
    "the remote could not be read": a merge is not run blind. A timeout, in
    this or any other command the hook runs, is guardlib's deny naming the
    command and the seconds.

A merge that passes gets no decision from this check, like any other
allowed call. `git merge` handling is unchanged by this rule.

Otherwise, merging into a protected branch is the operator's approval, and
a history rewrite is run by the operator by hand. The protected branches
are the config's protected_branches. Every rule below is decided on a
command segment's tokens (the shell punctuation `;`, `&&`, `||`, `|`, `&`,
`(`, `)` and a newline outside quotes separate segments), by the
segment's command word and its arguments. A guarded word anywhere else (a
commit message, a grep pattern, an echo, a Python string) denies nothing.
Blocked:

- a command word whose basename is `git` (`/usr/bin/git`, `./git`), read
  past its global options (`-C <dir>`, `-c <k>=<v>`, `--no-pager`,
  `--git-dir=<dir>`, `--work-tree=<dir>`, and the two-token `--git-dir
  <dir>` and `--work-tree <dir>`) to its subcommand:
  - `push` with a force flag (`--force`, `--force-with-lease`, either with
    or without `=value`, or a short cluster holding `f` such as `-uf`) or
    a refspec beginning with `+`: a force push to its destination,
    whatever the destination, since the kit's agents never force-push;
  - `push` to a protected branch: an explicit refspec naming one, --all or
    --mirror, or a push with no refspec (or HEAD) while on one; and a push
    with no refspec (or HEAD) from a checkout the walk cannot determine
    (below), denied by name;
  - `merge` while the repository is on a protected branch. The branch is
    read in the `-C` directory, resolved against the effective directory
    (below), else in the effective directory itself. With `--git-dir` or
    `--work-tree` given in either form, or with the effective directory
    unknown, the checkout the merge runs in cannot be determined, and the
    merge is denied for that reason;
  - `filter-repo` and `filter-branch` as the subcommand, and a command
    word whose basename is `git-filter-repo` or `git-filter-branch`;
- a command word whose basename is `gh`:
  - `pr merge` (the adjacent tokens `pr`, `merge` after `gh`), unless the
    merge rule above lets it through. Condition (b) reads the whole
    command, so the segment must be the whole command, with nothing
    before `gh`;
  - `api` that writes to a pull request's merge endpoint (below), and any
    `api` whose tokens contain `mergePullRequest`, the GraphQL merge
    mutation, denied for every role;
  - `alias set`, denied for every role: an alias can hide any command;
- a command word whose basename is `sh`, `bash`, `dash`, `zsh` or `ksh`
  with a `-c` flag (alone or in a short cluster such as `-lc` or `-ec`):
  the token after it is a command string, walked under these same rules
  with a copy of the directory state, so a `cd` inside it does not reach
  the segments after it. `eval`: its arguments, joined by spaces, are
  walked under the same rules with the shared directory state (a `cd`
  inside `eval` does reach the shell that ran it). A string that cannot be
  parsed is denied if it mentions a guarded command (the mention rule
  below), else let through. Interpreters (`python3 -c` and the like) are
  not parsed (B-02).

The merge endpoint. A `gh api` segment with an endpoint token
`repos/<owner>/<repo>/pulls/<N>/merge`, with or without a leading `/`, is
denied for every role when it would write: `-X`/`--method` (as a separate
token, with `=`, or attached as `-XPUT`) with a method other than GET, a
field flag (`-f`, `-F`, `--field`, `--raw-field`), or `--input`. It is
denied outright, not tested against the merge rule: the one allowed route
to merge is `gh pr merge` under that rule. A plain GET, which only checks
whether the pull request is merged, is let through.

The walk reads a command's segments in order and keeps an effective
directory, which starts at the payload's cwd. `cd <dir>` and `pushd <dir>`
set it, resolved as a `git -C` directory is (below) against the current
effective directory; `cd` alone and `cd ~` set it to the home directory;
`popd` restores the directory pushed last. A `cd`, `pushd` or `popd` the
walk cannot resolve (`cd -`, a `$` or backtick in the argument, a flag,
more than one argument, `pushd` alone, `popd` with nothing pushed) makes
the effective directory unknown, and a `git push` in an unknown directory
with no explicit refspec, or with `HEAD`, is denied by name: the checkout
it pushes from cannot be determined. A push there with an explicit refspec
is checked by its refspec alone. A `git merge` there is denied (above). A
`(` saves the directory state and the matching `)` restores it, so a `cd`
inside a subshell does not reach the segments after it. `git -C <dir>`
still overrides the effective directory for that one command, resolved
against it.

A segment's command word is found after dropping its leading tokens while
they are a shell keyword (`{`, `}`, `!`, `if`, `then`, `else`, `elif`,
`fi`, `do`, `done`, `while`, `until`), a wrapper (`time`, `command`,
`exec`, `builtin`, `nohup`, `env` with its `-i`, `-u NAME` and
`NAME=value` arguments, `nice` with `-n N`) or a `NAME=value` assignment,
for the `cd` reading, the git reading, the gh reading and the shell and
`eval` readings alike.

The branch is read in the repository that `git -C <dir>` names, or else in
the effective directory, for the push check and the `git merge` check
alike. A leading `~` or `~user` in a directory is expanded with
os.path.expanduser, as the shell would, before the branch is read. Nothing
else is expanded: `$VAR` stays as written (B-04). After that expansion, a
relative directory is resolved against the effective directory, where git
would run it, not the hook process's own directory; an absolute one, or
one starting with `~`, is used as it is. A quoted `~` (`git -C '~/x'`) is
expanded as well, unlike in the shell: such a command fails in git anyway,
so the check errs toward reading the home-directory repo.

Before a command is parsed, heredoc bodies are removed. For each `<<WORD`,
`<<-WORD`, `<<'WORD'` or `<<"WORD"` that stands outside single quotes,
double quotes and `#` comments (the states that separate commands below,
carried from one kept line to the next; a marker inside quotes or a
comment opens nothing), and that is not part of a `<<<` here-string (a
`<<` preceded or followed by `<` is no marker), the lines after that line,
up to and including the first line that is only WORD (after `<<-`, leading
tabs are allowed), are data, not commands, and are dropped. A heredoc with
no such closing line is not removed. Newlines outside quotes then separate
commands as `;` does; a backslash before a newline continues the line and
does not separate.

The mention rule. A command that still cannot be parsed (an unbalanced
quote) is denied when its text mentions a guarded command: `git`, its
global options, then `push` or `merge`; `gh` then `pr merge` or `api`;
`filter-repo`; `filter-branch`; `mergePullRequest`; or a `--force`/`-f`
flag after a `git push`. The reason says it could not be parsed and what
it mentions. Any other unparseable command is let through. The mention
patterns exist for this case only; nothing else is matched against the
text.

Known bypasses: .claude/hooks/BYPASSES.md B-01, B-02, B-04, B-09
"""
import hashlib
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import guardlib as g  # noqa: E402

# The merge rule's form, condition (b).
MERGE_METHODS = {"--merge", "-m", "--squash", "-s"}
MERGE_VALUE_FLAGS = {"--subject", "-t", "--body", "-b", "--match-head-commit"}
MERGE_FORBIDDEN = {"--rebase", "-r", "--auto", "--admin", "--delete-branch", "-d",
                   "--repo", "-R", "--disable-auto"}
SHA40 = re.compile(r"[0-9a-f]{40}")
MANIFEST_LINE = re.compile(r"([0-9a-fA-F]{64}) [ *](.+)")

# git's global options the walk reads past: those whose value is the next
# token, and the two that name the repository elsewhere (either form).
GIT_OPTS_WITH_VALUE = {"-C", "-c", "--git-dir", "--work-tree"}
GIT_LOCATION_OPTS = ("--git-dir", "--work-tree")
FORCE_FLAGS = {"--force", "--force-with-lease"}
PUSH_OPTS_WITH_VALUE = {"-o", "--push-option", "--repo", "--receive-pack", "--exec"}
FILTER_SUBCOMMANDS = {"filter-repo", "filter-branch"}
FILTER_COMMANDS = {"git-filter-repo", "git-filter-branch"}
# The shells whose `-c` string is walked, and the shell options whose value
# is the next token (so `bash -o pipefail -c '...'` reaches its `-c`).
SHELLS = {"sh", "bash", "dash", "zsh", "ksh"}
SHELL_OPTS_WITH_VALUE = {"-o", "+o", "-O", "+O", "--rcfile", "--init-file"}
HEREDOC = re.compile(r"<<(-?)(['\"]?)(\w+)\2")
# The walk's vocabulary (module docstring): the tokens dropped before a
# segment's command word, the directory words it follows, and what makes a
# directory argument unresolvable.
SHELL_KEYWORDS = {"{", "}", "!", "if", "then", "else", "elif", "fi", "do", "done",
                  "while", "until"}
WRAPPERS = {"time", "command", "exec", "builtin", "nohup"}
ASSIGNMENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*=")
DIRECTORY_WORDS = ("cd", "pushd", "popd")
UNRESOLVED = re.compile(r"[$`]")
# The merge endpoint check, over a `gh api` segment's tokens: the endpoint,
# the flags that make the call a write, and the GraphQL merge mutation.
MERGE_ENDPOINT = re.compile(
    r"(?<![\w-])/?repos/[^\s/'\"]+/[^\s/'\"]+/pulls/[^\s/'\"]+/merge(?![\w/.-])")
API_WRITE_FLAGS = {"-f", "-F", "--field", "--raw-field", "--input"}
GRAPHQL_MERGE = "mergePullRequest"
# The mention rule (module docstring): patterns over text that cannot be
# tokenized, and nothing else. Each with the name the denial gives it.
GIT_OPTS = (r"(?:\s+(?:-C\s+\S+|-c\s+\S+|--no-pager|--git-dir(?:=|\s+)\S+"
            r"|--work-tree(?:=|\s+)\S+))*")
MENTIONS = [
    ("`git push`", re.compile(r"\bgit\b" + GIT_OPTS + r"\s+push\b")),
    ("a force flag after `git push`", re.compile(
        r"\bgit\b" + GIT_OPTS + r"\s+push\b[^;&|]*?\s"
        r"(?:--force(?:-with-lease)?(?:=\S*)?|-[A-Za-z]*f[A-Za-z]*)(?=\s|$)")),
    ("`git merge`", re.compile(r"\bgit\b" + GIT_OPTS + r"\s+merge\b(?!-)")),
    ("`gh pr merge`", re.compile(r"\bgh\b[^;&|]*\bpr\b[\s'\"]*merge\b")),
    ("`gh api`", re.compile(r"\bgh\b[^;&|]*?\sapi\b")),
    ("`filter-repo`", re.compile(r"\bfilter-repo\b")),
    ("`filter-branch`", re.compile(r"\bfilter-branch\b")),
    ("`mergePullRequest`", re.compile(GRAPHQL_MERGE)),
]


def heredoc_markers(line, quote):
    """(the HEREDOC matches on line that start outside quotes and # comments,
    the quote state at the line's end). quote is the state the line starts
    in. The states and their changes are command_lines'."""
    code, i, n = set(), 0, len(line)
    while i < n:
        c = line[i]
        if quote == "'":
            if c == "'":
                quote = None
        elif quote == '"':
            if c == "\\" and i + 1 < n:
                i += 1
            elif c == '"':
                quote = None
        elif c == "\\" and i + 1 < n:
            i += 1
        elif c in "'\"":
            quote = c
        elif c == "#":
            break
        else:
            code.add(i)
        i += 1
    # A `<<` preceded or followed by `<` is part of a `<<<` here-string.
    return ([m for m in HEREDOC.finditer(line)
             if m.start() in code and m.start() + 1 in code
             and line[m.start() - 1:m.start()] != "<"
             and line[m.start() + 2:m.start() + 3] != "<"], quote)


def strip_heredocs(command):
    """The command with heredoc bodies removed, per the module docstring:
    for each heredoc operator outside quotes and # comments, the lines after
    its line up to and including the first line that is only WORD (leading
    tabs allowed after <<-). A heredoc with no closing line is kept. The
    quote state carries from one kept line to the next; dropped lines are
    data and change no state."""
    lines = command.split("\n")
    drop = set()
    quote = None
    for i, line in enumerate(lines):
        if i in drop:
            continue
        markers, quote = heredoc_markers(line, quote)
        for m in markers:
            tabs_ok, word = m.group(1), m.group(3)
            for j in range(i + 1, len(lines)):
                if (lines[j].lstrip("\t") if tabs_ok else lines[j]) == word:
                    drop.update(range(i + 1, j + 1))
                    break
    return "\n".join(line for i, line in enumerate(lines) if i not in drop)


def command_lines(text):
    """text split at newlines that separate commands: those outside single
    and double quotes, not escaped by a backslash (a continuation), and not
    inside a # comment's text. The states follow shlex's posix ones, so a
    quote that shlex would see as open keeps its newlines too."""
    pieces, cur, quote, i, n = [], [], None, 0, len(text)
    while i < n:
        c = text[i]
        if quote == "'":
            if c == "'":
                quote = None
        elif quote == '"':
            if c == "\\" and i + 1 < n:
                cur.append(c)
                i += 1
                c = text[i]
            elif c == '"':
                quote = None
        elif c == "\\" and i + 1 < n:
            cur.append(c)
            i += 1
            c = text[i]
        elif c in "'\"":
            quote = c
        elif c == "#":
            end = text.find("\n", i)
            end = n if end < 0 else end
            cur.append(text[i:end])
            i = end
            continue
        elif c == "\n":
            pieces.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(c)
        i += 1
    pieces.append("".join(cur))
    return pieces


def tokenize(text):
    """The shell tokens of text's command lines, `;` between lines. Raises
    ValueError when a line cannot be tokenized (an unbalanced quote)."""
    tokens = []
    for piece in command_lines(text):
        tokens += g.shell_tokens(piece) + [";"]
    return tokens


def mentions(text):
    """The names of the guarded commands text mentions (the mention rule)."""
    return [name for name, pattern in MENTIONS if pattern.search(text)]


def dash_c_directory(directory, cwd):
    """The directory a `git -C <directory>` run from cwd works in: `~`
    expanded first, then a relative path joined to cwd."""
    expanded = os.path.expanduser(directory)
    if directory.startswith("~") or os.path.isabs(expanded):
        return expanded
    return os.path.join(cwd, expanded)


def current_branch(directory):
    r = g.run_command(["git", "-C", directory, "symbolic-ref", "--short", "-q", "HEAD"])
    if r.returncode == 0:
        return r.stdout.strip(), None
    if r.returncode == 1 and not r.stderr.strip():
        return "(detached HEAD)", None
    return None, r.stderr.strip() or f"git exited {r.returncode}"


def command_start(seg):
    """The index of seg's command word: its leading tokens are dropped while
    they are a shell keyword, a wrapper (with env's and nice's own
    arguments) or a NAME=value assignment, per the module docstring."""
    i = 0
    while i < len(seg):
        t = seg[i]
        if t in SHELL_KEYWORDS or t in WRAPPERS or ASSIGNMENT.match(t):
            i += 1
        elif t == "env":
            i += 1
            while i < len(seg):
                if seg[i] == "-i" or ASSIGNMENT.match(seg[i]):
                    i += 1
                elif seg[i] == "-u" and i + 1 < len(seg):
                    i += 2
                else:
                    break
        elif t == "nice":
            i += 1
            if seg[i:i + 1] == ["-n"] and i + 1 < len(seg):
                i += 2
        else:
            break
    return i


def resolve_directory(directory, effective):
    """dash_c_directory against the effective directory, or None when that
    is unknown and directory is relative."""
    if effective is None and not (directory.startswith("~") or os.path.isabs(directory)):
        return None
    return dash_c_directory(directory, effective)


def follow_directory_change(seg, state):
    """Apply a `cd`, `pushd` or `popd` segment to state, {"dir": the
    effective directory or None when unknown, "stack": the directories
    pushd left}. True when seg was one of them."""
    seg = seg[command_start(seg):]
    if not seg or seg[0] not in DIRECTORY_WORDS:
        return False
    word, args = seg[0], seg[1:]
    if word == "popd":
        state["dir"] = state["stack"].pop() if not args and state["stack"] else None
        return True
    if word == "pushd":
        state["stack"].append(state["dir"])
    if not args:
        state["dir"] = os.path.expanduser("~") if word == "cd" else None
    elif len(args) != 1 or args[0].startswith("-") or UNRESOLVED.search(args[0]):
        state["dir"] = None
    else:
        state["dir"] = resolve_directory(args[0], state["dir"])
    return True


def git_invocation(seg):
    """(directory override or None, subcommand, args, located) for a segment
    that runs git, else None. The command word is read after command_start's
    drop, and is git when its basename is `git`. The global options are
    skipped (GIT_OPTS_WITH_VALUE take the next token as their value; any
    other `-x` stands alone); located is True when `--git-dir` or
    `--work-tree` was given in either form."""
    seg = seg[command_start(seg):]
    if not seg or os.path.basename(seg[0]) != "git":
        return None
    i, directory, located = 1, None, False
    while i < len(seg) and seg[i].startswith("-"):
        opt = seg[i]
        if opt in GIT_OPTS_WITH_VALUE and i + 1 < len(seg):
            if opt == "-C":
                directory = seg[i + 1]
            located = located or opt in GIT_LOCATION_OPTS
            i += 2
        else:
            located = located or opt.startswith(tuple(o + "=" for o in GIT_LOCATION_OPTS))
            i += 1
    if i >= len(seg):
        return None
    return directory, seg[i], seg[i + 1:], located


def force_flag(args):
    """The first force flag among a push's arguments (module docstring),
    else None."""
    for a in args:
        if a.startswith("--"):
            if a.split("=", 1)[0] in FORCE_FLAGS:
                return a
        elif a.startswith("-") and "f" in a[1:]:
            return a
    return None


def push_targets_protected(args, branch, protected):
    """(kind, problem) for a push that is denied, else None. kind is
    "force" (a refspec beginning with `+`, whatever its destination),
    "unknown" (the push needs the current branch and branch is None, the
    checkout being undetermined) or "protected" (a protected destination,
    --all or --mirror)."""
    refs = set(protected) | {f"refs/heads/{b}" for b in protected}
    positional, i = [], 0
    while i < len(args):
        a = args[i]
        if a in ("--all", "--mirror"):
            return ("protected", f"`git push {a}` pushes {', '.join(protected)} along "
                                 f"with every other branch")
        if a in PUSH_OPTS_WITH_VALUE:
            i += 2
            continue
        if not a.startswith("-"):
            positional.append(a)
        i += 1
    refspecs = positional[1:]
    if not refspecs:
        if branch is None:
            return ("unknown", "a push with no refspec pushes the current branch, and "
                               "the checkout it pushes from cannot be determined")
        return (("protected", f"a push with no refspec pushes the current branch, "
                              f"which is {branch}")
                if branch in protected else None)
    for spec in refspecs:
        src, _, dst = spec.partition(":")
        dst = dst or src
        if spec.startswith("+"):
            return ("force", f"refspec {spec!r} is a force push to {dst.lstrip('+')}")
        if dst == "HEAD":
            if branch is None:
                return ("unknown", f"refspec {spec!r} pushes the current branch, and "
                                   f"the checkout it pushes from cannot be determined")
            dst = branch
        if dst in refs:
            return ("protected", f"refspec {spec!r} pushes to {dst}")
    return None


def push_problem(args, directory, state, protected):
    """The push check for a `git push` segment: a force flag, then the
    protected-branch check against the `git -C` directory or the effective
    directory. The deny reason, or None."""
    flag = force_flag(args)
    if flag:
        return (f"history_guard: force-push (`{flag}`) rewrites history on the remote "
                f"and is never run by an agent.")
    effective = resolve_directory(directory, state["dir"]) if directory else state["dir"]
    branch = None
    if effective is not None:
        branch, err = current_branch(effective)
        if err:
            return f"history_guard: push blocked: could not read the current branch ({err})."
    found = push_targets_protected(args, branch, protected)
    if not found:
        return None
    kind, problem = found
    if kind == "force":
        return (f"history_guard: force-push ({problem}) rewrites history on the remote "
                f"and is never run by an agent.")
    if kind == "unknown":
        return (f"history_guard: push blocked: {problem}: the `cd`, `pushd` or `popd` "
                f"before it could not be followed, so its target cannot be checked.")
    return (f"history_guard: push to a protected branch blocked: {problem}. A protected "
            f"branch changes only through a PR the operator merges.")


def git_merge_problem(directory, located, state, protected):
    """The `git merge` check: the branch of the `git -C` directory or the
    effective directory; denied when it is protected, or when the checkout
    cannot be determined. The deny reason, or None."""
    if located:
        effective, cause = None, "`--git-dir` or `--work-tree` names the repository"
    else:
        effective = resolve_directory(directory, state["dir"]) if directory else state["dir"]
        cause = "the `cd`, `pushd` or `popd` before it could not be followed"
    if effective is None:
        return (f"history_guard: `git merge` blocked: the checkout it runs in cannot be "
                f"determined ({cause}), so it is denied as `git merge` while on "
                f"{' or '.join(protected)} is. Merging into a protected branch is the "
                f"operator's approval.")
    branch, err = current_branch(effective)
    if err:
        return (f"history_guard: `git merge` blocked: could not read the current branch "
                f"of {effective} ({err}).")
    if branch in protected:
        return (f"history_guard: `git merge` while on {branch} is blocked. Merging into a "
                f"protected branch is the operator's approval.")
    return None


def filter_problem(name):
    return (f"history_guard: `{name}` rewrites history and is run by the operator by "
            f"hand: history rewriting is guarded.")


def git_problem(inv, state, ctx):
    """The rules for a git segment, by subcommand."""
    directory, sub, args, located = inv
    if sub == "push":
        return push_problem(args, directory, state, ctx["protected"])
    if sub == "merge":
        return git_merge_problem(directory, located, state, ctx["protected"])
    if sub in FILTER_SUBCOMMANDS:
        return filter_problem(f"git {sub}")
    return None


def merge_role_problem(payload):
    """Condition (a): the caller's role, found the way role_guard finds it."""
    agent = payload.get("agent_type")
    if not agent:
        return "the main session is the planner, and only the coder role may merge"
    role = g.CONFIG["agent_roles"].get(agent)
    if role is None:
        return f"agent {agent!r} has no role in agent_roles; only the coder role may merge"
    if role != "coder":
        return f"agent {agent!r} has role {role!r}; only the coder role may merge"
    return None


def merge_form(command):
    """Condition (b): (N, None) for an allowed form, else (None, problem)."""
    pieces = [p for p in command_lines(command) if p.strip()]
    if len(pieces) != 1:
        return None, "it must be one command, with nothing else on another line"
    try:
        tokens = g.shell_tokens(pieces[0])
    except ValueError as exc:
        return None, f"it could not be parsed ({exc})"
    if any(g.PUNCTUATION.match(t) for t in tokens):
        return None, "it must be one command, with no other command, pipe or redirection in it"
    if tokens[:3] != ["gh", "pr", "merge"]:
        return None, "it must start with `gh pr merge`, with nothing before it"
    number, methods, i = None, [], 3
    while i < len(tokens):
        a = tokens[i]
        name = a.split("=", 1)[0] if a.startswith("--") else a
        if name in MERGE_FORBIDDEN:
            return None, f"`{name}` is not allowed"
        if a in MERGE_METHODS:
            methods.append(a)
        elif a in MERGE_VALUE_FLAGS:
            if i + 1 >= len(tokens):
                return None, f"`{a}` needs a value"
            i += 1
        elif a.startswith("-"):
            return None, f"unknown flag `{a}` is not allowed"
        elif number is not None:
            return None, f"a second argument `{a}`; only one pull request number is allowed"
        elif not re.fullmatch(r"[0-9]+", a):
            return None, f"`{a}` is not a pull request number (digits only)"
        else:
            number = int(a)
        i += 1
    if number is None:
        return None, "no pull request number"
    if len(methods) != 1:
        return None, (f"exactly one of --merge/-m or --squash/-s is required, "
                      f"got {len(methods)}")
    return number, None


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def merge_backup_problem(number, cwd):
    """Conditions (c), (d) and (e): (label, problem) or None."""
    setting = g.CONFIG.get("backup_dir")
    if setting is None or not setting.strip():
        return ("(c) config", "backup_dir is not set. Set it in .claude/kit.json to the "
                              "directory that holds merge backups.")
    root = Path(os.path.expanduser(setting))
    if not root.is_dir():
        return ("(d) backup", f"backup_dir {root} is not a directory, so it holds no "
                              f"merge.json for PR {number}")
    found = []
    for child in sorted(root.iterdir()):
        path = child / "merge.json"
        if not (child.is_dir() and path.is_file()):
            continue
        try:
            data = json.loads(path.read_text())
        except (OSError, ValueError):
            continue
        if isinstance(data, dict) and type(data.get("pr")) is int and data["pr"] == number:
            found.append((child, data))
    if not found:
        return ("(d) backup", f"no folder directly under {root} holds a merge.json "
                              f"with \"pr\": {number}")
    if len(found) > 1:
        return ("(d) backup", f"more than one folder under {root} holds a merge.json "
                              f"with \"pr\": {number}: "
                              f"{', '.join(c.name for c, _ in found)}")
    folder, data = found[0]
    branch, sha, bundle = data.get("branch"), data.get("sha"), data.get("bundle")
    protected = g.CONFIG["protected_branches"]
    if not (isinstance(branch, str) and branch in protected):
        return ("(d) backup", f"{folder}/merge.json's `branch` {branch!r} is not one of "
                              f"the protected branches ({', '.join(protected)}); the "
                              f"backup must be of the branch the merge lands on")
    if not (isinstance(sha, str) and SHA40.fullmatch(sha)):
        return ("(d) backup", f"{folder}/merge.json's `sha` is not 40 lowercase hex")
    if not (isinstance(bundle, str) and bundle not in ("", ".", "..")
            and "/" not in bundle and os.sep not in bundle
            and (folder / bundle).is_file()):
        return ("(d) backup", f"{folder}/merge.json's `bundle` is not the name of a "
                              f"file in that folder")

    manifest = folder / "MANIFEST.sha256"
    if not manifest.is_file():
        return ("(d) backup", f"{folder} has no MANIFEST.sha256")
    listed = {}
    for line in manifest.read_text().splitlines():
        if not line.strip():
            continue
        m = MANIFEST_LINE.fullmatch(line)
        if not m:
            return ("(d) backup", f"MANIFEST.sha256 in {folder} has a line it cannot "
                                  f"read: {line!r}")
        listed[m.group(2)] = m.group(1).lower()
    for name in ("merge.json", bundle):
        if name not in listed:
            return ("(d) backup", f"MANIFEST.sha256 in {folder} does not list {name}")
    for name, digest in listed.items():
        path = folder / name
        if not path.is_file():
            return ("(d) backup", f"MANIFEST.sha256 in {folder} lists {name}, which "
                                  f"is missing")
        if sha256_of(path) != digest:
            return ("(d) backup", f"MANIFEST.sha256 in {folder} does not match {name}: "
                                  f"the file changed after the manifest was written")

    r = g.run_command(["git", "bundle", "list-heads", str(folder / bundle)], cwd=cwd)
    if r.returncode != 0:
        return ("(d) backup", f"`git bundle list-heads` could not read {folder / bundle} "
                              f"({r.stderr.strip() or f'git exited {r.returncode}'})")
    heads = [line.split()[:2] for line in r.stdout.splitlines() if line.split()]
    ref = f"refs/remotes/origin/{branch}"
    if [sha, ref] not in heads:
        listed = "; ".join(" ".join(h) for h in heads) or "nothing"
        return ("(d) backup", f"the bundle {folder / bundle} does not list {sha} {ref}: "
                              f"it lists {listed}")

    index = root / "INDEX.md"
    if not index.is_file():
        return ("(d) backup", f"{index} does not exist")
    folder_mark = re.compile(r"(?<![A-Za-z0-9_-])%s(?![A-Za-z0-9_-])"
                             % re.escape(folder.name))
    pr_mark = re.compile(r"#%d(?![0-9])" % number)
    sha_mark = re.compile(r"(?<![0-9a-fA-F])%s(?![0-9a-fA-F])" % sha)
    if not any(folder_mark.search(line) and pr_mark.search(line) and sha_mark.search(line)
               for line in index.read_text().splitlines()):
        return ("(d) backup", f"INDEX.md in {root} has no line naming {folder.name}, "
                              f"#{number} and {sha}, each as a whole word")

    r = g.run_command(["git", "-C", cwd, "rev-parse", "--verify", "--quiet",
                       f"origin/{branch}"])
    tip = r.stdout.strip()
    if r.returncode != 0 or not tip:
        return ("(e) freshness", f"origin/{branch} could not be read in {cwd}. Fetch "
                                 f"first, then write the backup.")
    if tip != sha:
        return ("(e) freshness", f"origin/{branch} is {tip}, not merge.json's sha {sha}: "
                                 f"the branch moved after the backup, or the backup "
                                 f"predates the last fetch. Fetch, then write a new backup.")

    blind = "the remote could not be read ({}); a merge is not run blind."
    # A timeout here raises g.CommandTimeout, which guardlib.run denies by
    # name. git's stderr is not quoted: it can carry the remote's URL.
    r = g.run_command(["git", "ls-remote", "--quiet", "origin", f"refs/heads/{branch}"],
                      cwd=cwd)
    if r.returncode != 0:
        return ("(e) freshness", blind.format(f"`git ls-remote origin` exited "
                                              f"{r.returncode}"))
    fields = r.stdout.split()
    if len(fields) < 2:
        return ("(e) freshness", blind.format(f"`git ls-remote origin` listed no "
                                              f"refs/heads/{branch}"))
    remote = fields[0]
    if remote != sha:
        return ("(e) freshness", f"refs/heads/{branch} on the remote is {remote}, not "
                                 f"merge.json's sha {sha}: the branch moved on the remote "
                                 f"after the backup. Fetch, then write a new backup.")
    return None


def merge_problem(payload, command, cwd):
    """The merge rule in the module docstring: None if the merge may go on,
    else the reason, naming the failed condition."""
    problem = merge_role_problem(payload)
    if problem:
        return f"(a) role: {problem}."
    number, problem = merge_form(command)
    if problem:
        return f"(b) form: {problem}."
    found = merge_backup_problem(number, cwd)
    if found:
        return f"{found[0]}: {found[1]}"
    return None


def endpoint_write_problem(endpoint, what):
    return (f"history_guard: `gh api` write to a pull request's merge endpoint "
            f"({endpoint}, {what}) is denied for every role. The one allowed route to "
            f"merge is `gh pr merge`, under the merge rule (T17: role, form, backup, "
            f"freshness).")


def api_problem(args):
    """A `gh api` segment's arguments: the GraphQL merge mutation, or a write
    to a pull request's merge endpoint (module docstring). The deny reason,
    or None."""
    if any(GRAPHQL_MERGE in a for a in args):
        return (f"history_guard: `gh api` naming `{GRAPHQL_MERGE}` (the GraphQL pull "
                f"request merge) is denied for every role. The one allowed route to merge "
                f"is `gh pr merge`, under the merge rule (T17: role, form, backup, "
                f"freshness).")
    endpoint = None
    for a in args:
        m = MERGE_ENDPOINT.search(a)
        if m:
            endpoint = m.group(0)
            break
    if endpoint is None:
        return None
    methods = []
    for i, a in enumerate(args):
        if a in ("-X", "--method"):
            if i + 1 < len(args):
                methods.append(args[i + 1])
        elif a.startswith("--method="):
            methods.append(a[len("--method="):])
        elif a.startswith("-X") and len(a) > 2:
            methods.append(a[2:].lstrip("="))
    for method in methods:
        if method.upper() != "GET":
            return endpoint_write_problem(endpoint, f"method {method}")
    for a in args:
        name = a.split("=", 1)[0]
        if name in API_WRITE_FLAGS:
            return endpoint_write_problem(endpoint, f"`{name}`")
        if a[:2] in ("-f", "-F") and not a.startswith("--"):
            return endpoint_write_problem(endpoint, f"`{a[:2]}`")
    return None


def gh_problem(seg, ctx):
    """The rules for a gh segment (seg starts at the gh command word): the
    merge rule for `pr merge`, the api checks, and `alias set` or
    `alias import`."""
    rest = seg[1:]
    if any(a == "pr" and b == "merge" for a, b in zip(rest, rest[1:])):
        problem = merge_problem(ctx["payload"], ctx["command"], ctx["cwd"])
        return f"history_guard: `gh pr merge` denied, {problem}" if problem else None
    if rest[:1] == ["api"]:
        return api_problem(rest[1:])
    if rest[:1] == ["alias"]:
        for word in ("set", "import"):
            if word in rest[1:]:
                return (f"history_guard: `gh alias {word}` is denied for every role: an "
                        f"alias can hide any command.")
    return None


def shell_string(seg):
    """The command string a shell segment runs (seg starts at the shell's
    command word): the token after the first short-option cluster holding
    `c`, read past the options before it; None when there is none."""
    i = 1
    while i < len(seg) and (seg[i].startswith("-") or seg[i].startswith("+")):
        a = seg[i]
        if a in SHELL_OPTS_WITH_VALUE:
            i += 2
            continue
        if a.startswith("-") and not a.startswith("--") and "c" in a[1:]:
            return seg[i + 1] if i + 1 < len(seg) else None
        i += 1
    return None


def check_segment(seg, state, ctx):
    """One segment of the walk: a directory change, or the rules for its
    command word (module docstring). The deny reason, or None."""
    if follow_directory_change(seg, state):
        return None
    inv = git_invocation(seg)
    if inv:
        return git_problem(inv, state, ctx)
    seg = seg[command_start(seg):]
    if not seg:
        return None
    word = os.path.basename(seg[0])
    if word in FILTER_COMMANDS:
        return filter_problem(word)
    if word == "gh":
        return gh_problem(seg, ctx)
    if word in SHELLS:
        string = shell_string(seg)
        if string is None:
            return None
        return walk_text(string, {"dir": state["dir"], "stack": list(state["stack"])}, ctx)
    if word == "eval":
        return walk_text(" ".join(seg[1:]), state, ctx)
    return None


def walk_tokens(tokens, state, ctx):
    """The walk of the module docstring over a command's tokens: the reason
    the first denied segment is denied, else None. Punctuation tokens end a
    segment; in them, each `(` saves the directory state and each `)`
    restores the state saved last."""
    saved, seg = [], []
    for t in tokens + [";"]:
        if not g.PUNCTUATION.match(t):
            seg.append(t)
            continue
        if seg:
            problem = check_segment(seg, state, ctx)
            if problem:
                return problem
            seg = []
        for c in t:
            if c == "(":
                saved.append((state["dir"], list(state["stack"])))
            elif c == ")" and saved:
                state["dir"], state["stack"] = saved.pop()
    return None


def walk_text(command, state, ctx):
    """Strip heredocs, tokenize and walk command with state; for text that
    cannot be tokenized, the mention rule. Used for the whole command and
    again for each `sh -c` or `eval` string."""
    text = strip_heredocs(command)
    try:
        tokens = tokenize(text)
    except ValueError as exc:
        names = mentions(text)
        if names:
            return (f"history_guard: could not parse this command ({exc}), and it "
                    f"mentions {', '.join(names)}, so it cannot be checked.")
        return None
    return walk_tokens(tokens, state, ctx)


def guard(payload):
    if payload.get("tool_name") != "Bash":
        return None
    command = g.command_of(payload)
    cwd = payload.get("cwd") or "."
    ctx = {"payload": payload, "command": command, "cwd": cwd,
           "protected": g.CONFIG["protected_branches"]}
    problem = walk_text(command, {"dir": cwd, "stack": []}, ctx)
    return ("deny", problem) if problem else None


if __name__ == "__main__":
    sys.exit(g.run(guard))
