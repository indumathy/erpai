package com.example.erp.product

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {

    fun existsBySku(sku: String): Boolean

    fun findAllByActive(active: Boolean, sort: Sort): List<Product>
}
