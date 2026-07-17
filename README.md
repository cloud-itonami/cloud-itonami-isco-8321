# cloud-itonami-isco-8321

Open Occupation Blueprint for **ISCO-08 8321**: Motorcycle Drivers.

This repository designs a forkable OSS business for an independent motorcycle courier/delivery dispatch practice: a dispatch/logistics coordination robot manages rider-roster scheduling, delivery-record logging and maintenance-order coordination under a governor-gated actor, so the practice keeps its own dispatch records instead of renting a closed courier-dispatch SaaS.

**Maturity: `:implemented`.** `src/motodispatch/` implements the
`MotorcycleDispatchActor` as a `langgraph.graph/state-graph`
(`motodispatch.actor`) wired to a `Motorcycle Dispatch Advisor`
(`motodispatch.advisor`) and an independent `MotorcycleDispatchGovernor`
(`motodispatch.governor`), following the itonami actor pattern
(ADR-2607011000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 22 tests / 49 assertions green (`clojure -M:test`).

HARD invariants (always hold, never overridable): dispatch/courier
operator (client) provenance, no-actuation (`:effect` must be
`:propose`), a **closed op-allowlist** (`:log-delivery-record`,
`:schedule-dispatch-operation`, `:flag-safety-concern`,
`:coordinate-maintenance-order` — no other op is ever recognized), a
registered and **independently license-verified** rider basis for any
proposal, and — the road-vehicle-operation safety floor — any proposal
that would directly **finalize a route/traffic-navigation decision or
override a rider's on-road safety judgment**, which is a hard,
permanent block regardless of confidence or op. This actor coordinates
dispatch/logistics scheduling **only**; it never operates the
motorcycle.

Always-escalate (human sign-off regardless of confidence, mapping this
repo's Trust Controls in [`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (vehicle-defect / road-hazard / rider-fatigue
concerns always reach a human, never auto-resolved), and
`:coordinate-maintenance-order` proposals above the registered
maintenance-cost ceiling.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a dispatch/logistics coordination robot performs rider-roster scheduling, delivery-record logging and maintenance-order coordination under an actor that proposes actions and an independent **Motorcycle Dispatch Governor** that gates them. The governor never dispatches hardware itself, and this actor never operates the motorcycle; `:high`/`:safety-critical` actions (such as a flagged safety concern, or a maintenance order above the registered cost ceiling) require human sign-off.

This actor coordinates **dispatch/logistics scheduling only** — it never finalizes a route or traffic-navigation decision, and it never overrides a rider's on-road safety judgment. Both are a permanent, non-overridable block, enforced independently of the advisor's confidence and independently of which op was proposed.

## Core Contract

```text
delivery/dispatch request + rider roster + vehicle maintenance log
        |
        v
Motorcycle Dispatch Advisor -> Motorcycle Dispatch Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
operate the motorcycle, finalize a route/traffic-navigation decision,
override a rider's on-road safety judgment, suppress an operating
record, or disclose sensitive data without governor approval and audit
evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8321`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
