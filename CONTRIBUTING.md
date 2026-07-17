# Contributing

`cloud-itonami-isco-8321` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:test
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real rider, client data, credentials or operating documents.
- Keep production writes and disclosures behind Motorcycle Dispatch Governor.
- Treat this occupation's workflows as high-risk: add tests for permission,
  purpose, safety and audit logging.
- Never propose or implement a change that lets this actor finalize a
  route/traffic-navigation decision or override a rider's on-road safety
  judgment — that boundary is permanent and non-overridable.
- This actor coordinates dispatch/logistics scheduling only; do not add an
  op that would let it operate the motorcycle directly.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
