package com.example.erp.shared.error

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Like Kotlin's `require`, but signals a business rule violation (HTTP 422)
 * instead of an IllegalArgumentException (which would surface as HTTP 500).
 */
@OptIn(ExperimentalContracts::class)
inline fun requireRule(condition: Boolean, message: () -> String) {
    contract { returns() implies condition }
    if (!condition) throw BusinessRuleViolationException(message())
}
