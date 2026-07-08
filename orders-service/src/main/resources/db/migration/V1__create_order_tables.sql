create table orders (
    id uuid primary key,
    customer_id uuid not null,
    status text not null default 'CREATED',
    total_amount numeric(12, 2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint orders_status_check check (status in ('CREATED', 'CONFIRMED', 'CANCELLED', 'FAILED')),
    constraint orders_total_amount_check check (total_amount >= 0)
);

create index orders_customer_id_idx on orders (customer_id);
create index orders_status_idx on orders (status);

create table order_items (
    id uuid primary key,
    order_id uuid not null references orders (id) on delete cascade,
    product_id uuid not null,
    quantity integer not null,
    unit_price numeric(12, 2) not null,
    constraint order_items_quantity_check check (quantity > 0),
    constraint order_items_unit_price_check check (unit_price >= 0)
);

create index order_items_order_id_idx on order_items (order_id);
create index order_items_product_id_idx on order_items (product_id);
