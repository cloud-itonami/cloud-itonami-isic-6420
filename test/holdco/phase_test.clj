(ns holdco.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/disburse-distribution`/`:actuation/record-
  ownership-change` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [holdco.phase :as phase]))

(deftest disburse-distribution-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real distribution disbursement"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/disburse-distribution))
          (str "phase " n " must not auto-commit :actuation/disburse-distribution")))))

(deftest record-ownership-change-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real ownership-structure-change recording"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/record-ownership-change))
          (str "phase " n " must not auto-commit :actuation/record-ownership-change")))))

(deftest beneficial-ownership-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :beneficial-ownership/screen))
          (str "phase " n " must not auto-commit :beneficial-ownership/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":position/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:position/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :position/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/disburse-distribution} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/record-ownership-change} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :position/intake} :commit)))))
