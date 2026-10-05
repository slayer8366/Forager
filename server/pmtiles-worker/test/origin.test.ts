// Unit tests for src/origin.ts: when a tile comes from the Pi and when it falls back to R2.
// Run with `npm test` (Node's built-in runner with type stripping; Node 22.6 or later, no packages).
import { test } from "node:test";
import assert from "node:assert/strict";
import { originConfig, originTile, originTileJson, OriginState, type OriginConfig } from "../src/origin.ts";

const ENV = {
  ORIGIN_URL: "https://origin.example/",
  ORIGIN_ARCHIVES: "forager-orwa, other",
  CF_ACCESS_CLIENT_ID: "id-value",
  CF_ACCESS_CLIENT_SECRET: "secret-value",
};

function cfg(over: Partial<OriginConfig> = {}): OriginConfig {
  const c = originConfig(ENV);
  if ("unconfigured" in c) throw new Error(c.unconfigured);
  return { ...c, timeoutMs: 50, downForMs: 60_000, ...over };
}

type Call = { url: string; headers: Record<string, string> };

function fetchReturning(make: () => Response | Promise<Response>, calls: Call[] = []) {
  return async (url: string, init: { headers: Record<string, string>; signal: AbortSignal }) => {
    calls.push({ url, headers: init.headers });
    return make();
  };
}

// Arbitrary numbers, not a real place: positions never enter the repository.
const TILE: [number, number, number] = [3, 1, 2];

test("config: every required setting missing is named, none guessed", () => {
  const c = originConfig({ ORIGIN_URL: "https://o", ORIGIN_ARCHIVES: " " });
  assert.deepEqual(c, { unconfigured: "missing ORIGIN_ARCHIVES, CF_ACCESS_CLIENT_ID, CF_ACCESS_CLIENT_SECRET" });
});

test("config: trims the URL's trailing slash, splits archives, defaults the timings", () => {
  const c = originConfig(ENV);
  assert.ok(!("unconfigured" in c));
  assert.equal(c.baseUrl, "https://origin.example");
  assert.deepEqual([...c.archives], ["forager-orwa", "other"]);
  assert.equal(c.timeoutMs, 3000);
  assert.equal(c.downForMs, 60_000);
  const bad = originConfig({ ...ENV, ORIGIN_TIMEOUT_MS: "-5", ORIGIN_DOWN_FOR_MS: "abc" });
  assert.ok(!("unconfigured" in bad));
  assert.equal(bad.timeoutMs, 3000);
  assert.equal(bad.downForMs, 60_000);
});

test("a 200 from the Pi is served from the Pi, with the Access headers sent", async () => {
  const calls: Call[] = [];
  const body = new Uint8Array([1, 2, 3]);
  const r = await originTile(
    cfg(), new OriginState(), "forager-orwa", TILE, "mvt",
    fetchReturning(() => new Response(body, { status: 200, headers: { "Content-Type": "application/x-protobuf" } }), calls),
    () => 1000
  );
  assert.equal(r.source, "pi");
  assert.ok(r.source === "pi");
  assert.equal(r.status, 200);
  assert.deepEqual(new Uint8Array(r.body!), body);
  assert.equal(r.contentType, "application/x-protobuf");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://origin.example/forager-orwa/3/1/2.mvt");
  assert.deepEqual(calls[0].headers, { "CF-Access-Client-Id": "id-value", "CF-Access-Client-Secret": "secret-value" });
});

test("a 204 from the Pi is an empty tile, served from the Pi", async () => {
  const r = await originTile(cfg(), new OriginState(), "forager-orwa", TILE, "mvt",
    fetchReturning(() => new Response(null, { status: 204 })), () => 0);
  assert.deepEqual(r, { source: "pi", status: 204, body: null, contentType: null });
});

test("a 404 goes to R2 for that tile only and does not mark the Pi down", async () => {
  const state = new OriginState();
  const r = await originTile(cfg(), state, "forager-orwa", TILE, "mvt",
    fetchReturning(() => new Response("Archive not found", { status: 404 })), () => 0);
  assert.deepEqual(r, { source: "r2", reason: "pi 404 (Archive not found)", markedDown: false });
  assert.equal(state.downUntil, 0);
});

for (const [status, label] of [[530, "tunnel down"], [502, "bad gateway"], [403, "Access refusal"], [500, "server error"]] as const) {
  test(`a ${status} (${label}) falls back and marks the Pi down`, async () => {
    const state = new OriginState();
    const r = await originTile(cfg(), state, "forager-orwa", TILE, "mvt",
      fetchReturning(() => new Response("x", { status })), () => 5000);
    assert.deepEqual(r, { source: "r2", reason: `pi answered ${status}`, markedDown: true });
    assert.equal(state.downUntil, 65_000);
  });
}

test("a network error falls back and marks the Pi down", async () => {
  const state = new OriginState();
  const r = await originTile(cfg(), state, "forager-orwa", TILE, "mvt",
    async () => { throw new TypeError("connection refused"); }, () => 0);
  assert.deepEqual(r, { source: "r2", reason: "fetch failed: connection refused", markedDown: true });
  assert.equal(state.downReason, "fetch failed: connection refused");
});

