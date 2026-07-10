(ns holdco.store
  "SSoT for the holding-company actor, behind a `Store` protocol so
  the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/holdco/store_contract_test.clj), which is the whole point: the
  actor, the Holding Structure Governor and the audit ledger never
  know which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (disbursing a distribution, recording an ownership
  change) acting on the SAME entity (a `position`), each with its OWN
  history collection, sequence counter and dedicated double-actuation-
  guard boolean (`:distribution-disbursed?`/`:ownership-change-
  recorded?`, never a `:status` value) -- the same discipline every
  prior sibling governor's guards establish, informed by `cloud-
  itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which position was
  screened for verified beneficial ownership, which distribution was
  disbursed, which ownership change was recorded, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a family office or corporate-
  services provider trusting a holding-company administrator needs,
  and the evidence an operator needs if a disbursement or ownership-
  change decision is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [holdco.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (position [s id])
  (all-positions [s])
  (beneficial-ownership-of [s position-id] "committed beneficial-ownership screening verdict for a position, or nil")
  (disclosure-of [s position-id] "committed ownership-structure disclosure assessment, or nil")
  (ledger [s])
  (distribution-history [s] "the append-only distribution-disbursement history (holdco.registry drafts)")
  (ownership-change-history [s] "the append-only ownership-change history (holdco.registry drafts)")
  (next-distribution-sequence [s jurisdiction] "next distribution-number sequence for a jurisdiction")
  (next-ownership-change-sequence [s jurisdiction] "next ownership-change-number sequence for a jurisdiction")
  (position-already-disbursed? [s position-id] "has this position's distribution already been disbursed?")
  (position-already-recorded? [s position-id] "has this position's ownership change already been recorded?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-positions [s positions] "replace/seed the position directory (map id->position)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained position set covering both actuation
  lifecycles (disbursing a distribution, recording an ownership
  change) so the actor + tests run offline."
  []
  {:positions
   {"position-1" {:id "position-1" :subsidiary-name "Sato Holdings K.K."
                  :proposed-distribution-amount 500000 :distributable-reserves 2000000
                  :beneficial-ownership-verified? true
                  :distribution-disbursed? false :ownership-change-recorded? false
                  :jurisdiction "JPN" :status :intake}
    "position-2" {:id "position-2" :subsidiary-name "Atlantis Holdings"
                  :proposed-distribution-amount 500000 :distributable-reserves 2000000
                  :beneficial-ownership-verified? true
                  :distribution-disbursed? false :ownership-change-recorded? false
                  :jurisdiction "ATL" :status :intake}
    "position-3" {:id "position-3" :subsidiary-name "鈴木ホールディングス"
                  :proposed-distribution-amount 3000000 :distributable-reserves 2000000
                  :beneficial-ownership-verified? true
                  :distribution-disbursed? false :ownership-change-recorded? false
                  :jurisdiction "JPN" :status :intake}
    "position-4" {:id "position-4" :subsidiary-name "田中ホールディングス"
                  :proposed-distribution-amount 500000 :distributable-reserves 2000000
                  :beneficial-ownership-verified? false
                  :distribution-disbursed? false :ownership-change-recorded? false
                  :jurisdiction "JPN" :status :intake}
    ;; position-5: locally marked verified, but its :subsidiary-name is an
    ;; EXACT match for cloud-itonami-isic-8291's own demo company co-300's
    ;; legal name. Per 8291's sourced relationship-graph data (`dossier.
    ;; store/demo-data`), co-300's real owner is the sanctions-flagged
    ;; co-200 at 60% -- a fact this position's self-declared
    ;; :beneficial-ownership-verified? true never surfaces on its own.
    ;; Exists purely to prove `holdco.corporate-intel/ownership-chain`'s
    ;; cross-reference catches what local-only verification alone would
    ;; miss (see test/holdco/corporate_intel_test.clj).
    "position-5" {:id "position-5" :subsidiary-name "出島サブシディアリ株式会社(デモ)"
                  :proposed-distribution-amount 500000 :distributable-reserves 2000000
                  :beneficial-ownership-verified? true
                  :distribution-disbursed? false :ownership-change-recorded? false
                  :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- disburse-distribution!
  "Backend-agnostic `:position/mark-disbursed` -- looks up the
  position via the protocol and drafts the distribution-disbursement
  record, and returns {:result .. :position-patch ..} for the caller
  to persist."
  [s position-id]
  (let [p (position s position-id)
        seq-n (next-distribution-sequence s (:jurisdiction p))
        result (registry/register-distribution-disbursement position-id (:jurisdiction p) seq-n)]
    {:result result
     :position-patch {:distribution-disbursed? true
                      :distribution-number (get result "distribution_number")}}))

(defn- record-ownership-change!
  "Backend-agnostic `:position/mark-recorded` -- looks up the position
  via the protocol and drafts the ownership-change record, and
  returns {:result .. :position-patch ..} for the caller to persist."
  [s position-id]
  (let [p (position s position-id)
        seq-n (next-ownership-change-sequence s (:jurisdiction p))
        result (registry/register-ownership-change position-id (:jurisdiction p) seq-n)]
    {:result result
     :position-patch {:ownership-change-recorded? true
                      :change-number (get result "change_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (position [_ id] (get-in @a [:positions id]))
  (all-positions [_] (sort-by :id (vals (:positions @a))))
  (beneficial-ownership-of [_ id] (get-in @a [:beneficial-ownership-screens id]))
  (disclosure-of [_ position-id] (get-in @a [:disclosures position-id]))
  (ledger [_] (:ledger @a))
  (distribution-history [_] (:distributions @a))
  (ownership-change-history [_] (:ownership-changes @a))
  (next-distribution-sequence [_ jurisdiction] (get-in @a [:distribution-sequences jurisdiction] 0))
  (next-ownership-change-sequence [_ jurisdiction] (get-in @a [:ownership-change-sequences jurisdiction] 0))
  (position-already-disbursed? [_ position-id] (boolean (get-in @a [:positions position-id :distribution-disbursed?])))
  (position-already-recorded? [_ position-id] (boolean (get-in @a [:positions position-id :ownership-change-recorded?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :position/upsert
      (swap! a update-in [:positions (:id value)] merge value)

      :disclosure/set
      (swap! a assoc-in [:disclosures (first path)] payload)

      :beneficial-ownership/set
      (swap! a assoc-in [:beneficial-ownership-screens (first path)] payload)

      :position/mark-disbursed
      (let [position-id (first path)
            {:keys [result position-patch]} (disburse-distribution! s position-id)
            jurisdiction (:jurisdiction (position s position-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:distribution-sequences jurisdiction] (fnil inc 0))
                       (update-in [:positions position-id] merge position-patch)
                       (update :distributions registry/append result))))
        result)

      :position/mark-recorded
      (let [position-id (first path)
            {:keys [result position-patch]} (record-ownership-change! s position-id)
            jurisdiction (:jurisdiction (position s position-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:ownership-change-sequences jurisdiction] (fnil inc 0))
                       (update-in [:positions position-id] merge position-patch)
                       (update :ownership-changes registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-positions [s positions] (when (seq positions) (swap! a assoc :positions positions)) s))

(defn seed-db
  "A MemStore seeded with the demo position set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :disclosures {} :beneficial-ownership-screens {} :ledger [] :distribution-sequences {}
                           :distributions [] :ownership-change-sequences {} :ownership-changes []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (disclosure/beneficial-ownership payloads, ledger
  facts, distribution/ownership-change records) are stored as EDN
  strings so `langchain.db` doesn't expand them into sub-entities --
  the same convention every sibling actor's store uses."
  {:position/id                              {:db/unique :db.unique/identity}
   :disclosure/position-id                   {:db/unique :db.unique/identity}
   :beneficial-ownership/position-id         {:db/unique :db.unique/identity}
   :ledger/seq                               {:db/unique :db.unique/identity}
   :distribution/seq                         {:db/unique :db.unique/identity}
   :ownership-change/seq                     {:db/unique :db.unique/identity}
   :distribution-sequence/jurisdiction       {:db/unique :db.unique/identity}
   :ownership-change-sequence/jurisdiction   {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- position->tx [{:keys [id subsidiary-name proposed-distribution-amount distributable-reserves
                             beneficial-ownership-verified?
                             distribution-disbursed? ownership-change-recorded?
                             jurisdiction status distribution-number change-number]}]
  (cond-> {:position/id id}
    subsidiary-name                             (assoc :position/subsidiary-name subsidiary-name)
    proposed-distribution-amount                 (assoc :position/proposed-distribution-amount proposed-distribution-amount)
    distributable-reserves                        (assoc :position/distributable-reserves distributable-reserves)
    (some? beneficial-ownership-verified?)         (assoc :position/beneficial-ownership-verified? beneficial-ownership-verified?)
    (some? distribution-disbursed?)                 (assoc :position/distribution-disbursed? distribution-disbursed?)
    (some? ownership-change-recorded?)               (assoc :position/ownership-change-recorded? ownership-change-recorded?)
    jurisdiction                                      (assoc :position/jurisdiction jurisdiction)
    status                                             (assoc :position/status status)
    distribution-number                                 (assoc :position/distribution-number distribution-number)
    change-number                                        (assoc :position/change-number change-number)))

(def ^:private position-pull
  [:position/id :position/subsidiary-name :position/proposed-distribution-amount
   :position/distributable-reserves :position/beneficial-ownership-verified?
   :position/distribution-disbursed? :position/ownership-change-recorded?
   :position/jurisdiction :position/status :position/distribution-number :position/change-number])

(defn- pull->position [m]
  (when (:position/id m)
    {:id (:position/id m) :subsidiary-name (:position/subsidiary-name m)
     :proposed-distribution-amount (:position/proposed-distribution-amount m)
     :distributable-reserves (:position/distributable-reserves m)
     :beneficial-ownership-verified? (boolean (:position/beneficial-ownership-verified? m))
     :distribution-disbursed? (boolean (:position/distribution-disbursed? m))
     :ownership-change-recorded? (boolean (:position/ownership-change-recorded? m))
     :jurisdiction (:position/jurisdiction m) :status (:position/status m)
     :distribution-number (:position/distribution-number m) :change-number (:position/change-number m)}))

(defrecord DatomicStore [conn]
  Store
  (position [_ id]
    (pull->position (d/pull (d/db conn) position-pull [:position/id id])))
  (all-positions [_]
    (->> (d/q '[:find [?id ...] :where [?e :position/id ?id]] (d/db conn))
         (map #(pull->position (d/pull (d/db conn) position-pull [:position/id %])))
         (sort-by :id)))
  (beneficial-ownership-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?k :beneficial-ownership/position-id ?pid] [?k :beneficial-ownership/payload ?p]]
              (d/db conn) id)))
  (disclosure-of [_ position-id]
    (dec* (d/q '[:find ?p . :in $ ?pid
                :where [?a :disclosure/position-id ?pid] [?a :disclosure/payload ?p]]
              (d/db conn) position-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (distribution-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :distribution/seq ?s] [?e :distribution/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (ownership-change-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :ownership-change/seq ?s] [?e :ownership-change/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-distribution-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :distribution-sequence/jurisdiction ?j] [?e :distribution-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-ownership-change-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :ownership-change-sequence/jurisdiction ?j] [?e :ownership-change-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (position-already-disbursed? [s position-id]
    (boolean (:distribution-disbursed? (position s position-id))))
  (position-already-recorded? [s position-id]
    (boolean (:ownership-change-recorded? (position s position-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :position/upsert
      (d/transact! conn [(position->tx value)])

      :disclosure/set
      (d/transact! conn [{:disclosure/position-id (first path) :disclosure/payload (enc payload)}])

      :beneficial-ownership/set
      (d/transact! conn [{:beneficial-ownership/position-id (first path) :beneficial-ownership/payload (enc payload)}])

      :position/mark-disbursed
      (let [position-id (first path)
            {:keys [result position-patch]} (disburse-distribution! s position-id)
            jurisdiction (:jurisdiction (position s position-id))
            next-n (inc (next-distribution-sequence s jurisdiction))]
        (d/transact! conn
                     [(position->tx (assoc position-patch :id position-id))
                      {:distribution-sequence/jurisdiction jurisdiction :distribution-sequence/next next-n}
                      {:distribution/seq (count (distribution-history s)) :distribution/record (enc (get result "record"))}])
        result)

      :position/mark-recorded
      (let [position-id (first path)
            {:keys [result position-patch]} (record-ownership-change! s position-id)
            jurisdiction (:jurisdiction (position s position-id))
            next-n (inc (next-ownership-change-sequence s jurisdiction))]
        (d/transact! conn
                     [(position->tx (assoc position-patch :id position-id))
                      {:ownership-change-sequence/jurisdiction jurisdiction :ownership-change-sequence/next next-n}
                      {:ownership-change/seq (count (ownership-change-history s)) :ownership-change/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-positions [s positions]
    (when (seq positions) (d/transact! conn (mapv position->tx (vals positions)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:positions ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [positions]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-positions s positions))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo position set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
