## Summary

<!-- What changed and why. -->

## Test plan

<!-- How this was verified: unit tests run, manual checks, hardware if applicable. -->

---

## Motion spec compliance

**Only required if this PR touches `app/src/main/java/com/forager/app/ui/motion/**` or
`docs/motion-spec.md`.** Otherwise, delete this section.

A failed item blocks the release. `CODEOWNERS` requires review from the named motion owner
role on those paths.

- [ ] Precedence order respected (legibility > performance > calm) — see `docs/motion-spec.md` §1
- [ ] No confidence score, candidate list, species suggestion, or per-observation harm
      assessment introduced anywhere in this change — **not waivable**, see
      `docs/motion-spec.md` "Scope boundary"
- [ ] All durations/easings come from `MotionTokens.kt`; no magic numbers at call sites
- [ ] Performance budget holds under a dense-map fixture; clustering engages before the
      animated-object cap
- [ ] Degradation order implemented and tested
- [ ] Reduce Motion mappings implemented and tested; state stays communicated (never a bare
      kill switch)
- [ ] Color is never the sole carrier of state
- [ ] Latency states (GPS acquiring, offline tiles) have explicit, non-misleading treatments
- [ ] Validated against the full Field Test Conditions list (`docs/motion-spec.md` §5)

### Waivers

Only the motion owner role named in `CODEOWNERS` may waive a checklist item, and only with a
written reason recorded below plus an expiry date. An expired waiver auto-fails the item it
covered. The scope-boundary item above is **not waivable**, by anyone, ever.

| Item waived | Reason | Expires | Waived by |
|---|---|---|---|
| | | | |
