(ns holdco.registry
  "Pure-function distribution-disbursement + ownership-change-
  recording record construction -- an append-only holding-company
  book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a distribution-disbursement
  or ownership-change reference number -- every holding-company/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `holdco.facts` uses.

  `distribution-amount-exceeds-distributable-reserves?` is the NINTH
  instance of this fleet's MAXIMUM-ceiling check family (`facility`/
  `school`/`card`/`recovery`/`care`/`navigator`/`advertising`/
  `nursing` established the first eight), applying the same lo-bound-
  absent/hi-bound-only comparison to a subsidiary equity position's
  own recorded proposed distribution amount against its own recorded
  distributable-reserves ceiling -- a direct, natural mapping onto
  real corporate distribution law (e.g. Delaware GCL §170's surplus/
  net-profits test, UK Companies Act 2006 Part 23's distributable-
  profits test, Japan's Companies Act §461 distributable-amount
  rules, Germany's AktG §57-58 Kapitalerhaltung): a holding company
  may not legally disburse a distribution exceeding its own
  distributable reserves.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real equity-register system. It builds the RECORD a
  holding company would keep, not the act of disbursing the
  distribution or recording the ownership change itself (that is
  `holdco.operation`'s `:actuation/disburse-distribution`/`:actuation/
  record-ownership-change`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  holding company's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn distribution-amount-exceeds-distributable-reserves?
  "Does `position`'s own recorded `:proposed-distribution-amount`
  exceed its own recorded `:distributable-reserves` ceiling? A pure
  ground-truth check against the position's own permanent fields --
  no upstream comparison needed. The NINTH instance of this fleet's
  MAXIMUM-ceiling check family (see ns docstring)."
  [{:keys [proposed-distribution-amount distributable-reserves]}]
  (and (number? proposed-distribution-amount) (number? distributable-reserves)
       (> proposed-distribution-amount distributable-reserves)))

(defn register-distribution-disbursement
  "Validate + construct the DISTRIBUTION-DISBURSEMENT registration
  DRAFT -- the holding company's own act of disbursing a real
  dividend/distribution. Pure function -- does not touch any real
  equity-register system; it builds the RECORD a holding company
  would keep. `holdco.governor` independently re-verifies the
  position's own distributable-reserves ground truth and blocks a
  double-disbursement for the same position, before this is ever
  allowed to commit."
  [position-id jurisdiction sequence]
  (when-not (and position-id (not= position-id ""))
    (throw (ex-info "distribution-disbursement: position_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "distribution-disbursement: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "distribution-disbursement: sequence must be >= 0" {})))
  (let [distribution-number (str (str/upper-case jurisdiction) "-DIS-" (zero-pad sequence 6))
        record {"record_id" distribution-number
                "kind" "distribution-disbursement-draft"
                "position_id" position-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "distribution_number" distribution-number
     "certificate" (unsigned-certificate "DistributionDisbursement" distribution-number distribution-number)}))

(defn register-ownership-change
  "Validate + construct the OWNERSHIP-CHANGE registration DRAFT -- the
  holding company's own act of recording a real ownership-structure
  change. Pure function -- does not touch any real equity-register
  system; it builds the RECORD a holding company would keep.
  `holdco.governor` independently re-verifies the position's own
  evidence checklist and blocks a double-recording for the same
  position, before this is ever allowed to commit."
  [position-id jurisdiction sequence]
  (when-not (and position-id (not= position-id ""))
    (throw (ex-info "ownership-change: position_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "ownership-change: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "ownership-change: sequence must be >= 0" {})))
  (let [change-number (str (str/upper-case jurisdiction) "-OWN-" (zero-pad sequence 6))
        record {"record_id" change-number
                "kind" "ownership-change-draft"
                "position_id" position-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "change_number" change-number
     "certificate" (unsigned-certificate "OwnershipChange" change-number change-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
