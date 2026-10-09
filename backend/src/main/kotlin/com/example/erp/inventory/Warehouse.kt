package com.example.erp.inventory

import com.example.erp.shared.error.requireRule
import com.example.erp.shared.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/** A physical stock location. Like other master data it is deactivated, never deleted. */
@Entity
@Table(name = "warehouse")
class Warehouse(code: String, name: String) : BaseEntity() {

    @Column(nullable = false, updatable = false, length = 16)
    var code: String = code.trim().uppercase()
        protected set

    @Column(nullable = false, length = 200)
    var name: String = name.trim()
        protected set

    @Column(nullable = false)
    var active: Boolean = true
        protected set

    init {
        requireRule(this.code.isNotEmpty()) { "Warehouse code must not be blank" }
        requireRule(this.name.isNotEmpty()) { "Warehouse name must not be blank" }
    }

    fun activate() {
        active = true
    }

    fun deactivate() {
        active = false
    }
}
