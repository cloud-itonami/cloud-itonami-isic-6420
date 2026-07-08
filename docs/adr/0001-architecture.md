# ADR-0001: HoldCo-LLM ⊣ Holding Structure Governor architecture

## Status

Accepted. `cloud-itonami-isic-6420` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-6420` publishes an OSS business blueprint for
holding-company administration: owning and managing controlling
equity interests in subsidiary companies without engaging in their
operations. Like every prior actor in this fleet, the blueprint alone
is not an implementation: this ADR records the governed-actor
architecture that promotes it to real, tested code, following the
same langgraph-clj StateGraph + independent Governor + Phase 0→3
rollout pattern established by `cloud-itonami-isic-6511` (life
insurance) and applied across sixty-one prior siblings, most recently
`cloud-itonami-isic-9601` (washing and dry-cleaning).

## Decision

### Decision 1: entity and op shape

The primary entity is a `position` (a subsidiary equity position,
matching the blueprint's own Offer language -- "subsidiary equity-
position intake"). Five ops: `:position/intake` (directory upsert, no
capital risk), `:disclosure/verify` (per-jurisdiction ownership-
structure disclosure evidence checklist, never auto), `:beneficial-
ownership/screen` (beneficial-ownership-verification screening,
unconditional-evaluation discipline, never auto), `:actuation/
disburse-distribution` (POSITIVE, high-stakes -- disbursing a real
dividend/distribution), and `:actuation/record-ownership-change`
(POSITIVE, high-stakes -- recording a real ownership-structure
change).

### Decision 2: dual-actuation shape on one entity

This blueprint's own text names TWO real-world acts ("disbursing a
dividend/distribution or recording an ownership-structure change"),
both acting on the SAME entity (the position). Matching `6512`/
`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/
`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/
`6190`/`3030`/`3830`/`9420`/`9491`/`2610`/`3512`/`8810`/`8691`/`8569`/
`6419`/`9601`'s dual-actuation-on-one-entity shape, `high-stakes` is
the two-member set `#{:actuation/disburse-distribution :actuation/
record-ownership-change}`, each with its own history collection,
sequence counter, and dedicated double-actuation-guard boolean
(`:distribution-disbursed?`/`:ownership-change-recorded?`, never a
single `:status` value).

### Decision 3: `distribution-amount-exceeds-distributable-reserves?` -- a genuinely new check, the 9th MAXIMUM-ceiling instance

Before writing this check, every prior sibling's governor/registry
namespaces were grepped for "distributable-reserves" and
"distributable reserves" -- zero hits, confirming this is a genuinely
new concept, avoiding the false-precedent-claim risk `leasing`'s
ADR-0001 documents. Following `facility.registry/occupancy-exceeds-
capacity?` (1st), `school.registry/class-size-exceeds-maximum?` (2nd),
`card.registry/settlement-amount-exceeds-authorized?` (3rd),
`recovery.registry/contamination-percentage-exceeds-maximum?` (4th),
`care.registry/caregiver-workload-exceeds-maximum?` (5th),
`navigator.registry/eligibility-window-elapsed-exceeds-validity?`
(6th), `advertising.registry/media-spend-exceeds-authorized?` (7th)
and `nursing.registry/medication-dosage-exceeds-maximum?` (8th),
`holdco.registry/distribution-amount-exceeds-distributable-reserves?`
applies the same lo-bound-absent/hi-bound-only comparison to a
position's own recorded proposed distribution amount against its own
recorded distributable-reserves ceiling -- a direct, natural mapping
onto real corporate-distribution law (Delaware GCL §170's surplus/
net-profits test, UK Companies Act 2006 Part 23's distributable-
profits test, Japan's Companies Act §461 distributable-amount rules,
Germany's AktG §57-58 Kapitalerhaltung). The NINTH instance overall.
Gates only `:actuation/disburse-distribution`.

### Decision 4: `beneficial-ownership-verification-unresolved-violations` -- a genuinely new check, the 46th unconditional-evaluation grounding

Before writing this check, every prior sibling's governor/registry
namespaces were grepped for "beneficial-owner" and "beneficial
owner" -- zero hits, confirming this is a genuinely new concept.
`beneficial-ownership-verification-unresolved-violations` reuses the
unconditional-evaluation DISCIPLINE (`casualty.governor/sanctions-
violations`'s original fix) for the 46th distinct application overall
(continuing the count established across this fleet's builds, most
recently `laundry.governor/certification-not-current-violations` at
45th). Grounded directly in real beneficial-ownership-transparency
law (US Corporate Transparency Act / FinCEN beneficial-ownership
reporting, UK's Persons with Significant Control register, Germany's
Transparenzregister). Explicitly distinct from `sanctions-violations`
(counterparty sanctions-list screening) -- this concerns confirming
WHO ultimately owns/controls the entity, not whether a counterparty
appears on a sanctions list. Gates `:beneficial-ownership/screen` and
both actuation ops.

### Decision 5: dedicated double-actuation-guard booleans

`:distribution-disbursed?`/`:ownership-change-recorded?` are dedicated
booleans on the `position` record, never a single `:status` value --
the same discipline every prior sibling governor's guards establish,
informed by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 6: Store protocol, MemStore + DatomicStore parity

`holdco.store/Store` is implemented by both `MemStore` (atom-backed,
default for dev/tests/demo) and `DatomicStore` (`langchain.db`-
backed), proven to satisfy the same contract in `test/holdco/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
The protocol's per-entity accessor is named `position` directly --
not a Clojure special form, so no `-of` suffix workaround was needed.

