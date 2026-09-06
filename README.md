# Ledger Match

Ledger Match is a generic reconciliation dashboard for comparing a store's order export with a payment processor export. It keeps matching deterministic and uses an optional server-side LLM only to explain an already-classified discrepancy.

## Local setup

Requirements: Java 21, Maven 3.9+, Node 20+, npm, and Docker Desktop.

1. Copy `.env.example` to `.env` and set a long `JWT_SECRET`. `OPENAI_API_KEY` is optional; without it, the dashboard returns useful deterministic, type-specific guidance. With it, the backend asks `gpt-4o-mini` for a structured explanation and falls back to that guidance if the provider is unavailable.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the API: `mvn -f backend/pom.xml spring-boot:run`.
4. Start the web app: `cd frontend`, `npm install`, `npm run dev`.
5. Create an account in the browser, upload both CSV files, and run reconciliation.

The frontend uses `VITE_API_URL` and the backend defaults to `http://localhost:8080`. Flyway creates the schema on API startup. No secrets are committed.

## Architecture

The Spring Boot API owns authentication, CSV parsing, persistence, reconciliation, and the OpenAI call. PostgreSQL stores users, upload batches, normalized source rows, run summaries, discrepancies, and cached explanations. Every authenticated query receives the user UUID from the verified JWT principal and includes that UUID in its SQL predicate.

The React/Vite client has three states: authentication, upload, and dashboard. Recharts presents the issue breakdown. TanStack Table renders the searchable, filterable discrepancy queue. Explanation requests are made per row and never block the rest of the report.

## Reconciliation rules

Order ids and payment references are trimmed and upper-cased before grouping. Exact duplicate source rows are removed during ingestion and recorded as batch warnings. The latest orders and payments batches are grouped by normalized reference, then left/right joined.

Financial discrepancy types are:

| Type | Rule | Severity |
| --- | --- | --- |
| MISSING_PAYMENT | An order has no payment group | Critical |
| ORPHAN_PAYMENT | A payment group has no order | Critical |
| AMOUNT_MISMATCH | A settled one-to-one charge differs from `net_amount` by more than $0.02 | Critical |
| DUPLICATE_CHARGE | More than one settled charge exists for an order | Critical |
| CANCELLED_BUT_CHARGED | A cancelled order has a settled charge | Critical |
| STATUS_CONFLICT | A completed order has a failed or pending charge | High |
| PARTIAL_REFUND | A refunded order has less refund value than charge value, outside $0.02 tolerance | High |
| STALE_STATUS | A non-refunded order has a refund at least as large as the settled charge | High |
| CURRENCY_MISMATCH | Order and payment currency codes are not exactly equal | High |

Amounts are compared to the charge amount, not `net_settled`, because processor fees are not customer-facing order value. Multi-payment groups sum settled charges and refunds separately. Ingestion warnings such as blank timestamps, duplicate rows, and normalized id variants are intentionally excluded from financial dispute totals.

The engine is a plain Java service with fixture-backed JUnit coverage. Re-running the same batches creates a new run but produces the same findings. `money at risk` counts missing and orphan values, duplicate overcharge, the uncollected side of amount mismatches, and cancelled charges.

## Findings in the supplied data

After exact duplicate removal there are 184 orders and 187 payment rows. The fixtures produce 19 financial findings:

- 4 missing payments: ORD-1201 through ORD-1204.
- 3 orphan payment groups: ORD-1301 through ORD-1303.
- 3 amount mismatches: ORD-1401 through ORD-1403.
- 2 duplicate-charge groups: ORD-1501 and ORD-1502.
- 1 cancelled order that was charged: ORD-1701.
- 2 status conflicts: ORD-2001 failed and ORD-2002 pending.
- 1 partial refund: ORD-1702.
- 1 stale status after a full refund: ORD-1703.
- 2 currency mismatches: ORD-1601 and ORD-1602.

These represent uncollected sales, money collected without a matching order, duplicate collections, charges against cancelled orders, unsettled completed orders, incomplete refunds, stale order state, and currency metadata errors. The ingestion warnings include the duplicate ORD-1004 row, case/whitespace variants around ORD-1801 and ORD-1802, missing optional values, and a missing payment timestamp.

## LLM layer

The backend sends only computed discrepancy facts: type, severity, order id, relevant amounts, and statuses. It never sends raw CSV rows. The model is `gpt-4o-mini` at temperature `0.2`: the task is explanation over fixed facts, so a low temperature favors stable, concise output. The request asks for JSON containing `likely_cause`, `recommended_action`, and `confidence`; the server validates all three fields as non-empty text, retries once after malformed output or a transport failure, and returns useful deterministic guidance if it still fails. Successful responses are cached by discrepancy id in PostgreSQL, and each response identifies whether it came from the LLM or the fallback.

## Next steps

For a production version I would add refresh-token rotation, asynchronous large-file ingestion with progress, cursor pagination, currency-aware reporting for multi-currency batches, richer ingestion-warning views, and deployment health checks/observability. Hosting is intentionally left as a deployment decision rather than silently choosing a provider.

## AI tooling note

AI tools were used for exploration, scaffolding, and implementation assistance. The reconciliation rules, fixture findings, tests, and final code were reviewed against the assignment brief and the supplied CSVs.