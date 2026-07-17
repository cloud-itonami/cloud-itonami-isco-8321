(ns motodispatch.advisor
  "MotorcycleDispatchAdvisor — the advisor named in this repository's
  README, proposing a dispatch-coordination operation (log a
  delivery/incident record, schedule a rider-roster/route-assignment,
  flag a safety concern, coordinate a maintenance order) from a
  delivery/dispatch request, rider roster and vehicle maintenance log.
  Swappable mock/llm; the advisor ONLY proposes —
  `motodispatch.governor` checks rider provenance/verification and the
  maintenance-cost ceiling independently, always blocks any
  route/traffic-navigation finalization or override of the rider's
  on-road safety judgment, and always escalates safety concerns. This
  advisor coordinates DISPATCH/LOGISTICS SCHEDULING ONLY — it never
  operates the motorcycle. Modeled on cloud-itonami-isco-3313's
  advisor / cloud-itonami-isco-8322's driving.advisor.

  A proposal: {:op :log-delivery-record|:schedule-dispatch-operation|:flag-safety-concern|:coordinate-maintenance-order
               :effect :propose :rider-id str :motorcycle-id str?
               :cost number? :concern-type kw? :detail str? :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake rider-id motorcycle-id cost concern-type detail] :as request}]
  {:op op
   :effect :propose
   :rider-id rider-id
   :motorcycle-id motorcycle-id
   :cost cost
   :concern-type concern-type
   :detail detail
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for rider " (:rider-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a motorcycle-courier dispatch advisor. Given a request,
   propose an :op from the closed set :log-delivery-record,
   :schedule-dispatch-operation, :flag-safety-concern,
   :coordinate-maintenance-order, plus the :rider-id and any
   op-specific fields (:motorcycle-id, :cost, :concern-type, :detail),
   an honest :confidence and a :stake. You coordinate dispatch/
   logistics scheduling ONLY — never propose operating the
   motorcycle, never propose finalizing a route or traffic-navigation
   decision, and never propose overriding a rider's on-road safety
   judgment; the governor treats any such proposal as a hard,
   permanent block. Always flag safety concerns rather than resolve
   them yourself, and always leave maintenance orders above the
   registered cost ceiling for human sign-off.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