### Decision 7: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:position/intake` (no
capital risk). `:disclosure/verify` and `:beneficial-ownership/screen`
are never auto-eligible at any phase (matching every sibling's
screening-op posture), and both `:actuation/disburse-distribution`/
`:actuation/record-ownership-change` are permanently excluded from
every phase's `:auto` set -- a structural fact, not a rollout
milestone, enforced by BOTH `holdco.phase` and `holdco.governor`'s
`high-stakes` set independently.

### Decision 8: no bespoke domain capability lib as a code dependency (despite blueprint.edn requiring `:securities`)

This blueprint's own `:itonami.blueprint/required-technologies`
uniquely names `:securities` (pointing at `kotoba-lang/securities`'s
position/trade/fund-NAV/mandate contracts) beyond the generic stack.
This R0 implementation does NOT add `:securities` as a real
`deps.edn` dependency, following the same posture `banking` (`:banking`/
`:swift`) and `research`/`aerospace`/`fab` (`:cae`/`:eda`) already
established: implement the specific ground-truth check a governor
needs directly (here, a plain numeric distributable-reserves ceiling
and a boolean beneficial-ownership-verification flag in `holdco.
registry`) rather than pull in an external capability library for a
governed-actor scaffold this narrow in scope.

### Decision 9: mock + LLM advisor pair

`holdco.holdcoadvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
disbursing a distribution or auto-recording an ownership change).

### Decision 10: no `blueprint.edn` field-sync fixes needed

Matching `advertising`/7310's, `polling`/7320's, `research`/7210's,
`design`/7410's, `nursing`/8710's, `sports`/8541's, `alliedhealth`/
8690's and `laundry`/9601's own experience, this repo's `blueprint.
edn` already had the correct `isic-` prefixed `:id` and correctly
populated `:required-technologies`/`:optional-technologies`
(including `:securities`) matching the `kotoba-lang/industry`
registry's own entry for `"6420"` exactly -- only the `:maturity`
field itself needed adding.

## Alternatives considered

- **A single-actuation shape.** Rejected: the blueprint's own text
  names BOTH acts explicitly ("disbursing a dividend/distribution or
  recording an ownership-structure change") -- omitting either would
  understate the blueprint's own scope.
- **Reusing `sanctions-violations` for the beneficial-ownership
  concept.** Rejected: sanctions screening is about whether a
  counterparty appears on a sanctions list; beneficial-ownership
  verification is about confirming who ultimately owns/controls the
  entity -- a genuinely different regulatory concern, confirmed via
  grep to have zero prior instances under either name.
- **Adding `:securities` as a real backing library dependency.**
  Rejected for this R0: matching `banking`/`research`/`aerospace`/
  `fab`'s own precedent, this governed-actor scaffold is narrow
  enough in scope that plain numeric ground-truth checks suffice; a
  production operator wiring this to a real securities/equity-
  register system would add the library at that integration layer.

## Consequences

- Sixty-second actor in this fleet (61 implemented before this
  build).
- Confirms the MAXIMUM-ceiling check family generalizes to a 9th
  instance, genuinely distinct domain (corporate distribution law).
- Establishes a genuinely NEW unconditional-evaluation-screening
  concept (beneficial-ownership-verification-unresolved), grep-
  verified absent from every prior sibling before the claim was
  finalized.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/holdco/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- `blueprint.edn` required no field-sync fixes this time (already
  correct) -- only the `:maturity` flip itself.
- **Fleet-wide finding (not fixed in this build):** this repo's
  `deps.edn` initially referenced the historical `io.github.com-
  junkawasaki/langgraph-clj`/`langchain-clj` coordinates and
  `:local/root` paths; these projects have since been transferred and
  renamed to `io.github.kotoba-lang/langgraph`/`langchain` (checked
  out at `orgs/kotoba-lang/langgraph`/`langchain`, internal
  namespaces unchanged). This repo's own `deps.edn` was corrected to
  the current coordinates/paths as part of this build, but the SAME
  stale reference likely still exists in every prior `cloud-itonami-
  isic-*` sibling's `deps.edn` (all built before this transfer) --
  worth a dedicated fleet-wide follow-up, out of scope for this
  single-vertical build.

## References

- `orgs/cloud-itonami/cloud-itonami-isic-6420/README.md`
- `orgs/cloud-itonami/cloud-itonami-isic-6420/docs/business-model.md`
- `orgs/kotoba-lang/industry/resources/kotoba/industry/registry.edn` (entry `"6420"`)
