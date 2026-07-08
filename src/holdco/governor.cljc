(ns holdco.governor
  "Holding Structure Governor -- the independent compliance layer that
  earns the HoldCo-LLM the right to commit. The LLM has no notion of
  jurisdictional corporate-distribution/beneficial-ownership law,
  whether a proposed distribution amount actually stays within a
  position's own recorded distributable reserves, whether a
  position's own beneficial-ownership status has actually stayed
  verified, or when an act stops being a draft and becomes a real-
  world distribution disbursement or ownership-structure-change
  recording, so this MUST be a separate system able to *reject* a
  proposal and fall back to HOLD -- the holding-company analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, a
  distribution exceeding a position's own distributable reserves, an
  unverified beneficial-ownership status, or a double disbursement/
  recording). The confidence/actuation gate is SOFT: it asks a human
  to look (low confidence / actuation), and the human may approve --
  but see `holdco.phase`: for `:stake :actuation/disburse-
  distribution`/`:actuation/record-ownership-change` (a real dividend
  disbursement or a real ownership-structure-change recording) NO
  phase ever allows auto-commit either. Two independent layers agree
  that actuation is always a human call.

    1. Spec-basis                  -- did the disclosure proposal cite
                                       an OFFICIAL source (`holdco.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/disburse-
                                       distribution`/`:actuation/
                                       record-ownership-change`, has
                                       the position actually been
                                       assessed with a full ownership-
                                       structure-disclosure-record/
                                       beneficial-ownership-
                                       verification-record/
                                       distributable-reserves-
                                       certification-record/
                                       distribution-authorization-
                                       record evidence checklist on
                                       file?
    3. Distribution exceeds
       distributable reserves         -- for `:actuation/disburse-
                                       distribution`, INDEPENDENTLY
                                       recompute whether the
                                       position's own proposed
                                       distribution amount exceeds its
                                       own recorded distributable-
                                       reserves ceiling (`holdco.
                                       registry/distribution-amount-
                                       exceeds-distributable-
                                       reserves?`) -- needs no
                                       proposal inspection at all. A
                                       GENUINELY NEW concept in this
                                       fleet, grep-verified absent from
                                       every prior sibling's check
                                       names -- the NINTH instance of
                                       this fleet's MAXIMUM-ceiling
                                       check family (`facility`/
                                       `school`/`card`/`recovery`/
                                       `care`/`navigator`/
                                       `advertising`/`nursing`
                                       established the first eight),
                                       grounded in real corporate-
                                       distribution law (Delaware GCL
                                       §170, UK Companies Act 2006
                                       Part 23, Japan Companies Act
                                       §461, Germany's AktG §57-58).
    4. Beneficial ownership
       verification unresolved        -- reported by THIS proposal
                                       itself (a `:beneficial-
                                       ownership/screen` that just
                                       found an unverified status), or
                                       already on file for the
                                       position (`:beneficial-
                                       ownership/screen`/either
                                       actuation op). Evaluated
                                       UNCONDITIONALLY (not scoped to
                                       a specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...(forty-
                                       five prior siblings, most
                                       recently `laundry.governor/
                                       certification-not-current-
                                       violations`)...established -- a
                                       GENUINELY NEW concept in this
                                       fleet, grep-verified absent from
                                       every prior sibling, the 46th
                                       distinct application of this
                                       discipline overall, grounded in
                                       real beneficial-ownership-
                                       transparency law (US Corporate
                                       Transparency Act/FinCEN, UK PSC
                                       register, Germany's
                                       Transparenzregister).
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       disburse-distribution`/
                                       `:actuation/record-ownership-
                                       change` (REAL corporate acts)
                                       -> escalate.

  Two more guards, double-disbursement/double-recording prevention,
  are enforced but NOT listed as numbered HARD checks above because
  they need no upstream comparison at all -- `already-disbursed-
  violations`/`already-recorded-violations` refuse to disburse a
  distribution/record an ownership change for the SAME position
  twice, off dedicated `:distribution-disbursed?`/`:ownership-change-
  recorded?` facts (never a `:status` value) -- the SAME 'check a
  dedicated boolean, not status' discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320)."
  (:require [holdco.facts :as facts]
            [holdco.registry :as registry]
            [holdco.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Disbursing a real dividend/distribution and recording a real
  ownership-structure change are the two real-world actuation events
  this actor performs -- a two-member set, matching every prior dual-
  actuation sibling's shape. Both are POSITIVE actuations (disbursing/
  finalizing a real record), matching this fleet's majority actuation
  shape (3600/6190 remain the only negative-actuation exceptions)."
  #{:actuation/disburse-distribution :actuation/record-ownership-change})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:disclosure/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  corporate-distribution requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:disclosure/verify :actuation/disburse-distribution :actuation/record-ownership-change} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は分配可能額基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/disburse-distribution`/`:actuation/record-
  ownership-change`, the jurisdiction's required ownership-structure-
  disclosure-record/beneficial-ownership-verification-record/
  distributable-reserves-certification-record/distribution-
  authorization-record evidence must actually be satisfied -- do not
  trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/disburse-distribution :actuation/record-ownership-change} op)
    (let [p (store/position st subject)
          disclosure (store/disclosure-of st subject)]
      (when-not (and disclosure
                     (facts/required-evidence-satisfied?
                      (:jurisdiction p) (:checklist disclosure)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(株主構成開示記録/実質的支配者確認記録/分配可能額証明記録/分配承認記録等)が充足していない状態での提案"}]))))

(defn- distribution-exceeds-distributable-reserves-violations
  "For `:actuation/disburse-distribution`, INDEPENDENTLY recompute
  whether the position's own proposed distribution amount exceeds its
  own recorded distributable-reserves ceiling via `holdco.registry/
  distribution-amount-exceeds-distributable-reserves?` -- needs no
  proposal inspection at all, since its inputs are permanent ground-
  truth fields already on the position."
  [{:keys [op subject]} st]
  (when (= op :actuation/disburse-distribution)
    (let [p (store/position st subject)]
      (when (registry/distribution-amount-exceeds-distributable-reserves? p)
        [{:rule :distribution-exceeds-distributable-reserves
          :detail (str subject " の提案分配額(" (:proposed-distribution-amount p)
                      ")が分配可能額(" (:distributable-reserves p) ")を超過")}]))))

(defn- beneficial-ownership-verification-unresolved-violations
  "An unresolved (unverified) beneficial-ownership status -- reported
  by THIS proposal (e.g. a `:beneficial-ownership/screen` that itself
  just found an unverified status), or already on file in the store
  for the position (`:beneficial-ownership/screen`/either actuation
  op) -- is a HARD, un-overridable hold. Evaluated UNCONDITIONALLY
  (not scoped to a specific op) so the screening op itself can HARD-
  hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (false? (get-in proposal [:value :beneficial-ownership-verified?]))
        position-id (when (contains? #{:beneficial-ownership/screen :actuation/disburse-distribution :actuation/record-ownership-change} op) subject)
        hit-on-file? (and position-id (false? (:beneficial-ownership-verified? (store/beneficial-ownership-of st position-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :beneficial-ownership-verification-unresolved
        :detail "実質的支配者情報が未確認の状態での提案は進められない"}])))

(defn- already-disbursed-violations
  "For `:actuation/disburse-distribution`, refuses to disburse a
  distribution for the SAME position twice, off a dedicated
  `:distribution-disbursed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/disburse-distribution)
    (when (store/position-already-disbursed? st subject)
      [{:rule :already-disbursed
        :detail (str subject " は既に分配済み")}])))

(defn- already-recorded-violations
  "For `:actuation/record-ownership-change`, refuses to record an
  ownership change for the SAME position twice, off a dedicated
  `:ownership-change-recorded?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/record-ownership-change)
    (when (store/position-already-recorded? st subject)
      [{:rule :already-recorded
        :detail (str subject " は既に持株構成変更記録済み")}])))

(defn check
  "Censors a HoldCo-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (distribution-exceeds-distributable-reserves-violations request st)
                           (beneficial-ownership-verification-unresolved-violations request proposal st)
                           (already-disbursed-violations request st)
                           (already-recorded-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
