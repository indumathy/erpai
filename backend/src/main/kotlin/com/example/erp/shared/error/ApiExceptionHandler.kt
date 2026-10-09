package com.example.erp.shared.error

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Translates exceptions into RFC 9457 `application/problem+json` responses.
 * Controllers never build error responses themselves.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Resource not found", ex.message)

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Conflict", ex.message)

    @ExceptionHandler(BusinessRuleViolationException::class)
    fun handleBusinessRule(ex: BusinessRuleViolationException): ProblemDetail =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", ex.message)

    /**
     * Safety net for races the service-level checks cannot prevent, e.g. two concurrent
     * requests creating the same supplier number: the database UNIQUE constraint wins.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data")

    /** Someone else changed the same document (@Version mismatch) between our read and write. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Concurrent modification", "The document was changed by someone else. Reload and try again.")

    /** Bean Validation failures: list every invalid field so API clients can fix all at once. */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request contains invalid fields")
        body.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to it.defaultMessage) },
        )
        return ResponseEntity.badRequest().body(body)
    }

    private fun problem(status: HttpStatus, title: String, detail: String?): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail ?: title).apply { this.title = title }
}
