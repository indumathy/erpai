package com.example.erp.product

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateProductRequest(
    @field:NotBlank
    @field:Size(max = 64)
    @field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, '.', '_' and '-'")
    val sku: String,

    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 2000)
    val description: String? = null,

    val unitOfMeasure: UnitOfMeasure,
)

/** The SKU is the immutable business key and therefore not updatable. */
data class UpdateProductRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 2000)
    val description: String? = null,

    val unitOfMeasure: UnitOfMeasure,
)

data class ProductResponse(
    val id: Long,
    val sku: String,
    val name: String,
    val description: String?,
    val unitOfMeasure: UnitOfMeasure,
    val active: Boolean,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

fun Product.toResponse() = ProductResponse(
    id = requireId(),
    sku = sku,
    name = name,
    description = description,
    unitOfMeasure = unitOfMeasure,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
