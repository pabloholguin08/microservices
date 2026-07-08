# Pedidos — Distributed Order System

A simplified e-commerce order system built as three independently deployable
microservices that coordinate exclusively through a message broker, using a
**choreography-based saga**. It's a companion project to a monolithic
reservations app, this time focused on distributed-systems concerns:
asynchronous service coordination, eventual consistency, idempotent consumers,
optimistic-locking under concurrency, and dead-letter handling — not just
splitting a CRUD app into three deployables.

See [docs/architecture.md](docs/architecture.md) for the system diagram and the
choreography-vs-orchestration trade-off write-up, and
[docs/event-flow.md](docs/event-flow.md) for the full event catalog and
sequence diagrams.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Web, Spring Data JPA, Spring AMQP, Flyway |
| Messaging | RabbitMQ (topic exchange, per-consumer dead-letter queues) |
| Database | PostgreSQL 16 — one database per service |
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Router |
| Testing | JUnit 5, Mockito, Testcontainers (Postgres + RabbitMQ) |
| Infra | Docker, Docker Compose, GitHub Actions |

## Repository structure

```
microservices/
├── orders-service/        # order lifecycle, saga entry point
├── inventory-service/     # product catalog, stock reservation
├── notification-service/  # order-outcome notifications
├── frontend/               # React SPA
├── docker-compose.yml      # brings up the whole system with one command
├── docs/
│   ├── architecture.md
│   └── event-flow.md
└── .github/workflows/ci.yml
```

## Quick start

Requires Docker and Docker Compose.

```bash
docker compose up -d --build
```

This starts RabbitMQ, three Postgres instances (one per service), the three
Spring Boot services, and the frontend. Once everything is healthy:

- Frontend: <http://localhost:5173>
- orders-service: <http://localhost:8081> (`/actuator/health`, `/api/orders`)
- inventory-service: <http://localhost:8082> (`/actuator/health`, `/api/products`)
- notification-service: <http://localhost:8083> (`/actuator/health`, `/api/notifications`)
- RabbitMQ management UI: <http://localhost:15672> (guest/guest)

inventory-service seeds five demo products on first startup via Flyway, so the
catalog isn't empty on a fresh run.

```bash
docker compose down        # stop everything, keep data volumes
docker compose down -v     # stop everything and wipe the databases/RabbitMQ data
```

## API reference

```
# orders-service (localhost:8081)
POST   /api/v1/orders                    create an order, publishes order.created (201 + Location header)
GET    /api/v1/orders/{id}
GET    /api/v1/orders?customerId=...&page=0&size=20
PUT    /api/v1/orders/{id}/cancel        only while status is CREATED

# inventory-service (localhost:8082)
GET    /api/v1/products?page=0&size=20
GET    /api/v1/products/{id}
POST   /api/v1/products                  admin/seed (201 + Location header)
PUT    /api/v1/products/{id}/stock       admin/seed

# notification-service (localhost:8083)
GET    /api/v1/notifications?customerId=...&page=0&size=20
```

All three list endpoints return the same paginated envelope:
`{ items, page, pageSize, total, totalPages }`. `page` is 0-indexed; `size`
defaults to 20 and caps at 100.

## Running tests

Each service's test suite spins up real Postgres and RabbitMQ containers via
Testcontainers — no mocking of the database or broker — so Docker must be
running locally to execute them.

```bash
cd orders-service && ./mvnw test
cd inventory-service && ./mvnw test
cd notification-service && ./mvnw test
```

Worth calling out specifically, since they're the two requirements this
project exists to demonstrate:

- [`StockReservationConcurrencyTest`](inventory-service/src/test/java/com/pedidos/inventory_service/service/StockReservationConcurrencyTest.java) —
  fires two concurrent reservation attempts at a product with exactly one unit
  of stock and asserts only one succeeds, proving the `@Version` optimistic
  lock actually prevents overselling rather than just being present in the
  schema.
- [`OrderCreatedConsumerIdempotencyTest`](inventory-service/src/test/java/com/pedidos/inventory_service/messaging/OrderCreatedConsumerIdempotencyTest.java) —
  delivers the same `order.created` event twice and asserts stock is only
  decremented once.

`OrderCreatedIntegrationTest` in inventory-service goes a step further and uses
real Postgres + RabbitMQ containers to publish an actual `order.created`
message onto the exchange and assert the correct downstream event comes back
out, rather than calling the consumer method directly.

CI runs the same `mvn test` per service in a matrix job on every push/PR (see
[.github/workflows/ci.yml](.github/workflows/ci.yml)); GitHub-hosted Ubuntu
runners have Docker preinstalled, so Testcontainers works there without extra
setup.

## Resilience notes

- **Idempotency** is enforced per-consumer (existence checks or state-machine
  guards), not assumed — every consumer can safely process the same message
  twice.
- **Dead-letter queues**: each consumer queue has its own `.dlq`. A message
  that keeps failing is retried three times with backoff, then quarantined
  instead of blocking the queue or looping forever. See
  [docs/event-flow.md](docs/event-flow.md#dead-letter-queues) for how this was
  verified against a real poison message.
- **Correlation id**: the order id is propagated as the AMQP `correlation_id`
  on every event, so one order's full lifecycle can be traced across all three
  services' logs.

## Deployment

This project ships as a local `docker compose up` demo rather than a hosted
cloud deployment. Everything — three services, three databases, RabbitMQ, and
the frontend — comes up with one command and requires no external accounts or
recurring cost, which keeps the project reproducible for anyone reviewing it.
A cloud deploy (e.g. all containers on a single Fly.io app or Railway project)
is a reasonable next step if a clickable live demo becomes worth the
maintenance/cost trade-off, but wasn't pursued here to keep the project fully
self-contained.

## Notable build/config details

A few non-obvious things worth knowing if you're extending this project:

- **Spring Boot 4.1 ships on Jackson 3** (`tools.jackson.*`, not
  `com.fasterxml.jackson.*`). Spring AMQP's message converter needs
  `JacksonJsonMessageConverter` + `tools.jackson.databind.json.JsonMapper`, not
  the legacy `Jackson2JsonMessageConverter`.
- **`spring-boot-starter-aop` and `spring-retry` are not needed** for the
  RabbitMQ retry/DLQ setup — `RetryInterceptorBuilder` in this Spring AMQP
  version is built on Spring Framework 7's own `org.springframework.core.retry`
  API.
- Each service uses **time-ordered UUID primary keys**
  (`@UuidGenerator(style = UuidGenerator.Style.TIME)`) instead of random v4
  UUIDs, to avoid the index fragmentation that comes with fully random primary
  keys on B-tree indexes.
- `open-in-view` is disabled on all three services; read paths that need
  associations use `@EntityGraph` to fetch them in one query inside the
  transactional boundary, rather than relying on lazy loading after the
  transaction closes.
