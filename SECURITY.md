# Security Policy

This project handles motorcycle courier/delivery dispatch operating
workflows. Treat vulnerabilities as potentially high impact even when the
demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real rider, client or operator data exposure
- authorization bypass
- Motorcycle Dispatch Governor bypass
- route/traffic-navigation finalization or on-road safety-judgment override bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
gftdcojp organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on rider/client data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real rider/client/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
