(ns holdco.governor-contract-test
  "The governor contract as executable tests -- the holding-company
  analog of `cloud-itonami-isic-6512`'s `casualty.governor-contract-
  test`. The single invariant under test:

    HoldCo-LLM never disburses a distribution or records an
    ownership change the Holding Structure Governor would reject,
    `:actuation/disburse-distribution`/`:actuation/record-ownership-
    change` NEVER auto-commit at any phase, `:position/intake` (no
    direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [holdco.store :as store]
            [holdco.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :principal :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a disclosure
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :disclosure/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through beneficial-ownership screening -> approve,
  leaving a screening on file. Only safe to call for a position whose
  beneficial ownership is already verified -- an unverified status
  HARD-holds the screen itself (see
  `beneficial-ownership-unresolved-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :beneficial-ownership/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :position/intake :subject "position-1"
                   :patch {:id "position-1" :subsidiary-name "Sato Holdings K.K."}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Holdings K.K." (:subsidiary-name (store/position db "position-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest disclosure-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :disclosure/verify :subject "position-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/disclosure-of db "position-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a disclosure/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :disclosure/verify :subject "position-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/disclosure-of db "position-1")) "no disclosure written"))))

(deftest disburse-distribution-without-disclosure-is-held
  (testing "actuation/disburse-distribution before any disclosure verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/disburse-distribution :subject "position-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest distribution-exceeds-distributable-reserves-is-held
  (testing "a position whose own proposed distribution exceeds its own recorded distributable reserves -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "position-3")
          res (exec-op actor "t5" {:op :actuation/disburse-distribution :subject "position-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:distribution-exceeds-distributable-reserves} (-> (store/ledger db) last :basis)))
      (is (empty? (store/distribution-history db))))))

(deftest beneficial-ownership-unresolved-is-held-and-unoverridable
  (testing "an unverified beneficial-ownership status on a position -> HOLD, and never reaches request-approval -- exercised via :beneficial-ownership/screen DIRECTLY, not via the actuation op against an unscreened position (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's, banking's, advertising's, polling's, research's, design's, nursing's, sports's, alliedhealth's and laundry's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :beneficial-ownership/screen :subject "position-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:beneficial-ownership-verification-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/beneficial-ownership-of db "position-4")) "no clearance written"))))

(deftest disburse-distribution-always-escalates-then-human-decides
  (testing "a clean, fully-assessed position still ALWAYS interrupts for human approval -- actuation/disburse-distribution is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "position-1")
          r1 (exec-op actor "t7" {:op :actuation/disburse-distribution :subject "position-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, disbursement record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:distribution-disbursed? (store/position db "position-1"))))
          (is (= 1 (count (store/distribution-history db))) "one draft disbursement record"))))))

(deftest record-ownership-change-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, beneficial-ownership-verified position still ALWAYS interrupts for human approval -- actuation/record-ownership-change is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "position-1")
          _ (screen! actor "t8pre2" "position-1")
          r1 (exec-op actor "t8" {:op :actuation/record-ownership-change :subject "position-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, ownership-change record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:ownership-change-recorded? (store/position db "position-1"))))
          (is (= 1 (count (store/ownership-change-history db))) "one draft ownership-change record"))))))

(deftest disburse-distribution-double-disbursement-is-held
  (testing "disbursing a distribution to the same position twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "position-1")
          _ (exec-op actor "t9a" {:op :actuation/disburse-distribution :subject "position-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/disburse-distribution :subject "position-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-disbursed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/distribution-history db))) "still only the one earlier disbursement"))))

(deftest record-ownership-change-double-recording-is-held
  (testing "recording an ownership change for the same position twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "position-1")
          _ (screen! actor "t10pre2" "position-1")
          _ (exec-op actor "t10a" {:op :actuation/record-ownership-change :subject "position-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/record-ownership-change :subject "position-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-recorded} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/ownership-change-history db))) "still only the one earlier recording"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :position/intake :subject "position-1"
                          :patch {:id "position-1" :subsidiary-name "Sato Holdings K.K."}} operator)
      (exec-op actor "b" {:op :disclosure/verify :subject "position-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
