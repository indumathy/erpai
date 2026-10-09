-- Supplier invoices ("Eingangsrechnungen"). Matching against PO and goods receipts is computed
-- on demand by InvoiceMatcher; nothing about the match result is stored here.
CREATE TABLE supplier_invoice
(
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id       BIGINT      NOT NULL REFERENCES supplier (id),
    -- The supplier's own number as printed. Deliberately NOT unique: duplicates must be storable
    -- so the DUPLICATE_INVOICE rule can detect them.
    invoice_number    VARCHAR(64) NOT NULL,
    invoice_date      DATE        NOT NULL,
    currency          VARCHAR(3)  NOT NULL,
    purchase_order_id BIGINT REFERENCES purchase_order (id),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_supplier_invoice_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX ix_supplier_invoice_supplier_number ON supplier_invoice (supplier_id, lower(invoice_number));
CREATE INDEX ix_supplier_invoice_purchase_order ON supplier_invoice (purchase_order_id);

CREATE TABLE supplier_invoice_item
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_invoice_id    BIGINT         NOT NULL REFERENCES supplier_invoice (id) ON DELETE CASCADE,
    line_number            INTEGER        NOT NULL,
    purchase_order_item_id BIGINT REFERENCES purchase_order_item (id),
    description            VARCHAR(500)   NOT NULL,
    quantity               NUMERIC(19, 3) NOT NULL,
    unit_price             NUMERIC(19, 4) NOT NULL,
    tax_rate               NUMERIC(5, 2)  NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,

    CONSTRAINT uq_supplier_invoice_item_line UNIQUE (supplier_invoice_id, line_number),
    CONSTRAINT ck_supplier_invoice_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_supplier_invoice_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_supplier_invoice_item_tax_rate CHECK (tax_rate BETWEEN 0 AND 100)
);

CREATE INDEX ix_supplier_invoice_item_po_item ON supplier_invoice_item (purchase_order_item_id);
