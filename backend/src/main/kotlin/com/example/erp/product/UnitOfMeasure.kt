package com.example.erp.product

/**
 * Units a product is ordered, received and invoiced in.
 * Stored by name (VARCHAR), so the enum order may change but names must not
 * without a Flyway migration (see V2__create_product.sql CHECK constraint).
 */
enum class UnitOfMeasure {
    PIECE,
    BOX,
    PALLET,
    KG,
    LITER,
    METER,
    HOUR,
}
