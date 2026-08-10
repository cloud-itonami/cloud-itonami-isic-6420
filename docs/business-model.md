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

| Package | Customer | Price shape |
|---|---|---|
| Managed Starter | one holding structure (family office / cooperative or community-owned holding vehicle) with 5-15 subsidiary positions | ¥35,000/月 flat |

**Market-anchored (2026-08-10)**: benchmarked against 5 real competitor
products, converted at ~¥150/$ for the assumed customer above. **4 of the 5
publish real numbers on their own site** — unusually open for financial-sector
B2B SaaS, and the opposite of what the insurance verticals in this fleet show.

- **EntityKeeper** (entity management, published): Corporate "$465 /mo" for
  up to 100 entities or "$5,000 /yr"; Enterprise "$925 /mo" for up to 200
  entities or "$10,000 /yr"; Platinum custom; implementation fee at one-sixth
  of the annual subscription — <https://www.entitykeeper.com/pricing/>. Even
  the smallest tier is ≈ **¥69,750/月**, because a 5-15 subsidiary structure
  still has to buy the 100-entity block.
- **Athennian** (entity management, partially published): Essentials
  "$25,000/year" including 100 entities; Professional and Enterprise show
  "Contact Sales for Pricing" — <https://www.athennian.com/pricing>. ≈
  **¥312,500/月** on that 100-entity basis (an outlier at this customer size).
- **BoardPro** (board portal, published): Essentials "$165 per board,
  monthly" ($1,650/yr), Premium "$275", Ultimate "$440", sub-committees billed
  separately at "US$82.50" monthly, unlimited users —
  <https://www.boardpro.com/pricing>. One board ≈ **¥24,750/月**.
- **Boardable** (board portal, published): Essentials "$20.99", Professional
  "$29.99", Professional+ "$35.99", all "per active user, per month" billed
  annually — <https://boardable.com/pricing/>. At 8 users ≈ **¥36,000/月**.
- **SmartDiscussion** (Japanese paperless board-meeting system, published):
  スターター 初期費用 20,000円 / 月額 20,000円（10ライセンス）; スタンダード
  初期 30,000円 / 月額 36,000円〜（20-200ライセンス）; エンタープライズ 初期
  80,000円 / 月額 98,000円〜 — <https://smartdiscussion.jp/price/enterprises>.
  ≈ **¥20,000-36,000/月** at this size.

**¥35,000/月 sits in the lower third of the realistic band**
(¥20,000-69,750/月; Athennian's ¥312,500/月 is an outlier priced for 100
entities). It is below EntityKeeper because that price buys an entity register
with registered-agent and filing services attached, and this actor is
explicitly neither an equity register nor a banking rail nor a tax/legal
opinion — `holdco.registry/distribution-amount-exceeds-distributable-reserves?`
is a ceiling recompute against the position's own recorded fields. It is above
the board-portal band (SmartDiscussion ¥20,000/月, BoardPro ¥24,750/月)
because board portals distribute papers and minutes and look at neither of the
two things that actually cause loss here: whether a proposed distribution
exceeds that position's own recorded distributable reserves, and whether
beneficial-ownership verification is still unresolved. Both are un-overridable
holds in this actor and exist in none of the five comparators.

**The price is flat per structure, not per user and not per entity, and that
shape is itself the argument**: adding a subsidiary must not move the invoice,
so this tier does not compete on EntityKeeper's entity-block axis (where 5
subsidiaries and 100 subsidiaries cost the same ¥69,750/月) nor on Boardable's
per-active-user axis. A family office that consolidates one more holding
vehicle should see governance cost stay still. The figure is derived only from
the measurements above; it is **not** carried over from the ¥50,000-150,000/月
range used by the HR/recruiting/CRM-anchored flagships.

**Subscribe (2026-08-10)**: a live Stripe Payment Link for the Managed
Starter tier (¥35,000/月 flat) is available now —
[**subscribe to Managed Starter**](https://buy.stripe.com/bJeaEYfz18tqd5K3HIeEo0d).
This is a no-code Stripe-hosted checkout (Gftd Japan 株式会社, JPY); nothing
in this repo's actor code changed. Managed-tenant setup is manual today —
there is no automated onboarding. **No family office or holding vehicle has
subscribed to this tier yet — this is a live, working checkout with zero paid
tenants, not a claim of existing revenue.**

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
