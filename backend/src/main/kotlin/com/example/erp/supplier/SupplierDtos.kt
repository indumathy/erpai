package com.example.erp.supplier

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateSupplierRequest(
    @field:NotBlank
    @field:Size(max = 32)
    @field:Pattern(regexp = "[A-Za-z0-9-]+", message = "may only contain letters, digits and '-'")
    val supplierNumber: String,

    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 32)
    val vatId: String? = null,

    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String,

    @field:Min(0)
    @field:Max(365)
    val paymentTermsDays: Int,
)

/** The supplier number is the immutable business key and therefore not updatable. */
data class UpdateSupplierRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 32)
    val vatId: String? = null,

    @field:Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code such as EUR")
    val currency: String,

    @field:Min(0)
    @field:Max(365)
    val paymentTermsDays: Int,
)

data class SupplierResponse(
    val id: Long,
    val supplierNumber: String,
    val name: String,
    val vatId: String?,
    val currency: String,
    val paymentTermsDays: Int,
    val active: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

fun Supplier.toResponse() = SupplierResponse(
    id = requireId(),
    supplierNumber = supplierNumber,
    name = name,
    vatId = vatId,
    currency = currency,
    paymentTermsDays = paymentTermsDays,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
