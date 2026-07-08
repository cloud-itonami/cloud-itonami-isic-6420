(ns holdco.holdcoadvisor
  "HoldCo-LLM client -- the *contained intelligence node* for the
  holding-company actor.

  It normalizes position intake, drafts a per-jurisdiction ownership-
  structure disclosure checklist, screens positions for an unverified
  beneficial-ownership status, drafts the distribution-disbursement
  action, and drafts the ownership-change-recording action. CRITICAL:
  it is a smart-but-untrusted advisor. It returns a *proposal* (with a
  rationale + the fields it cited), never a committed record or a
  real distribution disbursement/ownership-change recording. Every
  output is censored downstream by `holdco.governor` before anything
  touches the SSoT, and `:actuation/disburse-distribution`/
  `:actuation/record-ownership-change` proposals NEVER auto-commit at
  any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/disburse-distribution | :actuation/record-ownership-change | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [holdco.facts :as facts]
            [holdco.registry :as registry]
            [holdco.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the position, subsidiary or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "持株記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :position/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-disclosure
  "Per-jurisdiction ownership-structure disclosure evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `holdco.facts` -- the Holding Structure Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [p (store/position db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction p))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "holdco.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :disclosure/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :disclosure/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-beneficial-ownership
  "Beneficial-ownership-verification screening draft.
  `:beneficial-ownership-verified?` on the position record injects
  the failure mode: the Holding Structure Governor must HOLD, un-
  overridably, on any unverified status."
  [db {:keys [subject]}]
  (let [p (store/position db subject)]
    (cond
      (nil? p)
      {:summary "対象持株記録が見つかりません" :rationale "no position record"
       :cites [] :effect :beneficial-ownership/set :value {:position-id subject :beneficial-ownership-verified? nil}
       :stake nil :confidence 0.0}

      (false? (:beneficial-ownership-verified? p))
      {:summary    (str (:subsidiary-name p) ": 実質的支配者情報が未確認")
       :rationale  "スクリーニングが未確認状態を検出。人手確認とホールドが必須。"
       :cites      [:beneficial-ownership-check]
       :effect     :beneficial-ownership/set
       :value      {:position-id subject :beneficial-ownership-verified? false}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:subsidiary-name p) ": 実質的支配者情報は確認済み")
       :rationale  "実質的支配者確認完了。"
       :cites      [:beneficial-ownership-check]
       :effect     :beneficial-ownership/set
       :value      {:position-id subject :beneficial-ownership-verified? true}
       :stake      nil
       :confidence 0.9})))

(defn- propose-distribution-disbursement
  "Draft the actual DISTRIBUTION-DISBURSEMENT action -- disbursing a
  real dividend/distribution. ALWAYS `:stake :actuation/disburse-
  distribution` -- this is a REAL-WORLD act, never a draft the actor
  may auto-run. See README `Actuation`: no phase ever adds this op to
  a phase's `:auto` set (`holdco.phase`); the governor also always
  escalates on `:actuation/disburse-distribution`. Two independent
  layers agree, deliberately."
  [db {:keys [subject]}]
  (let [p (store/position db subject)]
    {:summary    (str subject " 向け分配実施提案"
                      (when p (str " (subsidiary=" (:subsidiary-name p) ")")))
     :rationale  (if p
                   (str "proposed-distribution-amount=" (:proposed-distribution-amount p)
                        " distributable-reserves=" (:distributable-reserves p))
                   "持株記録が見つかりません")
     :cites      (if p [subject] [])
     :effect     :position/mark-disbursed
     :value      {:position-id subject}
     :stake      :actuation/disburse-distribution
     :confidence (if (and p (not (registry/distribution-amount-exceeds-distributable-reserves? p))) 0.9 0.3)}))

(defn- propose-ownership-change
  "Draft the actual OWNERSHIP-CHANGE action -- recording a real
  ownership-structure change. ALWAYS `:stake :actuation/record-
  ownership-change` -- this is a REAL-WORLD act, never a draft the
  actor may auto-run. See README `Actuation`: no phase ever adds this
  op to a phase's `:auto` set (`holdco.phase`); the governor also
  always escalates on `:actuation/record-ownership-change`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [p (store/position db subject)]
    {:summary    (str subject " 向け持株構成変更記録提案"
                      (when p (str " (subsidiary=" (:subsidiary-name p) ")")))
     :rationale  (if p
                   (str "beneficial-ownership-verified?=" (:beneficial-ownership-verified? p))
                   "持株記録が見つかりません")
     :cites      (if p [subject] [])
     :effect     :position/mark-recorded
     :value      {:position-id subject}
     :stake      :actuation/record-ownership-change
     :confidence (if (and p (:beneficial-ownership-verified? p)) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :position/intake                      (normalize-intake db request)
    :disclosure/verify                    (verify-disclosure db request)
    :beneficial-ownership/screen          (screen-beneficial-ownership db request)
    :actuation/disburse-distribution      (propose-distribution-disbursement db request)
    :actuation/record-ownership-change    (propose-ownership-change db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは持株会社管理事業の分配実施・持株構成変更記録エージェントの"
       "助言者です。与えられた事実のみに基づき、提案を1つだけEDNマップで"
       "返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:position/upsert|:disclosure/set|:beneficial-ownership/set|"
       ":position/mark-disbursed|:position/mark-recorded) "
       ":stake(:actuation/disburse-distribution か :actuation/record-ownership-change か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :disclosure/verify                    {:position (store/position st subject)}
    :beneficial-ownership/screen          {:position (store/position st subject)}
    :actuation/disburse-distribution      {:position (store/position st subject)}
    :actuation/record-ownership-change    {:position (store/position st subject)}
    {:position (store/position st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Holding Structure Governor
  escalates/holds -- an LLM hiccup can never auto-disburse a
  distribution or auto-record an ownership change."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :holdcoadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
