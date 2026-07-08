# Distributed Order System — Development Plan

## Goal

Second portfolio project demonstrating distributed systems and event-driven architecture skills, complementing the monolithic reservas-canchas project. Priorities: services communicating asynchronously, eventual consistency, resilience (retries, idempotency, dead-letter handling), and a clearly documented event flow — not just splitting a CRUD app into multiple deployables.

A simplified e-commerce order system split into three independently deployable services that coordinate via a message broker using a choreography-based saga pattern.

## Tech stack

**Backend:** Java 17+, Spring Boot 4.1, Spring Web, Spring Data JPA, Spring AMQP (RabbitMQ client), PostgreSQL 16 (one database per service), Flyway, JUnit 5, Testcontainers (Postgres + RabbitMQ modules), springdoc-openapi

**Messaging:** RabbitMQ (topic exchange)

**Frontend:** React 19, TypeScript, Vite, TanStack Query (with polling or Server-Sent Events for live order status)

**Infra:** Docker, Docker Compose, GitHub Actions

## Repository structure

```
pedidos-microservicios/
├── orders-service/
├── inventory-service/
├── notification-service/
├── frontend/
├── docker-compose.yml
├── .github/
│   └── workflows/
├── docs/
│   ├── architecture.md
│   └── event-flow.md
└── README.md
```

## Services and data model

### orders-service (own database: `orders_db`)

**Order**
- id: UUID (PK)
- customer_id: UUID
- status: enum (`CREATED`, `CONFIRMED`, `CANCELLED`, `FAILED`)
- total_amount: decimal
- created_at, updated_at: timestamp

**OrderItem**
- id: UUID (PK)
- order_id: UUID (FK -> Order)
- product_id: UUID
- quantity: int
- unit_price: decimal

### inventory-service (own database: `inventory_db`)

**Product**
- id: UUID (PK)
- name: string
- sku: string (unique)
- price: decimal
- stock_quantity: int
- version: int — optimistic locking column, critical for preventing overselling under concurrent orders

**StockReservation**
- id: UUID (PK)
- order_id: UUID
- product_id: UUID
- quantity: int
- status: enum (`RESERVED`, `RELEASED`)
- created_at: timestamp

### notification-service (own database: `notifications_db`, minimal)

**Notification**
- id: UUID (PK)
- order_id: UUID
- customer_id: UUID
- type: enum (`ORDER_CONFIRMED`, `ORDER_CANCELLED`)
- message: string
- sent_at: timestamp

No real email/SMS provider needed — creating the `Notification` record (and logging it) is enough to demonstrate the pattern.

## Event flow (the core of this project)

Topic exchange: `orders.exchange`. Routing keys and flow:

1. Client calls `POST /api/orders` on **orders-service** → order is persisted with status `CREATED` → publishes `order.created` (order id, customer id, items)
2. **inventory-service** consumes `order.created` → attempts to reserve stock for each item using the `version` column (optimistic locking) → publishes `order.stock.reserved` on success, or `order.stock.rejected` (with a reason) if any item is out of stock
3. **orders-service** consumes `order.stock.reserved` → updates order status to `CONFIRMED` → publishes `order.confirmed`. Or consumes `order.stock.rejected` → updates status to `CANCELLED` → publishes `order.cancelled`
4. **notification-service** consumes `order.confirmed` and `order.cancelled` → creates the corresponding `Notification` record

This is a **choreography-based saga**: no central orchestrator, each service reacts to events and publishes its own. Document this decision (vs. an orchestrator-based saga) in the README — it's a common interview topic and shows you understand the trade-off, not just the pattern name.

## Critical requirements (do not simplify away)

- **Idempotency**: message brokers guarantee at-least-once delivery, so the same event can arrive twice. Every consumer must check whether it already processed a given order/event id before acting again — e.g. `inventory-service` must not double-reserve stock if `order.created` is redelivered.
- **Optimistic locking on stock**: use the `@Version` column on `Product` so two concurrent orders for the last unit cannot both succeed — one must retry cleanly or fail with a clear error.
- **Dead-letter queue**: configure a DLQ for each queue so a message that repeatedly fails processing doesn't block the queue forever, and stays visible for inspection.
- **Correlation id**: propagate an order/correlation id through every event so the full lifecycle of one order can be traced across the three services' logs.

