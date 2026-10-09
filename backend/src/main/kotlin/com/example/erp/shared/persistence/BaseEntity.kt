package com.example.erp.shared.persistence

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

/**
 * Technical identity + audit timestamps shared by all entities.
 * Business keys (supplierNumber, sku, poNumber, ...) live on the concrete entities.
 */
@MappedSuperclass
abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    @PrePersist
    protected fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** Non-null id for persisted entities; fails loudly if called before the entity was saved. */
    fun requireId(): Long = checkNotNull(id) { "${javaClass.simpleName} has not been persisted yet" }
}
