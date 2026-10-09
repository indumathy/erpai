CREATE TABLE product
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku             VARCHAR(64)   NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(2000),
    unit_of_measure VARCHAR(16)   NOT NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uq_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_unit_of_measure
        CHECK (unit_of_measure IN ('PIECE', 'BOX', 'PALLET', 'KG', 'LITER', 'METER', 'HOUR'))
);
