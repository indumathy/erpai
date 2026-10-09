package com.example.erp.supplier

import com.example.erp.shared.error.ConflictException
import com.example.erp.shared.error.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SupplierService(private val suppliers: SupplierRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: CreateSupplierRequest): Supplier {
        val number = request.supplierNumber.trim()
        if (suppliers.existsBySupplierNumber(number)) {
            throw ConflictException("Supplier number $number already exists")
        }
        val supplier = suppliers.save(
            Supplier(
                supplierNumber = number,
                name = request.name,
                vatId = request.vatId,
                currency = request.currency,
                paymentTermsDays = request.paymentTermsDays,
            ),
        )
        log.atInfo().addKeyValue("supplierId", supplier.id).log("Supplier created")
        return supplier
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Supplier =
        suppliers.findByIdOrNull(id) ?: throw NotFoundException("Supplier $id not found")

    @Transactional(readOnly = true)
    fun list(active: Boolean?): List<Supplier> {
        val sort = Sort.by(Supplier::supplierNumber.name)
        return if (active == null) suppliers.findAll(sort) else suppliers.findAllByActive(active, sort)
    }

    fun update(id: Long, request: UpdateSupplierRequest): Supplier =
        get(id).apply {
            updateDetails(request.name, request.vatId, request.currency, request.paymentTermsDays)
        }

    fun activate(id: Long): Supplier = get(id).apply { activate() }

    fun deactivate(id: Long): Supplier =
        get(id).apply {
            deactivate()
            log.atInfo().addKeyValue("supplierId", id).log("Supplier deactivated")
        }
}
