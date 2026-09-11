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
  'file' lifecycle distinct from the position record itself.

  The decision core is delegated to the safety kernel
  `holdco.kernels.gate` (integer-coded, fail-closed, safe-kotoba
  subset); this namespace keeps the human-readable phase table (the
  documentation and structural-invariant tests read it) and does the
  keyword<->wire-code mapping at the boundary. The kernel's own
  battery and the parity matrix in `holdco.kernels.gate-test` pin the
  two representations together."
  (:require [holdco.kernels.gate :as kernel]))

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

;; ---- kernel wire-code bridges (façade-side, not kernel vocabulary) ----

(defn- op->code
  "Kernel op wire code. `read-ops` is EMPTY in this domain, so nothing
  maps to the reserved read code 0 (if `read-ops` ever gains a member,
  the kernel needs a read pass-through branch too — today code 0 has
  no rights in-kernel, fail-closed). Unknown ops map to 6 (unknown
  write) — the kernel never write-enables it, so an unrecognized op
  fails closed to HOLD exactly as the old set-membership logic did."
  [op]
  (cond
    (= op :position/intake)                   1
    (= op :disclosure/verify)                 2
    (= op :beneficial-ownership/screen)       3
    (= op :actuation/disburse-distribution)   4
    (= op :actuation/record-ownership-change) 5
    :else                                     6))

(defn- disposition->code [d]
  (cond (= d :commit) 0 (= d :escalate) 1 (= d :hold) 2 :else 2))

(defn- code->disposition [c]
  (if (= c 0) :commit (if (= c 1) :escalate :hold)))

(defn- code->reason [c]
  (if (= c 1) :phase-disabled (if (= c 2) :phase-approval nil)))

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
  (let [p (if (contains? phases phase) phase default-phase)
        op-code (op->code op)
        gov-code (disposition->code governor-disposition)
        d (kernel/phase-disposition p op-code gov-code)
        r (kernel/phase-reason p op-code gov-code)]
    {:disposition (code->disposition d)
     :reason (code->reason r)}))

(defn verdict->disposition
  "Map a Holding Structure Governor verdict to a base disposition
  before the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
