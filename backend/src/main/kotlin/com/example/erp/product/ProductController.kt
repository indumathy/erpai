package com.example.erp.product

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/products")
class ProductController(private val service: ProductService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateProductRequest): ResponseEntity<ProductResponse> {
        val product = service.create(request).toResponse()
        return ResponseEntity.created(URI("/api/products/${product.id}")).body(product)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ProductResponse = service.get(id).toResponse()

    @GetMapping
    fun list(@RequestParam(required = false) active: Boolean?): List<ProductResponse> =
        service.list(active).map { it.toResponse() }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateProductRequest): ProductResponse =
        service.update(id, request).toResponse()

    @PostMapping("/{id}/activate")
    fun activate(@PathVariable id: Long): ProductResponse = service.activate(id).toResponse()

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Long): ProductResponse = service.deactivate(id).toResponse()
}
