(ns holdco.kernels.gate-test
  "The safety kernel's executable spec, three ways:

  1. battery lock — the kernel's own in-subset battery must pass
     case-for-case (`battery-case-count` == `(battery-pass-count)`),
     so a silently dropped case can't survive review.
  2. parity matrix — the kernel's phase core is compared against an
     independent reference copy of the ORIGINAL set-based cond logic
     over the FULL input space (all phases incl. out-of-range, all op
     codes incl. the reserved-read 0 and unknown 6, all governor
     dispositions). The façade delegates, so this is the guard that
     delegation didn't change semantics.
  3. governor boundary — the confidence floor boundary, the
     fail-closed treatment of out-of-range confidence, and the
     distributable-reserves ceiling boundary, exercised through the
     real `holdco.governor/check` façade."
  (:require [clojure.test :refer [deftest is testing]]
            [holdco.facts :as facts]
            [holdco.governor :as governor]
            [holdco.kernels.gate :as gate]
            [holdco.store :as store]))

(deftest battery-lock
  (is (= gate/battery-case-count (gate/battery-pass-count))
      "every battery case must pass; update battery-case-count only when adding cases"))

(deftest confidence-floor-pinned-to-facade-constant
  (is (= gate/confidence-floor-x100
         (Math/round (* 100.0 governor/confidence-floor)))
      "the façade's documented 0.6 and the kernel's deciding 60 must not drift"))

;; ---------------------------------------------------------------
;; Independent oracle for the parity matrix: the pre-kernel phase
;; logic (sets + cond) restated over wire codes, PLUS the kernel's
;; fail-closed contract for out-of-range phases (no writes at all).
;; The original façade normalized an unknown phase to default-phase 3
;; BEFORE this logic and still does — so out-of-range rows here pin
;; the kernel's own contract, not a façade behavior change. NOTE
;; `read-ops` is EMPTY in this domain, so `ref-read-ops` is the empty
;; set and the reserved read code 0 behaves like an unknown op.

