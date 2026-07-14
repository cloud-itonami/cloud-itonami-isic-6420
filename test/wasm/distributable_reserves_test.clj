(ns wasm.distributable-reserves-test
  "Hosts wasm/distributable_reserves.wasm (compiled from wasm/
  distributable_reserves.kotoba, see wasm/README.md) via kototama.tender --
  proves holdco.governor's distribution-exceeds-distributable-reserves
  check (`distribution-exceeds-distributable-reserves-violations` in
  src/holdco/governor.cljc, wrapping holdco.registry/distribution-amount-
  exceeds-distributable-reserves?) runs as a real WASM guest, not just as
  JVM Clojure.

  ABI: main is 0-arity (kotoba wasm emit rejects a parameterized main --
  :main-arity); the two real i32 inputs are written into the guest's
  exported linear memory at fixed offsets before calling main() -- see
  wasm/distributable_reserves.kotoba's ns docstring for the offset
  layout."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kototama.contract :as contract]
            [kototama.tender :as tender]))

(defn- wasm-bytes []
  (.readAllBytes (io/input-stream (io/file "wasm/distributable_reserves.wasm"))))

(defn- run-distribution-within-reserves? [proposed-distribution-amount distributable-reserves]
  (let [instance (tender/instantiate (wasm-bytes) [] (contract/host-caps {}))
        memory (.memory instance)]
    (.writeI32 memory 0 proposed-distribution-amount)
    (.writeI32 memory 4 distributable-reserves)
    (tender/call-main instance)))

(deftest distributable-reserves-wasm-approves-well-within-reserves
  (testing "proposed-distribution-amount well below distributable-reserves -> within reserves"
    (is (= 1 (run-distribution-within-reserves? 200000 1000000)))))

(deftest distributable-reserves-wasm-rejects-exceeding-reserves
  (testing "proposed-distribution-amount above distributable-reserves -> exceeds reserves"
    (is (= 0 (run-distribution-within-reserves? 1500000 1000000)))))

(deftest distributable-reserves-wasm-approves-exact-boundary
  (testing "proposed-distribution-amount exactly equal to distributable-reserves -> within reserves (<=)"
    (is (= 1 (run-distribution-within-reserves? 1000000 1000000)))))

(deftest distributable-reserves-wasm-rejects-boundary-plus-one
  (testing "proposed-distribution-amount one cent above distributable-reserves -> exceeds reserves"
    (is (= 0 (run-distribution-within-reserves? 1000001 1000000)))))
