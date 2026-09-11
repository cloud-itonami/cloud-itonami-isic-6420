(ns holdco.registry-test
  (:require [clojure.test :refer [deftest is]]
            [holdco.registry :as r]))

;; ----------------------------- distribution-amount-exceeds-distributable-reserves? -----------------------------

(deftest not-exceeded-when-within-reserves
  (is (not (r/distribution-amount-exceeds-distributable-reserves?
            {:proposed-distribution-amount 500000 :distributable-reserves 2000000})))
  (is (not (r/distribution-amount-exceeds-distributable-reserves?
            {:proposed-distribution-amount 2000000 :distributable-reserves 2000000}))))

(deftest exceeded-when-over-reserves
  (is (r/distribution-amount-exceeds-distributable-reserves?
       {:proposed-distribution-amount 3000000 :distributable-reserves 2000000}))
  (is (r/distribution-amount-exceeds-distributable-reserves?
       {:proposed-distribution-amount 2000001 :distributable-reserves 2000000})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/distribution-amount-exceeds-distributable-reserves? {})))
  (is (not (r/distribution-amount-exceeds-distributable-reserves? {:proposed-distribution-amount 3000000}))))

;; ----------------------------- register-distribution-disbursement -----------------------------

(deftest disbursement-is-a-draft-not-a-real-disbursement
  (let [result (r/register-distribution-disbursement "position-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest disbursement-assigns-distribution-number
  (let [result (r/register-distribution-disbursement "position-1" "JPN" 7)]
    (is (= (get result "distribution_number") "JPN-DIS-000007"))
    (is (= (get-in result ["record" "position_id"]) "position-1"))
    (is (= (get-in result ["record" "kind"]) "distribution-disbursement-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest disbursement-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-distribution-disbursement "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-distribution-disbursement "position-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-distribution-disbursement "position-1" "JPN" -1))))

;; ----------------------------- register-ownership-change -----------------------------

(deftest change-is-a-draft-not-a-real-change
  (let [result (r/register-ownership-change "position-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest change-assigns-change-number
  (let [result (r/register-ownership-change "position-1" "JPN" 3)]
    (is (= (get result "change_number") "JPN-OWN-000003"))
    (is (= (get-in result ["record" "position_id"]) "position-1"))
    (is (= (get-in result ["record" "kind"]) "ownership-change-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest change-validation-rules
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-ownership-change "" "JPN" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-ownership-change "position-1" "" 0)))
  (is (thrown? #?(:clj Exception :cljs js/Error) (r/register-ownership-change "position-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-distribution-disbursement "position-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-distribution-disbursement "position-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-DIS-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-DIS-000001" (get-in hist2 [1 "record_id"])))))
