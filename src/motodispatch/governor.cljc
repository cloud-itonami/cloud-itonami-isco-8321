(ns motodispatch.governor
  "MotorcycleDispatchGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every dispatch-coordination proposal an advisor may make. The
  governor never dispatches hardware itself, never operates the
  motorcycle, and never lets a proposal finalize a route or
  traffic-navigation decision or override a rider's on-road safety
  judgment — those are always a hard, permanent block regardless of
  confidence or op. This governor gates DISPATCH/LOGISTICS SCHEDULING
  proposals ONLY. Modeled on cloud-itonami-isco-3313's
  accountingsupport.governor / cloud-itonami-isco-8322's
  driving.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance        — the dispatch/courier operator
                                   organization must be registered.
    2. no-actuation              — proposal :effect must be :propose
                                   (the governor never dispatches
                                   hardware and never operates the
                                   motorcycle; it only gates what the
                                   dispatch/logistics robot may log or
                                   schedule).
    3. closed op-allowlist       — :op must be one of
                                   :log-delivery-record,
                                   :schedule-dispatch-operation,
                                   :flag-safety-concern,
                                   :coordinate-maintenance-order. No
                                   other op is ever recognized.
    4. rider provenance +
       license verification      — the proposal must cite a
                                   REGISTERED rider belonging to this
                                   client whose license record has
                                   been independently verified
                                   (`:license-verified?` true) —
                                   dispatch on an unverified rider
                                   record is not authorized
                                   coordination.
    5. route/traffic-navigation
       finalization block         — a proposal that would finalize a
                                   route or traffic-navigation
                                   decision, or override the rider's
                                   on-road safety judgment, is a hard,
                                   permanent block — never
                                   auto-commit-eligible, never
                                   escalatable to an override, always
                                   refused outright. Detected as a
                                   defense-in-depth text scan for full
                                   action PHRASES (never bare nouns
                                   like \"route\"/\"traffic\", which
                                   would false-positive against this
                                   governor's own mock-advisor default
                                   rationale text — see
                                   `scope-excluded-phrases`, and the
                                   `default-mock-advisor-proposals-
                                   never-self-trip` test).
    6. maintenance-order basis    — a `:coordinate-maintenance-order`
                                   proposal must cite a REGISTERED
                                   motorcycle belonging to this
                                   client.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    7. :op :flag-safety-concern (vehicle-defect / road-hazard /
                                   rider-fatigue concerns always reach
                                   a human, never auto-resolved).
    8. maintenance-order cost above the motorcycle's registered
                                   `:max-maintenance-cost` ceiling.
    9. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [motodispatch.store :as store]))

(def confidence-floor 0.6)

(def ^:private allowed-ops
  #{:log-delivery-record :schedule-dispatch-operation
    :flag-safety-concern :coordinate-maintenance-order})

(def ^:private always-escalate-ops #{:flag-safety-concern})

;; Full action PHRASES only — never bare nouns such as "route" or
;; "traffic" — so this governor's own mock-advisor default rationale
;; text (e.g. "proposed :schedule-dispatch-operation for rider ...")
;; can never accidentally self-trip this rule. This is the fix for a
;; known self-tripping bug pattern: a bare-noun term list matches
;; inside the mock advisor's own default rationale text and
;; false-blocks routine proposals. See
;; `default-mock-advisor-proposals-never-self-trip` in
;; test/motodispatch/governor_test.clj.
(def ^:private scope-excluded-phrases
  ["finalize the route decision"
   "finalize a route decision"
   "finalize the traffic-navigation decision"
   "finalize a traffic-navigation decision"
   "override the rider's route judgment"
   "override the rider's on-road safety judgment"
   "override rider on-road safety judgment"
   "dispatch the vehicle directly"
   "operate the motorcycle directly"])

(defn- scope-excluded? [proposal]
  (let [text (str/lower (str (:rationale proposal) " " (:detail proposal)))]
    (boolean (some #(str/includes? text %) scope-excluded-phrases))))

(defn- hard-violations [{:keys [request proposal]} client-record rider-record motorcycle-record]
  (let [{:keys [op effect]} proposal
        maintenance? (= :coordinate-maintenance-order op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not (contains? allowed-ops op))
      (conj {:rule :op-not-allowlisted
             :detail (str "op " (pr-str op) " はクローズド allowlist 外（dispatch/logistics scheduling 以外は提案不可）")})

      (not= :propose effect)
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor はハードウェア/車両を直接起動しない）"})

      (nil? rider-record)
      (conj {:rule :unregistered-rider :detail "未登録 rider への提案は不可"})

      (and rider-record (not= (:client-id rider-record) (:client-id request)))
      (conj {:rule :rider-wrong-client :detail "rider が別 client のもの"})

      (and rider-record (not (:license-verified? rider-record)))
      (conj {:rule :rider-license-unverified
             :detail "rider/license record が独立に検証・登録される前の提案は不可"})

      (scope-excluded? proposal)
      (conj {:rule :route-traffic-finalization-blocked
             :detail "route/traffic-navigation の確定判断、または rider の路上安全判断の上書きは恒久的に禁止（override不可）"})

      (and maintenance? (nil? motorcycle-record))
      (conj {:rule :unknown-motorcycle :detail "未登録 motorcycle への整備発注提案は不可"})

      (and maintenance? motorcycle-record (not= (:client-id motorcycle-record) (:client-id request)))
      (conj {:rule :motorcycle-wrong-client :detail "motorcycle が別 client のもの"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `motodispatch.store/Store`. Pure — never
  mutates the store, never dispatches the robot, never operates the
  motorcycle."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        rider-record (some->> (:rider-id proposal) (store/rider store))
        motorcycle-record (some->> (:motorcycle-id proposal) (store/motorcycle store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record rider-record motorcycle-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))
        cost-over? (and (= :coordinate-maintenance-order (:op proposal))
                        motorcycle-record
                        (number? (:cost proposal))
                        (> (:cost proposal) (:max-maintenance-cost motorcycle-record)))]
    {:ok? (and (not hard?) (not low?) (not always-risky?) (not cost-over?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky? cost-over?))}))
