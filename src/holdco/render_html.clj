(ns holdco.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`holdco.operation` -> `holdco.governor` -> `holdco.store`) through a
  scenario adapted from this repo's own `holdco.sim` demo driver
  (`clojure -M:dev:run`, confirmed to run correctly against the real
  seeded position directory before this file was written -- this
  repo's own sim driver uses ids that DO match `holdco.store/demo-data`
  [position-1..position-5], unlike `cloud-itonami-isic-851`'s
  `schoolops.sim` at the time it was first written), trimmed to a
  representative subset (one full clean lifecycle through both
  actuation events, one hold set up mid-lifecycle, and three distinct
  HARD-hold reasons) and rendered deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verified by diffing two consecutive
  runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [holdco.store :as store]
            [holdco.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :holdco-administrator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: position-1 clears intake (auto-commit -- the
  ONLY member of phase 3's `:auto` set, no direct capital risk yet),
  then walks the full clean lifecycle through disclosure verification,
  beneficial-ownership screening, and both real corporate actuations --
  disbursing a distribution and recording an ownership-structure
  change -- every one of which ALWAYS escalates for human approval
  even when governor-clean (per `holdco.phase`, neither actuation is
  ever in any phase's `:auto` set); position-2 HARD-holds a disclosure
  proposal with no official jurisdiction spec-basis; position-3 clears
  disclosure so its own recorded proposed-distribution-amount
  (3,000,000) can be independently recomputed against its own recorded
  distributable-reserves ceiling (2,000,000) and HARD-holds on the
  overrun; position-4 (seeded `:beneficial-ownership-verified? false`)
  HARD-holds its own screening on unresolved beneficial ownership.
  Every HARD hold never reaches a human. Returns the resulting store --
  every field read by `render` below is real governor/store output,
  not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "p1-intake" {:op :position/intake :subject "position-1"
                               :patch {:id "position-1" :subsidiary-name "Sato Holdings K.K."}})

    (exec! actor "p1-disclosure" {:op :disclosure/verify :subject "position-1"})
    (approve! actor "p1-disclosure")

    (exec! actor "p1-screen" {:op :beneficial-ownership/screen :subject "position-1"})
    (approve! actor "p1-screen")

    (exec! actor "p1-disburse" {:op :actuation/disburse-distribution :subject "position-1"})
    (approve! actor "p1-disburse")

    (exec! actor "p1-record" {:op :actuation/record-ownership-change :subject "position-1"})
    (approve! actor "p1-record")

    (exec! actor "p2-disclosure" {:op :disclosure/verify :subject "position-2" :no-spec? true})

    (exec! actor "p3-disclosure" {:op :disclosure/verify :subject "position-3"})
    (approve! actor "p3-disclosure")
    (exec! actor "p3-disburse" {:op :actuation/disburse-distribution :subject "position-3"})

    (exec! actor "p4-screen" {:op :beneficial-ownership/screen :subject "position-4"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger position-id]
  (last (filter #(= (:subject %) position-id) ledger)))

(defn- status-cell [ledger position-id]
  (let [f (last-fact-for ledger position-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- position-row [ledger {:keys [id subsidiary-name jurisdiction proposed-distribution-amount
                                    distributable-reserves beneficial-ownership-verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc subsidiary-name) (esc jurisdiction)
          (esc proposed-distribution-amount) (esc distributable-reserves)
          (if beneficial-ownership-verified? "<span class=\"ok\">verified</span>" "<span class=\"warn\">unverified</span>")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  ;; Unlike the sibling repos this template was proven on (realty/
  ;; schoolops), holdco's own op keywords are namespace-qualified
  ;; (:disclosure/verify, :beneficial-ownership/screen,
  ;; :actuation/disburse-distribution, ...) -- plain `(name op)` would
  ;; silently drop the namespace and collapse distinct ops to
  ;; ambiguous short names ("verify", "screen"), so this render the
  ;; FULL qualified keyword instead.
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (if op (subs (str op) 1) "n-a")) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Ops`
  ;; table, `holdco.governor`/`holdco.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:position/intake</code></td><td><span class=\"ok\">auto-commit when clean at phase 3 -- no direct capital risk</span></td></tr>"
   "        <tr><td><code>:disclosure/verify</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis must cite an official jurisdiction source</span></td></tr>"
   "        <tr><td><code>:beneficial-ownership/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; HARD-holds un-overridably on an unresolved status</span></td></tr>"
   "        <tr><td><code>:actuation/disburse-distribution</code></td><td><span class=\"warn\">ALWAYS human approval, no phase ever auto-commits &middot; distributable-reserves independently recomputed &middot; double-disbursement blocked</span></td></tr>"
   "        <tr><td><code>:actuation/record-ownership-change</code></td><td><span class=\"warn\">ALWAYS human approval, no phase ever auto-commits &middot; double-recording blocked</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        positions (->> (store/all-positions db)
                       (filter #(#{"position-1" "position-2" "position-3" "position-4" "position-5"} (:id %)))
                       (sort-by :id))
        position-rows (str/join "\n" (map (partial position-row ledger) positions))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-6420 &middot; activities of holding companies</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Activities of holding companies (ISIC 6420) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · distribution disbursement and ownership-change recording always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Positions</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>holdco.store</code> via <code>holdco.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Position</th><th>Subsidiary</th><th>Jurisdiction</th><th>Proposed distribution / Distributable reserves</th><th>Beneficial ownership</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     position-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Holding Structure Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Distribution amounts are independently recomputed against each position's own distributable reserves, never trusted from the proposal; beneficial-ownership verification is checked unconditionally, on every op that touches a position.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/distribution-history db)) "distribution disbursements,"
             (count (store/ownership-change-history db)) "ownership-change recordings )")))
