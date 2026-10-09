package com.example.erp.shared.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Single source of truth for numeric scales and rounding in the ERP.
 *
 * Every monetary/quantity value entering the domain is normalised here, so that
 * the database column scales (see Flyway migrations) and the calculations agree.
 * No other code should call `setScale` with its own ad-hoc scale or rounding mode.
 */
object MoneyRounding {

    /** Commercial rounding (kaufmännisches Runden): 0.005 -> 0.01. */
    val MODE: RoundingMode = RoundingMode.HALF_UP

    /** Amounts: line totals, subtotal, tax, total. NUMERIC(19,2). */
    const val AMOUNT_SCALE = 2

    /** Unit prices may carry sub-cent precision (e.g. 0.0125 EUR per screw). NUMERIC(19,4). */
    const val UNIT_PRICE_SCALE = 4

    /** Quantities allow fractional units such as kg or m. NUMERIC(19,3). */
    const val QUANTITY_SCALE = 3

    /** Percentages such as tax rates and tolerances: 19.00 means 19 %. NUMERIC(5,2). */
    const val PERCENT_SCALE = 2

    private val HUNDRED = BigDecimal(100)

    fun amount(value: BigDecimal): BigDecimal = value.setScale(AMOUNT_SCALE, MODE)

    fun unitPrice(value: BigDecimal): BigDecimal = value.setScale(UNIT_PRICE_SCALE, MODE)

    fun quantity(value: BigDecimal): BigDecimal = value.setScale(QUANTITY_SCALE, MODE)

    fun percent(value: BigDecimal): BigDecimal = value.setScale(PERCENT_SCALE, MODE)

    /** `percent` % of `base`, rounded to an amount. Example: percentOf(100.00, 19.00) = 19.00. */
    fun percentOf(base: BigDecimal, percent: BigDecimal): BigDecimal =
        amount(base.multiply(percent).divide(HUNDRED))
}

/** Scale-insensitive equality: 42.0 and 42.00 are the same amount. */
infix fun BigDecimal.sameValueAs(other: BigDecimal): Boolean = this.compareTo(other) == 0
