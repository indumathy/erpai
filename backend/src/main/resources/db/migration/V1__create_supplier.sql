CREATE TABLE supplier
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_number    VARCHAR(32)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    vat_id             VARCHAR(32),
    currency           VARCHAR(3)   NOT NULL,
    payment_terms_days INTEGER      NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_supplier_number UNIQUE (supplier_number),
    CONSTRAINT ck_supplier_payment_terms CHECK (payment_terms_days BETWEEN 0 AND 365),
    CONSTRAINT ck_supplier_currency CHECK (currency ~ '^[A-Z]{3}$')
);
