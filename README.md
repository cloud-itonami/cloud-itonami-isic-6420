# cloud-itonami-isic-6420

Open Business Blueprint for **ISIC Rev.5 6420**: Activities of
holding companies.

This repository publishes a holding-company actor -- subsidiary
equity-position intake, ownership-structure disclosure assessment,
beneficial-ownership-verification screening, distribution
disbursement and ownership-structure-change recording -- as an OSS
business that any qualified, licensed operator can fork, deploy, run,
improve and sell, so a family office or corporate-services provider
never surrenders customer data and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419),
[`7310`](https://github.com/cloud-itonami/cloud-itonami-isic-7310),
[`7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320),
[`7210`](https://github.com/cloud-itonami/cloud-itonami-isic-7210),
[`7410`](https://github.com/cloud-itonami/cloud-itonami-isic-7410),
[`8710`](https://github.com/cloud-itonami/cloud-itonami-isic-8710),
[`8541`](https://github.com/cloud-itonami/cloud-itonami-isic-8541),
[`8690`](https://github.com/cloud-itonami/cloud-itonami-isic-8690),
[`9601`](https://github.com/cloud-itonami/cloud-itonami-isic-9601)) --
here it is **HoldCo-LLM ⊣ Holding Structure Governor**.

> **Why an actor layer at all?** An LLM is great at drafting an
> ownership-structure disclosure summary, normalizing records, and
> checking whether a proposed distribution amount actually stays
> within a position's own recorded distributable reserves -- but it
> has **no notion of which jurisdiction's corporate-distribution law
> is official, no license to disburse a real dividend/distribution or
> record a real ownership-structure change, and no way to know on its
> own whether a beneficial-ownership status has actually stayed
> verified**. Letting it disburse a distribution or record an
> ownership change directly invites fabricated regulatory citations,
> an over-reserves distribution being disbursed anyway, and an
> unverified beneficial owner being quietly overlooked -- and
> liability, and regulatory risk, for whoever runs it. This project
> seals the HoldCo-LLM into a single node and wraps it with an
> independent **Holding Structure Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers subsidiary equity-position intake through
ownership-structure disclosure assessment, beneficial-ownership-
verification screening, distribution disbursement and ownership-
structure-change recording. It does **not**, by itself, hold any
license required to administer holding-company structures in a given
jurisdiction, and it does not claim to. It also does not model a real
equity-register system, the actual banking-rail transfer of funds, or
tax/legal judgment beyond the position's own recorded fields --
`holdco.registry/distribution-amount-exceeds-distributable-reserves?`
is a pure ground-truth ceiling recompute against the position's own
recorded fields, not a legal opinion. Whoever deploys and operates a
live instance (a licensed corporate-services provider or family
office) supplies any jurisdiction-specific license, the real equity-
register/banking-rail integrations, and bears that jurisdiction's
liability -- the software supplies the governed, spec-cited, audited
execution scaffold so that operator does not have to build the
compliance layer from scratch.

### Actuation

**Disbursing a real dividend/distribution or recording a real
ownership-structure change is never autonomous, at any phase, by
construction.** Two independent layers enforce this (`holdco.
governor`'s `:actuation/disburse-distribution`/`:actuation/record-
ownership-change` high-stakes gate and `holdco.phase`'s phase table,
which never puts `:actuation/disburse-distribution`/`:actuation/
record-ownership-change` in any phase's `:auto` set) -- see `holdco.
phase`'s docstring and `test/holdco/phase_test.clj`'s `disburse-
distribution-never-auto-at-any-phase`/`record-ownership-change-never-
auto-at-any-phase`. The actor may draft, check and recommend; a human
principal is always the one who actually disburses a distribution or
records an ownership change. Like `6512`/`6622`/`6520`/`6530`/`6820`/
`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/
`8510`/`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/
`9491`/`2610`/`3512`/`8810`/`8691`/`8569`/`6419`/`9601`, this actor
has TWO actuation events, both POSITIVE (disbursing/finalizing a real
record), matching the majority pattern in this fleet (`3600`/`6190`
are the fleet's two NEGATIVE-actuation exceptions).

## The core contract

```
position intake + jurisdiction facts (holdco.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ HoldCo-LLM   │ ─────────────▶ │ Holding Structure Governor:    │  (independent system)
   │ (sealed)     │  + citations    │ spec-basis · evidence-        │
   └──────────────┘                 │ incomplete · distribution-      │
          │                 commit ◀┼ exceeds-distributable-reserves    │
          │                         │ (ceiling) · beneficial-ownership-   │
    record + ledger        escalate ┼ verification-unresolved              │
          │              (ALWAYS for│ (unconditional) · already-disbursed/  │
          │               :actuation│ already-recorded                       │
          │               /disburse-└───────────────────────┘
          ▼               distribution /
      human approval      :actuation/record-
                           ownership-change)
```

**The HoldCo-LLM never disburses a distribution or records an
ownership change the Holding Structure Governor would reject, and
never does so without a human sign-off.** Hard violations (fabricated
regulatory requirements; unsupported evidence; a distribution
exceeding the position's own distributable reserves; an unresolved
beneficial-ownership verification; a double disbursement or
recording) force **hold** and *cannot* be approved past; a clean
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a secure document-custody
robot manages physical share-certificate/registry-extract custody
under the actor, gated by the independent **Holding Structure
Governor**. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Holding Structure Governor, distribution-disbursement + ownership-change draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`6420`), which names [`kotoba-lang/securities`](https://github.com/kotoba-lang/securities)
(position, trade, fund-NAV and mandate contracts) as a required
capability. This R0 implementation does NOT add a real `deps.edn`
dependency on it -- matching `banking`/6419's (`:banking`/`:swift`)
and `research`/7210's/`aerospace`/`fab`'s (`:cae`/`:eda`) own
precedent, `holdco.*` implements the specific ground-truth checks a
governor needs (a plain numeric distributable-reserves ceiling and a
boolean beneficial-ownership-verification flag) directly, rather than
pulling in an external capability library for a governed-actor
scaffold this narrow in scope.

## Layout

| File | Role |
|---|---|
| `src/holdco/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate distribution-disbursement/ownership-change history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded position, and the double-actuation guards check dedicated `:distribution-disbursed?`/`:ownership-change-recorded?` booleans rather than a `:status` value |
| `src/holdco/registry.cljc` | Distribution-disbursement + ownership-change draft records, plus `distribution-amount-exceeds-distributable-reserves?` -- a GENUINELY NEW concept (grep-verified absent from every prior sibling), the NINTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care`/`navigator`/`advertising`/`nursing` established the first eight), grounded in real corporate-distribution law |
| `src/holdco/facts.cljc` | Per-jurisdiction holding-company/beneficial-ownership catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/holdco/holdcoadvisor.cljc` | **HoldCo-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/disclosure-verification/beneficial-ownership-screening/distribution-disbursement/ownership-change proposals |
| `src/holdco/governor.cljc` | **Holding Structure Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · distribution-exceeds-distributable-reserves, ground-truth ceiling recompute · beneficial-ownership-verification-unresolved, unconditional evaluation, a GENUINELY NEW concept, the 46th grounding of this discipline) + already-disbursed/already-recorded guards + 1 soft (confidence/actuation gate) |
| `src/holdco/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both distribution disbursement and ownership-change recording always human; position intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/holdco/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/holdco/sim.cljc` | demo driver |
| `test/holdco/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers subsidiary equity-position intake through
ownership-structure disclosure assessment, beneficial-ownership-
verification screening, distribution disbursement and ownership-
structure-change recording -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Position intake + per-jurisdiction ownership-structure-disclosure checklisting, HARD-gated on an official spec-basis citation (`:position/intake`/`:disclosure/verify`) | Real equity-register-system integration, real banking-rail fund transfer itself (see `holdco.facts`'s docstring) |
| Beneficial-ownership-verification screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:beneficial-ownership/screen`) | Any tax/legal judgment itself -- deliberately outside this actor's competence |
| Distribution disbursement, HARD-gated on full evidence and the position's own distributable-reserves ceiling, plus a double-disbursement guard (`:actuation/disburse-distribution`) | |
| Ownership-structure-change recording, HARD-gated on full evidence, plus a double-recording guard (`:actuation/record-ownership-change`) | |
| Immutable audit ledger for every intake/verification/screening/disbursement/recording decision | |

Extending coverage is additive: add the next gate (e.g. a related-
party-transaction disclosure check) as its own governed op with its
own HARD checks and tests, following the SAME "an independent
governor re-verifies against the actor's own records before any
real-world act" pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`holdco.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `holdco.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `holdco.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `HoldCo-LLM` + `Holding Structure Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the sixty-
one prior actors' architecture. See `docs/adr/0001-architecture.md`
for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
