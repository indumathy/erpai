package com.example.erp.invoice

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/invoices")
class SupplierInvoiceController(private val service: SupplierInvoiceService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateSupplierInvoiceRequest): ResponseEntity<SupplierInvoiceResponse> {
        val invoice = service.create(request)
        return ResponseEntity.created(URI("/api/invoices/${invoice.id}")).body(invoice)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): SupplierInvoiceResponse = service.get(id)

    @GetMapping
    fun list(): List<SupplierInvoiceSummaryResponse> = service.list()

    @GetMapping("/{id}/match")
    fun match(@PathVariable id: Long): MatchResultResponse = service.match(id)
}
