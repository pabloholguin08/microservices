create table products (
    id uuid primary key,
    name text not null,
    sku text not null,
    price numeric(12, 2) not null,
    stock_quantity integer not null default 0,
    version integer not null default 0,
    constraint products_sku_unique unique (sku),
    constraint products_price_check check (price >= 0),
    constraint products_stock_quantity_check check (stock_quantity >= 0)
);

create table stock_reservations (
    id uuid primary key,
    order_id uuid not null,
    product_id uuid not null references products (id),
    quantity integer not null,
    status text not null default 'RESERVED',
    created_at timestamptz not null default now(),
    constraint stock_reservations_quantity_check check (quantity > 0),
    constraint stock_reservations_status_check check (status in ('RESERVED', 'RELEASED'))
);

create index stock_reservations_order_id_idx on stock_reservations (order_id);
create index stock_reservations_product_id_idx on stock_reservations (product_id);
