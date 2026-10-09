package com.example.erp.shared.error

/** A requested resource does not exist -> HTTP 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** The request conflicts with existing state, e.g. a duplicate business key -> HTTP 409. */
class ConflictException(message: String) : RuntimeException(message)

/** A domain/business rule was violated, e.g. "supplier must be active" -> HTTP 422. */
class BusinessRuleViolationException(message: String) : RuntimeException(message)
