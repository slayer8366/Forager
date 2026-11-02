// The worker/* release guard (dispatch 2026-11-01-coder-11): tools/check-release.sh, run by .github/workflows/
// worker-release.yml on pull requests to main, passes a release whose changes against main are all inside
// server/pmtiles-worker and whose folder equals pre-main's, and fails anything else. Each case is a small git repository
// made here, with main, pre-main and the release branch, so the script is run from its own entry point on real commits.
//
// Run: node --test test/check-release.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const script = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../tools/check-release.sh");
const W = "server/pmtiles-worker";

function repo() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "worker-release-"));
  const git = (...args) => execFileSync("git", ["-C", dir, "-c", "user.name=t", "-c", "user.email=t@t", ...args], { encoding: "utf8" });
  const write = (rel, text) => { fs.mkdirSync(path.dirname(path.join(dir, rel)), { recursive: true }); fs.writeFileSync(path.join(dir, rel), text); };
  git("init", "-q", "-b", "main");
  write(`${W}/src/index.ts`, "old worker\n"); write("app/App.kt", "app\n"); write("map-style/style.json", "{}\n");
  git("add", "-A"); git("commit", "-qm", "main");
  git("checkout", "-qb", "pre-main");
  write(`${W}/src/index.ts`, "new worker\n"); write(`${W}/test/t.mjs`, "test\n"); write("app/App.kt", "app, newer\n");
  git("add", "-A"); git("commit", "-qm", "pre-main");
  git("checkout", "-q", "main"); git("checkout", "-qb", "worker/release");
  return { dir, git, write };
}

const run = (dir) => spawnSync("bash", [script, "main", "worker/release", "pre-main"], { cwd: dir, encoding: "utf8" });

test("a release of exactly pre-main's folder, and nothing else, passes", () => {
  const { dir, git } = repo();
  git("checkout", "pre-main", "--", W); git("commit", "-qm", "release");
  const r = run(dir);
  assert.equal(r.status, 0, r.stdout + r.stderr);
});

test("a release that changes a file outside the folder fails, naming the file", () => {
  const { dir, git, write } = repo();
  git("checkout", "pre-main", "--", W); write("app/App.kt", "app, sneaked in\n"); git("add", "-A"); git("commit", "-qm", "release");
  const r = run(dir);
  assert.notEqual(r.status, 0);
  assert.match(r.stdout + r.stderr, /app\/App\.kt/);
});

test("a release whose folder differs from pre-main's fails, naming the file", () => {
  const { dir, git, write } = repo();
  git("checkout", "pre-main", "--", W); write(`${W}/src/index.ts`, "edited on the release branch\n"); git("add", "-A"); git("commit", "-qm", "release");
  const r = run(dir);
  assert.notEqual(r.status, 0);
  assert.match(r.stdout + r.stderr, /src\/index\.ts/);
});

test("a release missing a file pre-main's folder has fails", () => {
  const { dir, git } = repo();
  git("checkout", "pre-main", "--", `${W}/src/index.ts`); git("commit", "-qm", "release without the test file");
  const r = run(dir);
  assert.notEqual(r.status, 0);
  assert.match(r.stdout + r.stderr, /test\/t\.mjs/);
});
