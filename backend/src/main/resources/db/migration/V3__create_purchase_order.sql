-- Gap-free enough for an ERP document number; formatted as PO-000001 by the application.
CREATE SEQUENCE purchase_order_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE purchase_order
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    po_number   VARCHAR(32) NOT NULL,
    supplier_id BIGINT      NOT NULL REFERENCES supplier (id),
    order_date  DATE        NOT NULL,
    currency    VARCHAR(3)  NOT NULL,
    status      VARCHAR(32) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_purchase_order_number UNIQUE (po_number),
    CONSTRAINT ck_purchase_order_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_purchase_order_status
        CHECK (status IN ('DRAFT', 'APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'))
);

CREATE INDEX ix_purchase_order_supplier ON purchase_order (supplier_id);
CREATE INDEX ix_purchase_order_status ON purchase_order (status);

CREATE TABLE purchase_order_item
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    line_number       INTEGER        NOT NULL,
    product_id        BIGINT         NOT NULL REFERENCES product (id),
    description       VARCHAR(500)   NOT NULL,
    quantity          NUMERIC(19, 3) NOT NULL,
    unit_price        NUMERIC(19, 4) NOT NULL,
    tax_rate          NUMERIC(5, 2)  NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,

    -- DEFERRABLE: replacing the lines of a draft PO deletes and re-inserts them in one transaction;
    -- Hibernate flushes inserts before deletes, so these are checked at commit, not per statement.
    CONSTRAINT uq_purchase_order_item_line UNIQUE (purchase_order_id, line_number) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_purchase_order_item_product UNIQUE (purchase_order_id, product_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_purchase_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_purchase_order_item_tax_rate CHECK (tax_rate BETWEEN 0 AND 100)
);

CREATE INDEX ix_purchase_order_item_product ON purchase_order_item (product_id);
