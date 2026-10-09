package com.example.erp.product

import com.example.erp.shared.error.ConflictException
import com.example.erp.shared.error.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProductService(private val products: ProductRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(request: CreateProductRequest): Product {
        val product = Product(
            sku = request.sku,
            name = request.name,
            description = request.description,
            unitOfMeasure = request.unitOfMeasure,
        )
        // Check after construction so we compare the normalised SKU ("bolt-m8" -> "BOLT-M8").
        if (products.existsBySku(product.sku)) {
            throw ConflictException("SKU ${product.sku} already exists")
        }
        products.save(product)
        log.atInfo().addKeyValue("productId", product.id).log("Product created")
        return product
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Product =
        products.findByIdOrNull(id) ?: throw NotFoundException("Product $id not found")

    @Transactional(readOnly = true)
    fun list(active: Boolean?): List<Product> {
        val sort = Sort.by(Product::sku.name)
        return if (active == null) products.findAll(sort) else products.findAllByActive(active, sort)
    }

    fun update(id: Long, request: UpdateProductRequest): Product =
        get(id).apply { updateDetails(request.name, request.description, request.unitOfMeasure) }

    fun activate(id: Long): Product = get(id).apply { activate() }

    fun deactivate(id: Long): Product =
        get(id).apply {
            deactivate()
            log.atInfo().addKeyValue("productId", id).log("Product deactivated")
        }
}
