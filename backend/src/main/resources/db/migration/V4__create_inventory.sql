CREATE TABLE warehouse
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(16)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_warehouse_code UNIQUE (code)
);

-- Every installation starts with one warehouse so goods can be received immediately.
INSERT INTO warehouse (code, name, active, created_at, updated_at)
VALUES ('MAIN', 'Main warehouse', TRUE, now(), now());

-- Current balance per warehouse and product. Always changed together with a stock_movement row.
CREATE TABLE stock_level
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouse (id),
    product_id       BIGINT         NOT NULL REFERENCES product (id),
    quantity_on_hand NUMERIC(19, 3) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_stock_level UNIQUE (warehouse_id, product_id),
    CONSTRAINT ck_stock_level_not_negative CHECK (quantity_on_hand >= 0)
);

-- Immutable ledger: the audit trail explaining every stock level.
CREATE TABLE stock_movement
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    warehouse_id     BIGINT         NOT NULL REFERENCES warehouse (id),
    product_id       BIGINT         NOT NULL REFERENCES product (id),
    quantity         NUMERIC(19, 3) NOT NULL,
    movement_type    VARCHAR(32)    NOT NULL,
    -- Source document (e.g. GOODS_RECEIPT / 7 / GR-000007). Polymorphic, so no foreign key.
    reference_type   VARCHAR(32),
    reference_id     BIGINT,
    reference_number VARCHAR(32),
    note             VARCHAR(500),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT ck_stock_movement_quantity CHECK (quantity <> 0),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('GOODS_RECEIPT', 'ADJUSTMENT'))
);

CREATE INDEX ix_stock_movement_product ON stock_movement (product_id);
CREATE INDEX ix_stock_movement_warehouse ON stock_movement (warehouse_id);
