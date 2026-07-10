(ns holdco.corporate-intel
  "Optional integration with cloud-itonami-isic-8291's :disclosure/
  ownership-chain op (ADR-2607110400 addendum 4) -- cross-references a
  subsidiary's beneficial-ownership chain against 8291's sourced
  relationship-graph data. Calls through 8291's OWN DisclosureGovernor --
  no bypass from this side either. A real 8291 hit (a flagged owner)
  escalates for 8291's own human reviewer first, so this namespace never
  peeks at Dossier-LLM's un-vetted draft proposal to get an early answer."
  (:require [langgraph.graph :as g]
            [dossier.store :as dstore]
            [dossier.operation :as dop]))

(def default-tenant
  "This blueprint's own tenant id under an 8291 :tier/graph contract."
  "cloud-itonami-isic-6420")

(defn demo-store
  "An 8291 MemStore seeded with 8291's own demo data, PLUS a :tier/graph
  contract for THIS blueprint's tenant (ownership-chain requires that
  tier). Replaces 8291's demo tenant-acme/tenant-basic/tenant-graph
  contracts entirely -- this is 6420's OWN isolated offline view."
  []
  (-> (dstore/seed-db)
      (dstore/with-contracts
       {default-tenant {:tenant default-tenant :tier :tier/graph
                         :active? true :purpose :beneficial-ownership-verification}})))

(defn build
  "Compiles an 8291 OperationActor bound to `store` (default: `demo-store`)."
  ([] (build (demo-store)))
  ([store] (dop/build store)))

(defn ownership-chain
  "Runs :disclosure/ownership-chain for `subsidiary-name`. Returns one of:
    {:company-id .. :has-sourced-ownership-data? bool :owners [..]}  -- a
      governor-approved answer (disposition :commit).
    {:pending-human-review? true :reason kw}  -- 8291 itself escalated a
      potential hit to ITS OWN human reviewer; treat as inconclusive.
    {:held? true :reason [kw ..]}  -- the query itself was rejected by
      8291's DisclosureGovernor (e.g. this tenant's contract is missing/
      inactive/wrong tier) -- a configuration problem, never treated as
      confirming clean ownership.

  opts:
    :actor     -- a pre-built 8291 OperationActor (default: fresh `build`)
    :tenant    -- tenant id to query under (default: `default-tenant`)
    :thread-id -- langgraph-clj thread id (default: derived from the name)"
  ([subsidiary-name] (ownership-chain subsidiary-name {}))
  ([subsidiary-name {:keys [actor tenant thread-id]
                      :or   {actor (build) tenant default-tenant}}]
   (let [thread-id (or thread-id (str "ownchain-" tenant "-" subsidiary-name))
         res (g/run* actor
                     {:request {:op :disclosure/ownership-chain :subject tenant
                                :company-name subsidiary-name}
                      :context {:actor-id default-tenant :actor-role :client :tenant tenant}}
                     {:thread-id thread-id})]
     (case (get-in res [:state :disposition])
       :commit    (get-in res [:state :record :value])
       :escalate  {:pending-human-review? true
                   :reason (-> res :state :audit last :reason)}
       :hold      {:held? true
                   :reason (-> res :state :audit last :basis)}
       {:held? true :reason [:corporate-intel-actor-error]}))))
