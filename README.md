# Digital Wallet (FinTech) - Spring Boot + React

Wallet API where money integrity is the point: ACID transfers, JWT security, Redis rate limiting and a separate audit log.

## Run it
```bash
docker compose up -d                          # MySQL 8.4 + Redis 7
cd backend && mvn spring-boot:run             # -> http://localhost:8080
cd frontend && npm install && npm run dev     # -> http://localhost:5173
```
Swagger UI: http://localhost:8080/swagger-ui.html (Authorize with the token from /api/auth/login).
Set `JWT_SECRET` (32+ chars) outside local dev. Audit trail: `backend/logs/audit.log`.

## API
| Method | Path | Notes |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | returns JWT (30 min) |
| GET | /api/wallet | balance (Redis-cached 30 s, evicted on every write) |
| GET | /api/wallet/transactions?page=0&size=10 | ledger history |
| POST | /api/wallet/deposit, /withdraw, /transfer | require `Idempotency-Key` header |

## Design decisions worth explaining in interviews
1. **Atomic transfer**: debit + credit + ledger insert in one `@Transactional` method; any failure rolls back all three.
2. **Pessimistic row locks in id order** (`SELECT ... FOR UPDATE ... ORDER BY id`) stop double-spend and A->B / B->A deadlocks. `@Version` is a second guard.
3. **Idempotency keys**: unique `(initiator_wallet_id, idempotency_key)`; a retry returns the original result, a concurrent duplicate gets 409 and rolls back.
4. **DECIMAL(19,4) + BigDecimal**, never floating point; `CHECK (balance >= 0)` in the database as a last line of defence.
5. **Append-only ledger** (`wallet_transactions`); balances can be reconciled from it.
6. **Redis**: distributed fixed-window rate limiting (stricter on /api/auth) and short-TTL read caching. The money path never trusts the cache.
7. **Audit logging**: dedicated `AUDIT` Logback logger, daily rolling file, 90-day retention, a `requestId` on every line, no secrets logged.
8. **Spring Security as an OAuth2 resource server** validating HS256 JWTs; stateless, CSRF off because there are no cookies.

## Next steps for production
Flyway migrations instead of `ddl-auto: update`, refresh tokens, RS256 keys from a secrets manager, a payment-gateway webhook for deposits, Testcontainers concurrency tests (100 parallel transfers, assert total balance unchanged), Micrometer metrics.
