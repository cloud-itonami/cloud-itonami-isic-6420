# Business Model: Activities of holding companies

## Classification

- Repository: `cloud-itonami-isic-6420`
- ISIC Rev.5: `6420`
- Activity: holding-company administration -- owning and managing controlling equity interests in subsidiary companies without engaging in their operations
- Social impact: financial inclusion, data sovereignty, transparent audit

## Customer

- family offices
- cooperative holding structures
- community-owned holding vehicles
- corporate-services providers administering client holding companies

## Offer

- subsidiary equity-position intake
- ownership-structure disclosure proposal
- dividend/distribution proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per holding entity
- support: monthly retainer with SLA
- migration: import from an incumbent equity-register system
- distribution-processing fee

## Trust Controls

- no distribution is disbursed and no ownership-structure change is recorded without human sign-off
- fabricated ownership evidence forces a hold, not an override
- every distribution path is auditable
- emergency manual override paths remain outside LLM control
- a distribution amount exceeding a position's own recorded
  distributable reserves, or an unresolved beneficial-ownership
  verification, forces a hold, not an override
- distribution disbursement and ownership-change recording are each
  logged and escalated, and cannot be finalized twice for the same
  position: a double-disbursement or double-recording attempt is held
  off this actor's own position facts alone, with no upstream
  comparison needed

## Holding Structure Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:holding-
structure-governor` -- this is not a generic "review step," it is
the gate the two real-world acts this business performs (disbursing a
distribution, recording an ownership-structure change) must pass. The
governor sits between the HoldCo-LLM and execution, per the README's
Core Contract:

```text
HoldCo-LLM -> Holding Structure Governor -> hold, proceed, or human approval
```

**Approves**: routine holding-structure actions proposed against a
position that already has a consented ownership disclosure on file,
a proposed distribution within its own recorded distributable
reserves, and a verified beneficial-ownership status. These proceed
straight to the holding-company ledger.

**Rejects or escalates**: the governor refuses to let the advisor
disburse a distribution or record an ownership change on its own
authority when any of the following hold -- a fabricated jurisdiction
spec-basis; incomplete evidence; a distribution exceeding the
position's own recorded distributable reserves; an unresolved
beneficial-ownership verification. A clean proposal still always
routes to a human -- `:actuation/disburse-distribution`/`:actuation/
record-ownership-change` are never auto-committed, at any rollout
phase.
