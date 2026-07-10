(ns holdco.corporate-intel-test
  "Cross-reference integration test: `holdco.corporate-intel/ownership-
  chain`, wired into `holdco.holdcoadvisor/screen-beneficial-ownership` via
  `mock-advisor`'s `:corporate-intel-ownership-chain` opt, actually calls
  through cloud-itonami-isic-8291's REAL `:disclosure/ownership-chain` op
  (its own DisclosureGovernor and all) -- not a stub -- for the positive
  cases; a stub is used only to simulate 8291 REJECTING the query outright
  (a configuration problem on this side).

  The single invariant under test: this repo's governor has only TWO
  meaningful `:beneficial-ownership-verified?` values (`true`/`false`) --
  there is no third 'incomplete' state -- so ANY non-definitively-verified
  signal from 8291 (a pending-human-review escalation, or a rejected
  query) collapses to `false`, which `holdco.governor`'s beneficial-
  ownership-verification-unresolved check treats as an UNCONDITIONAL HARD
  violation: immediate HOLD, no interrupt, no approval possible. This is
  the same vocabulary-collapse `cloud-itonami-isic-6419` (banking) already
  applies to its own binary verdict space."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [holdco.store :as store]
            [holdco.operation :as op]
            [holdco.holdcoadvisor :as holdcoadvisor]
            [holdco.corporate-intel :as ci]))

(def operator {:actor-id "op-1" :actor-role :principal :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(deftest position-5-without-integration-screens-verified-and-commits
  (testing "sanity: (op/build db) with NO :advisor override -- position-5 (locally
            verified, subsidiary-name matches 8291's co-300) screens true and
            commits after approval, same as any other clean position, since the
            8291 cross-reference is not wired in at all"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec-op actor "ci-t1" {:op :beneficial-ownership/screen :subject "position-5"} operator)]
      (is (= :interrupted (:status res)) "clean screen still always escalates for human approval")
      (let [r2 (approve! actor "ci-t1")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:beneficial-ownership-verified? (store/beneficial-ownership-of db "position-5"))))))))

(deftest position-5-with-integration-wired-in-hard-holds-on-flagged-owner
  (testing "with holdco.corporate-intel/ownership-chain (the REAL 8291 op) wired
            in: 8291 finds co-300's real owner co-200 (sanctions-flagged, 60%
            per 8291's own seeded relationship edge) and escalates for ITS OWN
            human reviewer -- :pending-human-review? true. This repo has no
            'incomplete' vocabulary, so it maps to
            :beneficial-ownership-verified? false, which HARD-holds
            immediately (unconditional check) -- no interrupt, no approval
            possible, even though the position was locally marked verified"
    (let [db (store/seed-db)
          advisor (holdcoadvisor/mock-advisor {:corporate-intel-ownership-chain ci/ownership-chain})
          actor (op/build db {:advisor advisor})
          res (exec-op actor "ci-t2" {:op :beneficial-ownership/screen :subject "position-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:beneficial-ownership-verification-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/beneficial-ownership-of db "position-5")) "no clearance written"))))

(deftest unrelated-position-with-integration-wired-in-still-commits-clean
  (testing "position-1's subsidiary-name (\"Sato Holdings K.K.\") has no match
            in 8291's demo company catalog -- the cross-reference is
            additive/neutral (has-sourced-ownership-data? false, no owners),
            NOT stricter-by-default: the position still screens true and
            commits normally after approval, exactly like the no-integration
            case"
    (let [db (store/seed-db)
          advisor (holdcoadvisor/mock-advisor {:corporate-intel-ownership-chain ci/ownership-chain})
          actor (op/build db {:advisor advisor})
          res (exec-op actor "ci-t3" {:op :beneficial-ownership/screen :subject "position-1"} operator)]
      (is (= :interrupted (:status res)) "clean screen still always escalates for human approval")
      (let [r2 (approve! actor "ci-t3")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (true? (:beneficial-ownership-verified? (store/beneficial-ownership-of db "position-1"))))))))

(deftest held-corporate-intel-response-hard-holds-not-silently-verified
  (testing "a stubbed :held? response (simulating this blueprint's own 8291
            tenant contract being missing/inactive/wrong-tier -- a
            configuration problem, not a finding) also collapses to
            :beneficial-ownership-verified? false and HARD-holds immediately
            -- a rejected query is never silently treated as confirming clean
            ownership"
    (let [db (store/seed-db)
          stub (constantly {:held? true :reason [:licensed-disclosure]})
          advisor (holdcoadvisor/mock-advisor {:corporate-intel-ownership-chain stub})
          actor (op/build db {:advisor advisor})
          res (exec-op actor "ci-t4" {:op :beneficial-ownership/screen :subject "position-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:beneficial-ownership-verification-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/beneficial-ownership-of db "position-5"))))))
