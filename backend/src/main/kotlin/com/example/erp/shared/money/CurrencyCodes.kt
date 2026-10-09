package com.example.erp.shared.money

import java.util.Currency

object CurrencyCodes {

    /** True if [code] is a known ISO 4217 currency code such as EUR, USD, CHF. */
    fun isValid(code: String): Boolean =
        runCatching { Currency.getInstance(code) }.isSuccess
}
