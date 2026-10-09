package com.example.erp.supplier

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository

interface SupplierRepository : JpaRepository<Supplier, Long> {

    fun existsBySupplierNumber(supplierNumber: String): Boolean

    fun findAllByActive(active: Boolean, sort: Sort): List<Supplier>
}
