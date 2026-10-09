-- How much of each PO line has arrived so far. Maintained by the PurchaseOrder aggregate
-- when goods receipts are posted; the database guarantees it never exceeds the ordered quantity.
ALTER TABLE purchase_order_item
    ADD COLUMN received_quantity NUMERIC(19, 3) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_purchase_order_item_received
        CHECK (received_quantity >= 0 AND received_quantity <= quantity);

CREATE SEQUENCE goods_receipt_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE goods_receipt
(
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gr_number            VARCHAR(32)  NOT NULL,
    purchase_order_id    BIGINT       NOT NULL REFERENCES purchase_order (id),
    warehouse_id         BIGINT       NOT NULL REFERENCES warehouse (id),
    receipt_date         DATE         NOT NULL,
    -- The supplier's delivery note ("Lieferschein") number, if any.
    delivery_note_number VARCHAR(64),
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_goods_receipt_number UNIQUE (gr_number)
);

CREATE INDEX ix_goods_receipt_purchase_order ON goods_receipt (purchase_order_id);

CREATE TABLE goods_receipt_item
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goods_receipt_id       BIGINT         NOT NULL REFERENCES goods_receipt (id) ON DELETE CASCADE,
    purchase_order_item_id BIGINT         NOT NULL REFERENCES purchase_order_item (id),
    quantity_received      NUMERIC(19, 3) NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_goods_receipt_item UNIQUE (goods_receipt_id, purchase_order_item_id),
    CONSTRAINT ck_goods_receipt_item_quantity CHECK (quantity_received > 0)
);

CREATE INDEX ix_goods_receipt_item_po_item ON goods_receipt_item (purchase_order_item_id);
