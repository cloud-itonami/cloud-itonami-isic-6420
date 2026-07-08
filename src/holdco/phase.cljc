(ns holdco.phase
  "Phase 0->3 staged rollout -- the holding-company analog of `cloud-
  itonami-isic-6512`'s `casualty.phase`.

    Phase 0  read-only        -- no writes, still governor-gated.
    Phase 1  assisted-intake  -- position intake allowed, every write
                                 needs human approval.
    Phase 2  assisted-verify  -- adds disclosure verification +
                                 beneficial-ownership screening
                                 writes, still approval.
    Phase 3  supervised auto  -- governor-clean, high-confidence
                                 `:position/intake` (no capital risk
                                 yet) may auto-commit. `:actuation/
                                 disburse-distribution`/`:actuation/
                                 record-ownership-change` NEVER auto-
                                 commit, at any phase.

  `:actuation/disburse-distribution`/`:actuation/record-ownership-
  change` are deliberately ABSENT from every phase's `:auto` set,
  including phase 3 -- a permanent structural fact, not a rollout
  milestone still to come. Disbursing a real dividend/distribution and
  recording a real ownership-structure change are the two real-world
  corporate acts this actor performs; both are always a human
  principal's call. `holdco.governor`'s `:actuation/disburse-
  distribution`/`:actuation/record-ownership-change` high-stakes gate
  enforces the same invariant independently -- two layers, not one,
  agree on this. `:beneficial-ownership/screen` is likewise never
  auto-eligible, at any phase -- the same posture every sibling's
  screening op has. Phase 3's `:auto` set here has only ONE member
  (`:position/intake`) -- this domain has no separate no-capital-risk
  'file' lifecycle distinct from the position record itself.")

(def read-ops  #{})
(def write-ops #{:position/intake :disclosure/verify :beneficial-ownership/screen
                 :actuation/disburse-distribution :actuation/record-ownership-change})

;; NOTE the invariant: `:actuation/disburse-distribution`/`:actuation/
;; record-ownership-change` are members of `write-ops` (governor-
;; gated like any write) but are NEVER members of any phase's `:auto`
;; set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                       :auto #{}}
   1 {:label "assisted-intake"  :writes #{:position/intake}                                        :auto #{}}
   2 {:label "assisted-verify"  :writes #{:position/intake :disclosure/verify :beneficial-ownership/screen} :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:position/intake}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:actuation/disburse-distribution`/`:actuation/record-ownership-
    change` are never auto-eligible at any phase, so they always
    escalate once the governor clears them (or hold if the governor
    doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Holding Structure Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