test("no answer within the timeout falls back and marks the Pi down", async () => {
  const state = new OriginState();
  // A Pi that never answers. Node's AbortSignal.timeout timer does not keep the process alive on
  // its own, so a referenced keep-alive timer holds the event loop open until the abort fires.
  const hang = (_u: string, init: { signal: AbortSignal }) =>
    new Promise<Response>((_res, rej) => {
      const keepAlive = setTimeout(() => rej(new Error("the timeout never fired")), 5000);
      init.signal.addEventListener("abort", () => {
        clearTimeout(keepAlive);
        rej(init.signal.reason);
      });
    });
  const started = Date.now();
  const r = await originTile(cfg({ timeoutMs: 30 }), state, "forager-orwa", TILE, "mvt", hang, () => 0);
  assert.deepEqual(r, { source: "r2", reason: "no answer within 30 ms", markedDown: true });
  assert.ok(Date.now() - started < 2000, "the timeout, not the test runner, ended the wait");
});

test("while marked down, the Pi is not asked at all; after the window it is asked again", async () => {
  const state = new OriginState();
  state.downUntil = 10_000;
  state.downReason = "pi answered 530";
  const calls: Call[] = [];
  const ok = fetchReturning(() => new Response(new Uint8Array([9]), { status: 200 }), calls);

  const during = await originTile(cfg(), state, "forager-orwa", TILE, "mvt", ok, () => 9_999);
  assert.deepEqual(during, { source: "r2", reason: "pi marked down: pi answered 530", markedDown: false });
  assert.equal(calls.length, 0);

  const after = await originTile(cfg(), state, "forager-orwa", TILE, "mvt", ok, () => 10_000);
  assert.equal(after.source, "pi");
  assert.equal(calls.length, 1);
});

const PI_JSON = {
  tilejson: "3.0.0",
  name: "Protomaps Basemap",
  minzoom: 0,
  maxzoom: 15,
  tiles: ["https://tiles.example/forager-orwa/{z}/{x}/{y}.mvt"],
  vector_layers: [{ id: "roads" }],
};

test("tileset JSON from the Pi has its tiles URL rewritten to this Worker, all else kept", async () => {
  const calls: Call[] = [];
  const r = await originTileJson(cfg(), new OriginState(), "forager-orwa", "https://worker.example/forager-orwa",
    fetchReturning(() => Response.json(PI_JSON), calls), () => 0);
  assert.equal(r.source, "pi");
  assert.ok(r.source === "pi");
  assert.deepEqual(r.json, { ...PI_JSON, tiles: ["https://worker.example/forager-orwa/{z}/{x}/{y}.mvt"] });
  assert.equal(calls[0].url, "https://origin.example/forager-orwa.json");
  assert.deepEqual(calls[0].headers, { "CF-Access-Client-Id": "id-value", "CF-Access-Client-Secret": "secret-value" });
});

test("tileset JSON: a 404 falls back without marking down; a 530 marks the Pi down", async () => {
  const s1 = new OriginState();
  const r404 = await originTileJson(cfg(), s1, "forager-orwa", "https://w/x",
    fetchReturning(() => new Response("Archive not found", { status: 404 })), () => 0);
  assert.deepEqual(r404, { source: "r2", reason: "pi 404 (Archive not found)", markedDown: false });
  assert.equal(s1.downUntil, 0);

  const s2 = new OriginState();
  const r530 = await originTileJson(cfg(), s2, "forager-orwa", "https://w/x",
    fetchReturning(() => new Response("x", { status: 530 })), () => 100);
  assert.deepEqual(r530, { source: "r2", reason: "pi answered 530", markedDown: true });
  assert.equal(s2.downUntil, 60_100);
});

test("tileset JSON that is not usable is a fallback, never served", async () => {
  for (const [body, reason] of [
    ["not json", "tileset JSON unreadable"],
    ["[1]", "tileset JSON is not an object"],
    [JSON.stringify({ ...PI_JSON, tiles: [] }), "tileset JSON has no usable tiles URL"],
  ] as const) {
    const r = await originTileJson(cfg(), new OriginState(), "forager-orwa", "https://w/x",
      fetchReturning(() => new Response(body, { status: 200 })), () => 0);
    assert.equal(r.source, "r2");
    assert.ok(r.source === "r2" && r.reason.startsWith(reason), `${body}: ${r.source === "r2" ? r.reason : ""}`);
  }
});

test("tileset JSON: while the Pi is marked down it is not asked", async () => {
  const state = new OriginState();
  state.downUntil = 50;
  state.downReason = "no answer within 3000 ms";
  const calls: Call[] = [];
  const r = await originTileJson(cfg(), state, "forager-orwa", "https://w/x",
    fetchReturning(() => Response.json(PI_JSON), calls), () => 10);
  assert.deepEqual(r, { source: "r2", reason: "pi marked down: no answer within 3000 ms", markedDown: false });
  assert.equal(calls.length, 0);
});
