// Checks forager-light.json against what is on disk (dispatch 2026-09-28-485): the layer count, every
// font stack and glyph range the style can ask for, every icon name it names in the sprite, and the
// licence files. Run: npm run check. Exits non-zero, saying what is missing, if anything is.
import fs from "node:fs";

const here = (path) => new URL(`../${path}`, import.meta.url);
// The style to check: forager-light.json by default, or the file named on the command line
// (npm run check:street checks forager-street-v2.preview.json, whose fonts and icons are on disk).
const stylePath = process.argv[2] ?? "forager-light.json";
const style = JSON.parse(fs.readFileSync(here(stylePath), "utf8"));
const problems = [];

// Font stacks: the names inside literal stack arrays (["Noto Sans Regular"]), including those an
// expression chooses between (["literal", [...]]); operator names and property names are not fonts.
const OPERATORS = new Set(["case", "match", "step", "coalesce", "get", "<=", ">=", "<", ">", "==", "!=", "all", "any", "in", "zoom", "+", "-"]);
const stacksIn = (value, out) => {
  if (!Array.isArray(value)) return out;
  if (value[0] === "literal") return stacksIn(value[1], out);
  if (value.length && value.every((v) => typeof v === "string") && !OPERATORS.has(value[0])) {
    value.forEach((name) => out.add(name));
    return out;
  }
  value.forEach((v) => stacksIn(v, out));
  return out;
};

// Icon names: each icon-image expression evaluated for the inputs it can receive, with a small
// evaluator for the operators the style uses (get, match, case, ==, step, zoom, concat, length).
const evaluate = (e, props, zoom) => {
  if (!Array.isArray(e)) return e;
  const [op, ...a] = e;
  switch (op) {
    case "get": return props[a[0]];
    case "literal": return a[0];
    case "zoom": return zoom;
    case "concat": return a.map((x) => evaluate(x, props, zoom)).join("");
    case "length": return String(evaluate(a[0], props, zoom) ?? "").length;
    case "==": return evaluate(a[0], props, zoom) === evaluate(a[1], props, zoom);
    case "case": {
      for (let i = 0; i + 1 < a.length; i += 2) if (evaluate(a[i], props, zoom)) return evaluate(a[i + 1], props, zoom);
      return evaluate(a[a.length - 1], props, zoom);
    }
    case "match": {
      const input = evaluate(a[0], props, zoom);
      for (let i = 1; i + 1 < a.length; i += 2) {
        const labels = Array.isArray(a[i]) ? a[i] : [a[i]];
        if (labels.includes(input)) return evaluate(a[i + 1], props, zoom);
      }
      return evaluate(a[a.length - 1], props, zoom);
    }
    case "step": {
      const input = evaluate(a[0], props, zoom);
      let out = evaluate(a[1], props, zoom);
      for (let i = 2; i + 1 < a.length; i += 2) if (input >= a[i]) out = evaluate(a[i + 1], props, zoom);
      return out;
    }
    default: throw new Error(`check-style: no evaluator for "${op}"`);
  }
};
const kindsInFilter = (filter) => {
  const out = [];
  const walk = (v) => {
    if (!Array.isArray(v)) return;
    if (v[0] === "in" && JSON.stringify(v[1]) === '["get","kind"]' && v[2]?.[0] === "literal") out.push(...v[2][1]);
    v.forEach(walk);
  };
  walk(filter);
  return out;
};

const fontStacks = new Set();
const needed = new Map(); // icon name -> what asks for it
for (const layer of style.layers) {
  const font = layer.layout?.["text-font"];
  if (font) stacksIn(font, fontStacks);
  const icon = layer.layout?.["icon-image"];
  if (icon === undefined) continue;
  const inputs = [];
  for (const kind of kindsInFilter(layer.filter)) inputs.push({ props: { kind }, why: `kind ${kind}` });
  for (const network of ["US:I", "NL:S-road", "other"]) {
    for (let n = 1; n <= 5; n++) inputs.push({ props: { network, shield_text: "x".repeat(n) }, why: `shield ${network} ${n} characters` });
  }
  for (const capital of ["yes", "no"]) inputs.push({ props: { capital }, why: `capital=${capital}` });
  for (const { props, why } of inputs.length ? inputs : [{ props: {}, why: "constant" }]) {
    for (const zoom of [0, 10, 16]) {
      const name = evaluate(icon, props, zoom);
      if (typeof name === "string" && name !== "" && !name.startsWith("undefined") && !needed.has(name)) needed.set(name, `${layer.id}: ${why}`);
    }
  }
}

// Glyphs: MapLibre asks for 256-codepoint ranges; all 256 must be present for each stack.
for (const stack of fontStacks) {
  const dir = here(`assets/fonts/${stack}/`);
  let missing = 0;
  for (let start = 0; start < 65536; start += 256) {
    if (!fs.existsSync(new URL(`${start}-${start + 255}.pbf`, dir))) missing++;
  }
  if (missing) problems.push(`font "${stack}": ${missing} of 256 glyph ranges missing`);
}

// Sprites: both densities, and every literal icon name the style uses present in the sprite index.
const spriteBase = style.sprite.replace(/^https?:\/\/[^/]+\//, "");
for (const suffix of ["", "@2x"]) {
  for (const ext of ["json", "png"]) {
    if (!fs.existsSync(here(`${spriteBase}${suffix}.${ext}`))) problems.push(`sprite file ${spriteBase}${suffix}.${ext} missing`);
  }
}
const sprite = JSON.parse(fs.readFileSync(here(`${spriteBase}.json`), "utf8"));
// Every name the evaluator produced that the sprite lacks. Some inputs may never occur in the tiles
// (a road with no shield text gives "generic_shield-0char"): these are reported, not counted as problems.
const absentIcons = [...needed].filter(([name]) => !(name in sprite));

for (const f of ["fonts-Noto-OFL.txt", "sprites-tangrams-icons-MIT.md", "protomaps-basemaps-LICENSE.md", "maplibre-gl-js-LICENSE.txt"]) {
  if (!fs.existsSync(here(`licences/${f}`))) problems.push(`licence file licences/${f} missing`);
}

console.log(`layers: ${style.layers.length} (by type: ${JSON.stringify(style.layers.reduce((a, l) => ((a[l.type] = (a[l.type] ?? 0) + 1), a), {}))})`);
console.log(`font stacks: ${[...fontStacks].join(", ")}; each checked for all 256 glyph ranges`);
console.log(`icon names the style can ask for: ${needed.size}; present in the sprite (${Object.keys(sprite).length} icons): ${needed.size - absentIcons.length}`);
for (const [name, why] of absentIcons) console.log(`  not in the sprite: "${name}" (${why}); MapLibre draws the label without an icon`);
if (problems.length) {
  console.log("PROBLEMS:\n  " + problems.join("\n  "));
  process.exit(1);
}
console.log("all fonts, sprites and licence files present");
