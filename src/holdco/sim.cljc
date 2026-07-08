(ns holdco.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean position through
  intake -> disclosure verification -> beneficial-ownership screening
  -> distribution-disbursement proposal (always escalates) -> human
  approval -> commit, then through ownership-change-recording proposal
  (always escalates) -> human approval -> commit, then shows five HARD
  holds (a jurisdiction with no spec-basis, a proposed distribution
  exceeding the position's own distributable reserves, an unresolved
  beneficial-ownership verification screened directly via
  `:beneficial-ownership/screen` [never via an actuation op against an
  unscreened position -- see this actor's own governor ns docstring /
  the lesson `parksafety`'s ADR-2607071922 Decision 5, `eldercare`'s,
  `museum`'s, `conservation`'s, `salon`'s, `entertainment`'s,
  `casework`'s, `hospital`'s, `facility`'s, `school`'s, `association`'s,
  `leasing`'s, `behavioral`'s, `secondary`'s, `card`'s, `water`'s,
  `telecom`'s, `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s, `fab`'s, `energy`'s, `care`'s, `navigator`'s,
  `learning`'s, `banking`'s, `advertising`'s, `polling`'s, `research`'s,
  `design`'s, `nursing`'s, `sports`'s, `alliedhealth`'s and `laundry`'s
  ADR-0001s already recorded], and a double disbursement/recording of
  an already-processed position) that never reach a human at all, and
  prints the audit ledger + the draft distribution-disbursement and
  ownership-change records."
  (:require [langgraph.graph :as g]
            [holdco.store :as store]
            [holdco.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :principal :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== position/intake position-1 (JPN, clean; distribution within reserves, beneficial ownership verified) ==")
    (println (exec! actor "t1" {:op :position/intake :subject "position-1"
                                :patch {:id "position-1" :subsidiary-name "Sato Holdings K.K."}} operator))

    (println "== disclosure/verify position-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :disclosure/verify :subject "position-1"} operator))
    (println (approve! actor "t2"))

    (println "== beneficial-ownership/screen position-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :beneficial-ownership/screen :subject "position-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/disburse-distribution position-1 (always escalates -- actuation/disburse-distribution) ==")
    (let [r (exec! actor "t4" {:op :actuation/disburse-distribution :subject "position-1"} operator)]
      (println r)
      (println "-- human principal approves --")
      (println (approve! actor "t4")))

    (println "== actuation/record-ownership-change position-1 (always escalates -- actuation/record-ownership-change) ==")
    (let [r (exec! actor "t5" {:op :actuation/record-ownership-change :subject "position-1"} operator)]
      (println r)
      (println "-- human principal approves --")
      (println (approve! actor "t5")))

    (println "== disclosure/verify position-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :disclosure/verify :subject "position-2" :no-spec? true} operator))

    (println "== disclosure/verify position-3 (escalates -- human approves; sets up the distributable-reserves test) ==")
    (println (exec! actor "t7" {:op :disclosure/verify :subject "position-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/disburse-distribution position-3 (distribution 3,000,000 > reserves 2,000,000 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/disburse-distribution :subject "position-3"} operator))

    (println "== beneficial-ownership/screen position-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :beneficial-ownership/screen :subject "position-4"} operator))

    (println "== actuation/disburse-distribution position-1 AGAIN (double-disbursement -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/disburse-distribution :subject "position-1"} operator))

    (println "== actuation/record-ownership-change position-1 AGAIN (double-recording -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/record-ownership-change :subject "position-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft distribution-disbursement records ==")
    (doseq [r (store/distribution-history db)] (println r))

    (println "== draft ownership-change records ==")
    (doseq [r (store/ownership-change-history db)] (println r))))
