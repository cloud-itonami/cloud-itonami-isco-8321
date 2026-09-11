(ns motodispatch.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [motodispatch.store :as store]
            [motodispatch.governor :as governor]
            [motodispatch.advisor :as advisor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Courier Dispatch"})
    (store/register-rider! st {:rider-id "R-1" :client-id "client-1"
                               :name "rider-042" :license-verified? true})
    (store/register-motorcycle! st {:motorcycle-id "M-1" :client-id "client-1"
                                    :name "moto-042" :max-maintenance-cost 500})
    st))

(defn- log-op []
  {:op :log-delivery-record :effect :propose :rider-id "R-1"
   :detail "delivered package #4471" :confidence 0.9 :stake :low})

(defn- schedule-op []
  {:op :schedule-dispatch-operation :effect :propose :rider-id "R-1"
   :detail "assign rider to zone-3 roster shift" :confidence 0.9 :stake :low})

(defn- maintenance-op [cost]
  {:op :coordinate-maintenance-order :effect :propose :rider-id "R-1"
   :motorcycle-id "M-1" :cost cost :confidence 0.9 :stake :low})

(defn- safety-op [concern-type]
  {:op :flag-safety-concern :effect :propose :rider-id "R-1"
   :concern-type concern-type :detail "brake pads worn" :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-valid-log-delivery-record
  (let [st (fresh-store)
        v (governor/check req {} (log-op) st)]
    (is (:ok? v))))

(deftest ok-valid-schedule-dispatch-operation
  (let [st (fresh-store)
        v (governor/check req {} (schedule-op) st)]
    (is (:ok? v))))

(deftest ok-maintenance-order-at-exact-cost-ceiling
  (testing "the maintenance-cost ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (maintenance-op 500) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (log-op) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-op-not-allowlisted
  (testing "closed op-allowlist enforced — no op outside the four dispatch/logistics ops is ever recognized"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (log-op) :op :approve-direct-dispatch) st)]
      (is (:hard? v))
      (is (some #(= :op-not-allowlisted (:rule %)) (:violations v))))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-rider
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :rider-id "R-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unregistered-rider (:rule %)) (:violations v)))))

(deftest hard-on-foreign-rider
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (log-op) st)]
      (is (:hard? v))
      (is (some #(= :rider-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unverified-rider-license
  (testing "rider/license record must be independently verified/registered before any action"
    (let [st (fresh-store)]
      (store/register-rider! st {:rider-id "R-2" :client-id "client-1"
                                 :name "rider-unverified" :license-verified? false})
      (let [v (governor/check req {} (assoc (log-op) :rider-id "R-2") st)]
        (is (:hard? v))
        (is (some #(= :rider-license-unverified (:rule %)) (:violations v)))))))

(deftest hard-on-route-finalization-phrase-in-rationale
  (testing "a proposal to directly finalize a route/traffic-navigation decision is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {}
                            (assoc (schedule-op) :confidence 0.99
                                   :rationale "will finalize the route decision for this delivery")
                            st)]
      (is (:hard? v))
      (is (some #(= :route-traffic-finalization-blocked (:rule %)) (:violations v))))))

(deftest hard-on-override-rider-safety-judgment-phrase-in-detail
  (testing "a proposal to override a rider's on-road safety judgment is a hard, permanent block"
    (let [st (fresh-store)
          v (governor/check req {}
                            (assoc (schedule-op) :confidence 0.99
                                   :detail "override the rider's on-road safety judgment and proceed")
                            st)]
      (is (:hard? v))
      (is (some #(= :route-traffic-finalization-blocked (:rule %)) (:violations v))))))

(deftest hard-on-unknown-motorcycle-for-maintenance-order
  (let [st (fresh-store)
        v (governor/check req {} (assoc (maintenance-op 100) :motorcycle-id "M-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-motorcycle (:rule %)) (:violations v)))))

(deftest hard-on-motorcycle-wrong-client
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (store/register-rider! st {:rider-id "R-3" :client-id "client-2"
                               :name "rider-other" :license-verified? true})
    (let [v (governor/check {:client-id "client-2"} {}
                            (assoc (maintenance-op 100) :rider-id "R-3") st)]
      (is (:hard? v))
      (is (some #(= :motorcycle-wrong-client (:rule %)) (:violations v))))))

(deftest always-escalates-flag-safety-concern-even-at-high-confidence
  (testing "vehicle-defect / road-hazard / rider-fatigue concerns always reach a human"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (safety-op :vehicle-defect) :confidence 0.99) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-maintenance-order-above-cost-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (maintenance-op 501) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (log-op) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip
  (testing "the governor's scope-exclusion phrase check never false-positives against
   this actor's own default mock-advisor rationale text (known self-tripping bug
   pattern: a bare-noun term list like \"route\"/\"traffic\" matches inside the
   mock advisor's own default rationale text and false-blocks routine proposals;
   this governor's phrase list is full finalize/override ACTIONS instead)"
    (let [st (fresh-store)
          mock (advisor/mock-advisor)
          base-request {:client-id "client-1" :rider-id "R-1" :motorcycle-id "M-1" :cost 100}]
      (doseq [op [:log-delivery-record :schedule-dispatch-operation
                  :flag-safety-concern :coordinate-maintenance-order]]
        (let [proposal (advisor/-advise mock st (assoc base-request :op op :stake :low))
              v (governor/check req {} proposal st)]
          (is (not (some #(= :route-traffic-finalization-blocked (:rule %)) (:violations v)))
              (str "op " op " default rationale unexpectedly self-tripped scope-exclusion: "
                   (pr-str (:rationale proposal)))))))))