(def ^:private ref-read-ops #{})
(def ^:private ref-phases
  {0 {:writes #{}            :auto #{}}
   1 {:writes #{1}           :auto #{}}
   2 {:writes #{1 2 3}       :auto #{}}
   3 {:writes #{1 2 3 4 5}   :auto #{1}}})

(defn- ref-gate [phase op gov]
  (let [{:keys [writes auto]} (get ref-phases phase {:writes #{} :auto #{}})]
    (cond
      (= gov 2)                        {:d 2 :r 0}
      (contains? ref-read-ops op)      {:d gov :r 0}
      (not (contains? writes op))      {:d 2 :r 1}
      (and (= gov 0)
           (not (contains? auto op)))  {:d 1 :r 2}
      :else                            {:d gov :r 0})))

(deftest phase-parity-matrix
  (testing "kernel == reference over the full input space (189 combos)"
    (doseq [phase [-1 0 1 2 3 4 7 100 -99]
            op    [0 1 2 3 4 5 6]
            gov   [0 1 2]]
      (let [expected (ref-gate phase op gov)]
        (is (= (:d expected) (gate/phase-disposition phase op gov))
            (str "disposition mismatch at phase=" phase " op=" op " gov=" gov))
        (is (= (:r expected) (gate/phase-reason phase op gov))
            (str "reason mismatch at phase=" phase " op=" op " gov=" gov))))))

(deftest actuation-auto-enabled-nowhere
  (testing "ops 4/5 (:actuation/disburse-distribution /
            :actuation/record-ownership-change) are auto-enabled at NO
            phase — kernel restates the phase table's permanent
            structural invariant (op 3 :beneficial-ownership/screen
            likewise)"
    (doseq [phase [-1 0 1 2 3 4 7]
            op    [3 4 5]]
      (is (= 0 (gate/op-auto-enabled phase op))))))

;; ---------------------------------------------------------------
;; Governor boundary through the real façade. op :position/intake
;; touches neither the store nor the evidence/reserves checks, and a
;; plain proposal carries no beneficial-ownership flag (the check
;; fires on an explicit `false`, and `(false? nil)` is false), so the
;; verdict is decided purely by confidence/actuation — nil store is
;; safe.

(defn- verdict [proposal]
  (governor/check {:op :position/intake :subject "position-x"} {} proposal nil))

(deftest confidence-floor-boundary
  (testing "0.59 escalates, 0.60 clears (kernel decides at integer x100)"
    (is (true?  (:escalate? (verdict {:confidence 0.59}))))
    (is (false? (:ok? (verdict {:confidence 0.59}))))
    (is (true?  (:ok? (verdict {:confidence 0.6}))))
    (is (false? (:escalate? (verdict {:confidence 0.6}))))))

(deftest out-of-range-confidence-fails-closed
  (testing "an advisor reporting impossible confidence gets MORE scrutiny,
            not auto-commit (kernel is deliberately stricter than the old
            inline `(< conf floor)` here)"
    (is (true? (:escalate? (verdict {:confidence 1.5}))))
    (is (false? (:ok? (verdict {:confidence 1.5}))))
    (is (true? (:escalate? (verdict {:confidence -0.2}))))))

;; ---------------------------------------------------------------
;; Distributable-reserves ceiling boundary through the real façade —
;; the kernel re-decides the comparison from the position's raw
;; integer fields (the exact strict-> comparison the registry
;; predicate makes), the façade still produces the human-readable
;; violation map, and both must agree at the boundary. The fixture
;; keeps every OTHER hard check clean (cites present, full evidence
;; checklist on file, beneficial ownership verified, no prior
;; actuation).

(defn- position-store
  "A MemStore holding one governor-clean position with the given
  numeric fields, plus a full JPN evidence checklist on file."
  [amount reserves]
  (let [s (store/seed-db)]
    (store/with-positions s {"position-x"
                             {:id "position-x" :subsidiary-name "Boundary Holdings"
                              :proposed-distribution-amount amount
                              :distributable-reserves reserves
                              :beneficial-ownership-verified? true
                              :distribution-disbursed? false
                              :ownership-change-recorded? false
                              :jurisdiction "JPN" :status :intake}})
    (store/commit-record! s {:effect :disclosure/set :path ["position-x"]
                             :payload {:jurisdiction "JPN"
                                       :checklist (facts/evidence-checklist "JPN")}})
    s))

(defn- disburse-verdict [amount reserves]
  (governor/check {:op :actuation/disburse-distribution :subject "position-x"} {}
                  {:confidence 0.9 :cites ["JPN-distribution-spec"]}
                  (position-store amount reserves)))

(deftest distributable-reserves-boundary-through-facade
  (testing "amount exactly at the reserves ceiling clears (strict >, not >=)"
    (let [v (disburse-verdict 500 500)]
      (is (false? (:hard? v)))
      (is (empty? (:violations v)))))
  (testing "one currency unit over the ceiling hard-holds, kernel and
            violation map agreeing"
    (let [v (disburse-verdict 501 500)]
      (is (true? (:hard? v)))
      (is (false? (:ok? v)))
      (is (some #{:distribution-exceeds-distributable-reserves}
                (mapv :rule (:violations v))))))
  (testing "one currency unit under the ceiling clears"
    (is (false? (:hard? (disburse-verdict 499 500))))))

(deftest actuation-still-escalates-and-hard-still-wins
  (is (true? (:escalate? (verdict {:confidence 0.99
                                   :stake :actuation/disburse-distribution}))))
  (is (true? (:escalate? (verdict {:confidence 0.99
                                   :stake :actuation/record-ownership-change}))))
  (testing "a hard violation dominates actuation escalation"
    (let [v (governor/check {:op :disclosure/verify :subject "position-x"} {}
                            {:confidence 0.99 :stake :actuation/disburse-distribution
                             :cites []} nil)]
      (is (true? (:hard? v)))
      (is (false? (:escalate? v)))
      (is (some #{:no-spec-basis} (mapv :rule (:violations v)))))))
