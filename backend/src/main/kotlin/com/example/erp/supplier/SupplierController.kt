package com.example.erp.supplier

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
@RequestMapping("/api/suppliers")
class SupplierController(private val service: SupplierService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateSupplierRequest): ResponseEntity<SupplierResponse> {
        val supplier = service.create(request).toResponse()
        return ResponseEntity.created(URI("/api/suppliers/${supplier.id}")).body(supplier)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): SupplierResponse = service.get(id).toResponse()

    @GetMapping
    fun list(@RequestParam(required = false) active: Boolean?): List<SupplierResponse> =
        service.list(active).map { it.toResponse() }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdateSupplierRequest): SupplierResponse =
        service.update(id, request).toResponse()

    @PostMapping("/{id}/activate")
    fun activate(@PathVariable id: Long): SupplierResponse = service.activate(id).toResponse()

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Long): SupplierResponse = service.deactivate(id).toResponse()
}
