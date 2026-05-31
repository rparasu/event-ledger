# Event Ledger

A distributed system composed of two microservices that process financial transaction events with idempotency, out-of-order tolerance, resiliency, and observability.

---

## Tech Stack

| Category   | Technology                      | Version               |
|------------|---------------------------------|-----------------------|
| Language   | Java                            | 21                    |
| Framework  | Spring Boot                     | 3.3.5                 |
| REST       | Spring Web MVC                  | Spring Boot managed   |
| HTTP Client| Spring WebFlux (WebClient)      | Spring Boot managed   |
| Persistence| Spring Data JPA + H2            | Spring Boot managed   |
| Validation | Spring Validation               | Spring Boot managed   |
| Resiliency | Resilience4j                    | 2.2.0                 |
| Tracing    | Micrometer Tracing (Brave)      | Spring Boot managed   |
| Metrics    | Micrometer + Spring Actuator    | Spring Boot managed   |
| Logging    | Logstash Logback Encoder        | 8.0                   |
| API Docs   | SpringDoc OpenAPI / Swagger UI  | 2.6.0                 |
| Testing    | JUnit 5 + Spring Boot Test      | Spring Boot managed   |
| Testing    | WireMock                        | 3.9.1                 |
| Build      | Maven                           | 3.x                   |

---

## Architecture

![Architecture](docs/diagrams/event-ledger-architecture.png)

The system is split into two independent services:

### Gateway Service (`:8080`)
Public-facing entry point. Accepts transaction events from clients, enforces idempotency, persists events locally, and forwards them to the Account Service.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/events` | Submit a transaction event |
| `GET`  | `/events/{id}` | Retrieve a single event by ID |
| `GET`  | `/events?account={accountId}` | List events for an account |
| `GET`  | `/events/balance?account={accountId}` | Get account balance |
| `GET`  | `/health` | Health check |

### Account Service (`:8081`)
Internal service. Manages account state — applies transactions and computes balances. Only called by the Gateway.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction |
| `GET`  | `/accounts/{accountId}/balance` | Get current balance |
| `GET`  | `/accounts/{accountId}` | Get account details and recent transactions |
| `GET`  | `/health` | Health check |

---

## Sequence Diagrams

### Gateway Service Flow
![Gateway Sequence](docs/diagrams/gateway-sequence.png)

### Account Service Flow
![Account Service Sequence](docs/diagrams/account-service-sequence.png)

---

## Prerequisites

- Java 21
- Maven 3.x (or use the included `./mvnw` wrapper — no install needed)

---

## Running the Services

> _Docker Compose to be added_

### Manual — Account Service
```bash
cd account-service
./mvnw spring-boot:run
```

| URL | Description |
|-----|-------------|
| `http://localhost:8081/swagger-ui/index.html` | Swagger UI — interactive API testing |
| `http://localhost:8081/v3/api-docs` | OpenAPI JSON spec |
| `http://localhost:8081/health` | Health check |
| `http://localhost:8081/h2-console` | H2 database console |

![Account Service Swagger](docs/diagrams/SwaggerAcctService.png)

### Inspecting the Database (H2 Console)

Open `http://localhost:8081/h2-console` and connect with:

| Field | Value |
|-------|-------|
| JDBC URL | `jdbc:h2:mem:accountsdb` |
| Username | `sa` |
| Password | _(leave empty)_ |

Useful queries:
```sql
-- View all applied transactions
SELECT * FROM APPLIED_TRANSACTION;

-- Check balance for a specific account
SELECT ACCOUNT_ID,
       SUM(CASE WHEN TYPE = 'CREDIT' THEN AMOUNT ELSE -AMOUNT END) AS BALANCE
FROM APPLIED_TRANSACTION
GROUP BY ACCOUNT_ID;
```

### Manual — Gateway Service

> _To be added_

---

## Testing Manually via Swagger

Once the Account Service is running, open `http://localhost:8081/swagger-ui/index.html` and try:

**Apply a transaction:**
```json
POST /accounts/{accountId}/transactions
{
  "eventId": "evt-001",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:00:00Z"
}
```

**Get balance:**
```
GET /accounts/{accountId}/balance
```

**Verify idempotency** — submit the same `eventId` twice, second response returns `alreadyApplied: true`.

---

## Running the Tests

### Account Service
```bash
cd account-service
./mvnw test
```

Covers:
- Idempotent transaction apply (duplicate `eventId` returns `200 alreadyApplied=true`)
- Balance computation — `Σ(CREDIT) − Σ(DEBIT)`
- Out-of-order tolerance — balance is correct regardless of arrival order
- Input validation — rejects negative amounts and unknown transaction types
- Health check

### Gateway Service

> _To be added_

---

## Resiliency Pattern

> _To be added_

---
