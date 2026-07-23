(ns holdco.facts
  "Per-jurisdiction holding-company/corporate-distribution regulatory
  catalog -- the G2-style spec-basis table the Holding Structure
  Governor checks every `:disclosure/verify` proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  distribution/beneficial-ownership framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official corporate-
  law/beneficial-ownership-register authority (see `:provenance`);
  they are a STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the ownership-
  structure-disclosure/beneficial-ownership-verification/distributable-
  reserves-certification/distribution-authorization evidence set this
  blueprint's own Offer names; `:legal-basis` / `:owner-authority` /
  `:provenance` are the G2 citation the governor requires before any
  `:actuation/disburse-distribution`/`:actuation/record-ownership-
  change` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "法務省 (Ministry of Justice)"
          :legal-basis "会社法 (Companies Act, Act No. 86 of 2005) 第461条 (剰余金の分配可能額)"
          :national-spec "剰余金分配可能額規制および株主名簿・実質的支配者情報の開示義務"
          :provenance "https://www.moj.go.jp/MINJI/minji06_00023.html"
          :required-evidence ["株主構成開示記録 (ownership-structure-disclosure-record)"
                              "実質的支配者確認記録 (beneficial-ownership-verification-record)"
                              "分配可能額証明記録 (distributable-reserves-certification-record)"
                              "分配承認記録 (distribution-authorization-record)"]}
   "USA" {:name "United States"
          :owner-authority "Delaware Division of Corporations / FinCEN"
          :legal-basis "Delaware General Corporation Law §170 (dividends) / Corporate Transparency Act, 31 U.S.C. §5336 (beneficial ownership reporting)"
          :national-spec "Surplus/net-profits distribution limits and FinCEN beneficial-ownership-information reporting requirements"
          :provenance "https://www.fincen.gov/boi"
          :required-evidence ["Ownership-structure disclosure record"
                              "Beneficial-ownership verification record"
                              "Distributable-reserves certification record"
                              "Distribution authorization record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Companies House"
          :legal-basis "Companies Act 2006, Part 23 (distributions) / Small Business, Enterprise and Employment Act 2015 (PSC register)"
          :national-spec "Distributable-profits test and Persons with Significant Control (PSC) register requirements"
          :provenance "https://www.gov.uk/guidance/people-with-significant-control-pscs"
          :required-evidence ["Ownership-structure disclosure record"
                              "Beneficial-ownership verification record"
                              "Distributable-reserves certification record"
                              "Distribution authorization record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesanzeiger Verlag (Transparenzregister)"
          :legal-basis "Aktiengesetz (AktG) §57-58 (Kapitalerhaltung) / Geldwäschegesetz (GwG) §20 (wirtschaftlich Berechtigte)"
          :national-spec "Kapitalerhaltungsvorschriften für Ausschüttungen und Transparenzregister-Meldepflicht für wirtschaftlich Berechtigte"
          :provenance "https://www.transparenzregister.de/"
          :required-evidence ["Beteiligungsstrukturoffenlegungsprotokoll (ownership-structure-disclosure-record)"
                              "Nachweis wirtschaftlich Berechtigter (beneficial-ownership-verification-record)"
                              "Ausschüttungsfähigkeitsnachweis (distributable-reserves-certification-record)"
                              "Ausschüttungsgenehmigungsprotokoll (distribution-authorization-record)"]}
   "SGP" {:name "Singapore"
          :owner-authority "Accounting and Corporate Regulatory Authority (ACRA)"
          :legal-basis "Companies Act 1967 §403 (dividends payable from profits only) / Part 11A §386AF & §386AN (register of controllers / central register of registrable controllers)"
          :national-spec "Profits-only restriction on dividend/distribution payments and Register of Registrable Controllers (RORC) beneficial-ownership disclosure and central-filing requirement for companies and LLPs"
          :provenance "https://www.acra.gov.sg/compliance/register-of-registrable-controllers/"
          :required-evidence ["Ownership-structure disclosure record"
                              "Beneficial-ownership verification record"
                              "Distributable-reserves certification record"
                              "Distribution authorization record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to disburse a
  distribution or record an ownership-structure change on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-6420 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `holdco.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
