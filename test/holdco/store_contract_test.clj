(ns holdco.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [holdco.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Holdings K.K." (:subsidiary-name (store/position s "position-1"))))
      (is (= "JPN" (:jurisdiction (store/position s "position-1"))))
      (is (= 500000 (:proposed-distribution-amount (store/position s "position-1"))))
      (is (= 2000000 (:distributable-reserves (store/position s "position-1"))))
      (is (true? (:beneficial-ownership-verified? (store/position s "position-1"))))
      (is (= 3000000 (:proposed-distribution-amount (store/position s "position-3"))))
      (is (false? (:beneficial-ownership-verified? (store/position s "position-4"))))
      (is (false? (:distribution-disbursed? (store/position s "position-1"))))
      (is (false? (:ownership-change-recorded? (store/position s "position-1"))))
      (is (= ["position-1" "position-2" "position-3" "position-4"]
             (mapv :id (store/all-positions s))))
      (is (nil? (store/beneficial-ownership-of s "position-1")))
      (is (nil? (store/disclosure-of s "position-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/distribution-history s)))
      (is (= [] (store/ownership-change-history s)))
      (is (zero? (store/next-distribution-sequence s "JPN")))
      (is (zero? (store/next-ownership-change-sequence s "JPN")))
      (is (false? (store/position-already-disbursed? s "position-1")))
      (is (false? (store/position-already-recorded? s "position-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :position/upsert
                                 :value {:id "position-1" :subsidiary-name "Sato Holdings K.K."}})
        (is (= "Sato Holdings K.K." (:subsidiary-name (store/position s "position-1"))))
        (is (= 2000000 (:distributable-reserves (store/position s "position-1"))) "unrelated field preserved"))
      (testing "disclosure / beneficial-ownership payloads commit and read back"
        (store/commit-record! s {:effect :disclosure/set :path ["position-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/disclosure-of s "position-1")))
        (store/commit-record! s {:effect :beneficial-ownership/set :path ["position-1"]
                                 :payload {:position-id "position-1" :beneficial-ownership-verified? true}})
        (is (= {:position-id "position-1" :beneficial-ownership-verified? true} (store/beneficial-ownership-of s "position-1"))))
      (testing "distribution disbursement drafts a record and advances the sequence"
        (store/commit-record! s {:effect :position/mark-disbursed :path ["position-1"]})
        (is (= "JPN-DIS-000000" (get (first (store/distribution-history s)) "record_id")))
        (is (= "distribution-disbursement-draft" (get (first (store/distribution-history s)) "kind")))
        (is (true? (:distribution-disbursed? (store/position s "position-1"))))
        (is (= 1 (count (store/distribution-history s))))
        (is (= 1 (store/next-distribution-sequence s "JPN")))
        (is (true? (store/position-already-disbursed? s "position-1")))
        (is (false? (store/position-already-disbursed? s "position-2"))))
      (testing "ownership change drafts a record and advances the sequence"
        (store/commit-record! s {:effect :position/mark-recorded :path ["position-1"]})
        (is (= "JPN-OWN-000000" (get (first (store/ownership-change-history s)) "record_id")))
        (is (= "ownership-change-draft" (get (first (store/ownership-change-history s)) "kind")))
        (is (true? (:ownership-change-recorded? (store/position s "position-1"))))
        (is (= 1 (count (store/ownership-change-history s))))
        (is (= 1 (store/next-ownership-change-sequence s "JPN")))
        (is (true? (store/position-already-recorded? s "position-1")))
        (is (false? (store/position-already-recorded? s "position-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/position s "nope")))
    (is (= [] (store/all-positions s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/distribution-history s)))
    (is (= [] (store/ownership-change-history s)))
    (is (zero? (store/next-distribution-sequence s "JPN")))
    (is (zero? (store/next-ownership-change-sequence s "JPN")))
    (store/with-positions s {"x" {:id "x" :subsidiary-name "n"
                                  :proposed-distribution-amount 500000 :distributable-reserves 2000000
                                  :beneficial-ownership-verified? true
                                  :distribution-disbursed? false :ownership-change-recorded? false
                                  :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:subsidiary-name (store/position s "x"))))))
