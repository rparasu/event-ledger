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

> _To be added_

---

## Running the Services

> _To be added (Docker Compose / manual steps)_

---

## Running the Tests

> _To be added_

---

## Resiliency Pattern

> _To be added_

---
