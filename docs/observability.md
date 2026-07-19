# Observability

The three services ship with metrics, distributed tracing, and structured
logging out of the box — this isn't bolted on for the demo, it's the same
configuration that runs whether you bring the stack up with Docker Compose
or on Kubernetes (see [`k8s/README.md`](../k8s/README.md)).

| Tool | URL | Credentials |
|---|---|---|
| Grafana | <http://localhost:3000> | `admin` / `admin` (also open to anonymous viewers, local-only) |
| Prometheus | <http://localhost:9090> | none |
| Jaeger UI | <http://localhost:16686> | none |
| RabbitMQ management | <http://localhost:15672> | `guest` / `guest` |

## What's instrumented

- **Metrics** — Actuator + Micrometer, scraped by Prometheus from
  `/actuator/prometheus` on each service (5s interval) and from RabbitMQ's
  `rabbitmq_prometheus` plugin. Beyond the automatic HTTP/JVM metrics, each
  service publishes business counters:
  - `orders_placed_total`, `orders_confirmed_total`,
    `orders_cancelled_total{reason="customer"|"stock_rejected"}` (orders-service)
  - `stock_reservations_total{outcome="reserved"}`,
    `stock_rejections_total{reason="insufficient_stock"|"concurrency_conflict"}`
    (inventory-service)
- **Tracing** — Micrometer Tracing bridged to OpenTelemetry
  (`spring-boot-starter-opentelemetry`), exported via OTLP/HTTP to Jaeger.
  100% sampling (`management.tracing.sampling.probability: 1.0`) so every
  request produces a trace — turn this down before ever running this
  configuration anywhere with real traffic.
- **Structured logging** — JSON lines via `logstash-logback-encoder`
  (`logback-spring.xml` in each service), with `traceId`/`spanId` populated
  automatically by Micrometer Tracing's MDC handler and `orderId` pushed
  explicitly at the handful of call sites that own an order (see
  `MDC.putCloseable` in `OrderService`, `StockEventConsumer`,
  `OrderCreatedConsumer`, `OrderEventConsumer`). Pipe compose logs through
  `jq` to read them: `docker compose logs -f orders-service | jq .`

## Tracing across the RabbitMQ boundary

The hard part of tracing a choreography saga is that the trace shouldn't
break at the queue — a trace that stops at "published to RabbitMQ" and
starts over at "received from RabbitMQ" isn't proving anything a log
correlation id couldn't already prove. This required **zero custom
propagation code**: Spring AMQP has built-in Micrometer Observation support
for `RabbitTemplate` and `@RabbitListener` containers that injects/extracts
the W3C `traceparent` header on the AMQP message itself. Turning it on is
two properties per service:

```yaml
spring:
  rabbitmq:
    template:
      observation-enabled: true
    listener:
      simple:
        observation-enabled: true
```

## Trace one order end-to-end

1. `docker compose up -d --build` and wait for everything to report healthy
   (`docker compose ps`).
2. Place an order (via the frontend at <http://localhost:5173>, or directly):

   ```bash
   curl -X POST http://localhost:8081/api/v1/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId":"<uuid>","items":[{"productId":"<uuid-from-/api/v1/products>","quantity":1,"unitPrice":34.50}]}'
   ```

3. Open Jaeger (<http://localhost:16686>), pick **orders-service** from the
   *Service* dropdown, and find the trace for `http post /api/v1/orders`.
   You should see one connected trace with spans across all three services:

   ```
   orders-service        http post /api/v1/orders
   orders-service          orders.exchange/order.created send
   inventory-service         inventory-service.order.created receive
   inventory-service            orders.exchange/order.stock.reserved send
   orders-service                 orders-service.order.stock.reserved receive
   orders-service                    orders.exchange/order.confirmed send
   notification-service                notification-service.order.confirmed receive
   ```

   Every span shares the same trace id — that id is also what shows up as
   `traceId` in each service's JSON logs for that request, so you can jump
   from a log line straight to the full trace.
4. Open Grafana (<http://localhost:3000>) → the **Pedidos — Overview**
   dashboard (provisioned automatically, no login needed for viewing) to see
   the same order reflected in the request-rate, orders-placed, and
   RabbitMQ-queue-depth panels.

To see the rejection path instead, order a quantity larger than a product's
`stockQuantity` (check current stock via `GET /api/v1/products`) — the trace
will show `order.stock.rejected` instead of `order.stock.reserved`, and the
**Stock rejections** panel in Grafana will tick up.

## Grafana dashboard

`observability/grafana/dashboards/pedidos-overview.json`, provisioned
automatically on Grafana startup (`observability/grafana/provisioning/`):

- **HTTP** — request rate, 5xx error rate, and p95 latency per service
- **Order saga** — orders placed/confirmed/cancelled (by reason), and stock
  rejections (by reason) — the business-level view of the saga, independent
  of HTTP status codes
- **Messaging** — RabbitMQ ready-message count per queue; a queue that keeps
  growing means its consumer is falling behind or down

## Notable gotchas found while wiring this up

- **Two different OTLP tracing property namespaces exist, only one works.**
  Spring Boot 4.1's metadata lists both `management.otlp.tracing.endpoint`
  and `management.opentelemetry.tracing.export.otlp.endpoint`. Only the
  second is actually bound to a `@ConfigurationProperties` bean
  (`OtlpTracingProperties`) — the first is silently a no-op. Confirmed via
  `/actuator/configprops`, not by reading docs, since both are documented as
  if they worked.
- **Prometheus's OpenMetrics naming spec reserves the `_created` suffix.** A
  Micrometer counter named `orders.created` gets rendered as `orders_total`
  in the scrape output — Prometheus treats the `_created` segment as the
  reserved "counter created timestamp" marker and swallows it, rather than
  producing `orders_created_total`. The fix was renaming the counter (here,
  to `orders.placed`) rather than fighting the naming convention.
- **RabbitMQ's Prometheus plugin has two scrape paths.** `/metrics` (the
  default) only exposes cluster-wide aggregates; per-queue series — what a
  "queue depth" panel actually needs — live under `/metrics/per-object`.
  Cardinality is why it's opt-in rather than the default.
- **`read_only: true` doesn't suit every container equally.** It works
  cleanly for the four services we build ourselves (non-root user we
  control, everything they write is under `/tmp`). It broke Prometheus:
  its TSDB writes throughout `/prometheus`, not one subdirectory, and the
  official image's non-root user doesn't own a freshly-mounted tmpfs there.
  Prometheus runs with `cap_drop: [ALL]` and `no-new-privileges` but not
  `read_only` — matching the hardening to what each image actually needs
  is more valuable than applying the same recipe everywhere uniformly.
