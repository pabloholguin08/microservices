create table notifications (
    id uuid primary key,
    order_id uuid not null,
    customer_id uuid not null,
    type text not null,
    message text not null,
    sent_at timestamptz not null default now(),
    constraint notifications_type_check check (type in ('ORDER_CONFIRMED', 'ORDER_CANCELLED'))
);

create index notifications_order_id_idx on notifications (order_id);
create index notifications_customer_id_idx on notifications (customer_id);
