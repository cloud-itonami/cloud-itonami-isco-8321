(ns motodispatch.store
  "SSoT for the ISCO-08 8321 independent motorcycle courier dispatch
  practice actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md
  Actors section; README's 'Robotics premise' — a dispatch/logistics
  coordination robot performs rider-roster scheduling, delivery-record
  logging and maintenance-order coordination under this
  advisor/governor pair, which never dispatches hardware itself, never
  operates the motorcycle, and never finalizes a route or
  traffic-navigation decision on the rider's behalf). Modeled on
  cloud-itonami-isco-3313's accountingsupport.store /
  cloud-itonami-isco-8322's driving.store.

  Domain:

    client      — a registered dispatch/courier operator organization
                  (:client-id, :name)
    rider       — a registered courier/rider {:rider-id :client-id
                  :name :license-verified? boolean}. A rider/license
                  record must be independently verified and registered
                  before this actor may reference that rider in any
                  proposal.
    motorcycle  — a registered vehicle {:motorcycle-id :client-id
                  :name :max-maintenance-cost number}.
                  `:max-maintenance-cost` is the registered ceiling
                  above which a proposed maintenance order's cost
                  always requires human sign-off (a cost threshold,
                  not a hard block — maintenance procurement is
                  legitimate dispatch/logistics work, just gated above
                  threshold).
    record      — a committed operating record (logged delivery,
                  scheduled dispatch, flagged concern, coordinated
                  maintenance order) — written ONLY via
                  commit-record!.
    ledger      — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (rider [s rider-id])
  (motorcycle [s motorcycle-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-rider! [s r])
  (register-motorcycle! [s m])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (rider [_ rider-id] (get-in @a [:riders rider-id]))
  (motorcycle [_ motorcycle-id] (get-in @a [:motorcycles motorcycle-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-rider! [s r]
    (swap! a assoc-in [:riders (:rider-id r)] r) s)
  (register-motorcycle! [s m]
    (swap! a assoc-in [:motorcycles (:motorcycle-id m)] m) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :riders {} :motorcycles {} :records [] :ledger []}
                                   seed)))))
