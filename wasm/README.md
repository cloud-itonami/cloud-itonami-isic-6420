# wasm/ — kotoba-wasm deployment of the distribution-exceeds-distributable-reserves check

`distributable_reserves.kotoba` is a port of `holdco.registry/
distribution-amount-exceeds-distributable-reserves?` (wrapped for the
`:actuation/disburse-distribution` op by `holdco.governor/distribution-
exceeds-distributable-reserves-violations`)'s pure ground-truth
comparison — does a subsidiary equity position's own recorded proposed
distribution amount stay within its own recorded distributable-reserves
ceiling? (see `src/holdco/registry.cljc` lines ~52-60 and `src/holdco/
governor.cljc` lines ~174-187) — into the minimal `.kotoba` language
subset, compiled to a real WASM module via `kotoba wasm emit`, and hosted
via `kototama.tender` (`test/wasm/distributable_reserves_test.clj`).

This follows the same `kotoba wasm emit` → `kototama.tender` pattern
already proven by `cloud-itonami-isic-6492`'s `wasm/affordability.kotoba`,
`cloud-itonami-isic-6511`'s `wasm/underwriting_decision.kotoba`,
`cloud-itonami-isic-6611`'s and `cloud-itonami-isic-6630`'s siblings, and
most directly `cloud-itonami-isic-6512`'s `wasm/claim_coverage.kotoba`
(ADR-2607062330 addendum 5) — a sixth sibling actor's hot-path decision
function ported to real WASM, and structurally the closest analog to
`claim_coverage.kotoba`: a single direct integer comparison, no formula,
no zero-guard branch.

## Why the source differs from `holdco.registry`/`holdco.governor`

The `.kotoba` compiler's actual WASM code-generator supports only a small,
empirically-verified subset: the special forms `do`/`let`/`if` plus
`+ - * quot / rem mod = < > <= >= zero? not inc dec` (confirmed by reading
`compile-wasm-expr` in `kotoba-lang/kotoba/src/kotoba/runtime.clj` — no
`pos?`/`neg?`/`and`/`or`/`when`, unlike the broader tree-walking
interpreter). The port therefore:

- Ports ONLY the pure ground-truth arithmetic core — the direct comparison
  of `proposed-distribution-amount` against `distributable-reserves` —
  never `holdco.store/position`'s lookup or the `:actuation/disburse-
  distribution` op-dispatch/evidence-checklist/beneficial-ownership
  checks in `holdco.governor`, none of which get ported (no maps, no
  protocols, no op-keyword dispatch in the wasm-compilable subset).
- Uses plain positional integer args instead of `{:keys [...]}` map
  destructuring (no maps in the wasm-compilable subset).
- Drops `holdco.registry`'s `(and (number? ...) (number? ...) ...)`
  guard — the wasm ABI's two memory slots are always populated i32
  values by construction (the host writes both before calling `main()`),
  so the `number?` guard (protecting against a Clojure map missing a
  key) has no wasm analog; a missing/absent field is a host-side
  responsibility, not this guest's.
- Compares `proposed-distribution-amount <= distributable-reserves`
  directly as plain integers (smallest currency unit / cents) instead of
  `holdco.registry`'s `(> proposed-distribution-amount distributable-
  reserves)` — no floats needed for a direct integer comparison,
  consistent with `cloud-itonami-isic-6492`/`kotoba-card`/`kotoba-
  banking`'s own convention of representing amounts as plain integers.
- Inverts the polarity relative to `holdco.registry`'s violation check:
  `distribution-amount-exceeds-distributable-reserves?` returns truthy
  (a violation) when the distribution EXCEEDS reserves, whereas this
  module's `distribution-within-reserves?` (and `main`) returns `1` when
  the distribution is WITHIN reserves (i.e. NOT a violation) and `0` when
  it exceeds reserves — the more natural "is this OK" polarity for a
  boolean-shaped WASM export, the same polarity convention as
  `affordability.kotoba`'s `affordable?` and `claim_coverage.kotoba`'s
  `claim-within-coverage?`.

This is the simplest kind of port in this fleet's wasm family: one direct
comparison, no multi-term formula, no zero-guard branch — the same shape
as `claim_coverage.kotoba`.

## ABI — parameterized invocation

`kotoba wasm emit` rejects any `main` with parameters (`:main-arity` — the
compiler only ever exports a 0-arity `main`, see `compile-wasm-expr` in
`kotoba-lang/kotoba/src/kotoba/runtime.clj`), so real inputs are passed
through the guest's exported linear memory instead — the same convention
`cloud-itonami-isic-6492`'s `affordability.kotoba`,
`cloud-itonami-isic-6511`'s `underwriting_decision.kotoba`, and
`cloud-itonami-isic-6512`'s `claim_coverage.kotoba` use. A host writes two
little-endian i32 values (cents) before calling `main()`:

| offset | field                          |
|--------|--------------------------------|
| 0      | `proposed-distribution-amount` |
| 4      | `distributable-reserves`       |

`main()` returns `1` (distribution within reserves — no violation on this
check) or `0` (distribution exceeds reserves — a HARD `:distribution-
exceeds-distributable-reserves` violation per `holdco.governor`). Both
offsets are well below `heap-base` (2048), so they never collide with
anything the compiler itself places in memory.

## Rebuilding

```sh
cd ../../kotoba-lang/kotoba   # sibling checkout, west-managed
bin/kotoba-clj wasm emit ../../cloud-itonami/cloud-itonami-isic-6420/wasm/distributable_reserves.kotoba \
  --package-lock kotoba.lock.edn \
  --output ../../cloud-itonami/cloud-itonami-isic-6420/wasm/distributable_reserves.wasm --json
```

Fleet deployment: not attempted in this pass — see cloud-itonami-isic-6492/6511 for the established pattern.