## API endpoints

```
# orders-service
POST   /api/orders
GET    /api/orders/{id}
GET    /api/orders?customerId=...
PUT    /api/orders/{id}/cancel        (before confirmation only)

# inventory-service
GET    /api/products
GET    /api/products/{id}
POST   /api/products                  (admin/seed)
PUT    /api/products/{id}/stock       (admin/seed)

# notification-service
GET    /api/notifications?customerId=...
```

## Frontend screens and flows

Keep auth minimal here — this project is about backend distributed-systems depth, not re-implementing login (already covered in reservas-canchas). A simple customer id field is enough.

1. Product catalog (from inventory-service)
2. Place an order (select products + quantities) → calls orders-service
3. Order status view that updates live as the order moves `CREATED → CONFIRMED/CANCELLED` (poll every 1-2s, or use Server-Sent Events for a nicer demo effect)
4. Order history for a customer

## Non-functional requirements

- Unit tests per service — especially the optimistic-locking retry path in inventory-service
- Integration tests with Testcontainers: spin up Postgres + RabbitMQ, publish a real `order.created` event, assert the correct downstream event is published
- A specific test simulating two concurrent orders for the last unit of a product, asserting only one succeeds
- A specific test simulating a duplicate event delivery, asserting no double stock deduction
- `docker-compose.yml` bringing up RabbitMQ, the three Postgres instances, the three services, and the frontend, all with one command
- GitHub Actions: matrix build/test across the three services on every PR
- `README.md` in English with an architecture diagram, a sequence diagram of the event flow, and a short section explaining the saga pattern choice and trade-offs

## Development phases

### Phase 1 — Infra and skeleton
- [ ] `docker-compose.yml` with RabbitMQ and three Postgres databases
- [ ] Scaffold the three Spring Boot services with health-check endpoints
- [ ] Verify all services start and connect to their databases and to RabbitMQ via Compose

### Phase 2 — orders-service
- [ ] Order/OrderItem entities + Flyway migrations
- [ ] `POST /api/orders` creates the order and publishes `order.created`
- [ ] `GET` endpoints for order retrieval

### Phase 3 — inventory-service
- [ ] Product/StockReservation entities + Flyway migrations, seed data
- [ ] Consumer for `order.created` that reserves stock with optimistic locking
- [ ] Publish `order.stock.reserved` / `order.stock.rejected`
- [ ] Idempotency check on the consumer

### Phase 4 — orders-service event consumers
- [ ] Consume `order.stock.reserved` / `order.stock.rejected`
- [ ] Update order status accordingly and publish `order.confirmed` / `order.cancelled`

### Phase 5 — notification-service
- [ ] Consume `order.confirmed` / `order.cancelled`
- [ ] Persist and log the corresponding `Notification`

### Phase 6 — Frontend
- [ ] Product catalog page
- [ ] Place-order flow
- [ ] Live order status view (polling or SSE)
- [ ] Order history page

### Phase 7 — Resilience and testing
- [ ] Dead-letter queue configuration per queue
- [ ] Concurrency test: two simultaneous orders for the last unit of stock
- [ ] Duplicate-delivery idempotency test
- [ ] Integration tests with Testcontainers (Postgres + RabbitMQ)

### Phase 8 — CI/CD and docs
- [ ] GitHub Actions matrix build/test for the three services
- [ ] `README.md` with architecture + sequence diagrams and the saga pattern write-up
- [ ] Decide and document the deploy story: a polished local `docker-compose up` demo with a recorded walkthrough, or an actual cloud deploy (all containers on a single Fly.io app / Railway project) if you want a clickable live demo

## Notes for Claude Code

- Build and verify one service at a time — don't wire up the event flow until `orders-service` and `inventory-service` both run and connect to RabbitMQ independently.
- Keep the saga choreography-based as specified; don't introduce a central orchestrator unless asked.
- Idempotency and the optimistic-locking test are the two most important pieces of this project for interview purposes — do not skip them to save time.
- Ask before making major architectural deviations (e.g. switching to Kafka, adding an API gateway, adding a fourth service).
- Write all code comments, commit messages, and the README in English.
