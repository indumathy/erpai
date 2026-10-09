package com.example.erp.purchaseorder

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
@RequestMapping("/api/purchase-orders")
class PurchaseOrderController(private val service: PurchaseOrderService) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreatePurchaseOrderRequest): ResponseEntity<PurchaseOrderResponse> {
        val po = service.create(request).toResponse()
        return ResponseEntity.created(URI("/api/purchase-orders/${po.id}")).body(po)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): PurchaseOrderResponse = service.get(id).toResponse()

    @GetMapping
    fun list(@RequestParam(required = false) status: PurchaseOrderStatus?): List<PurchaseOrderSummaryResponse> =
        service.list(status).map { it.toSummaryResponse() }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpdatePurchaseOrderRequest): PurchaseOrderResponse =
        service.update(id, request).toResponse()

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): PurchaseOrderResponse = service.approve(id).toResponse()

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: Long): PurchaseOrderResponse = service.cancel(id).toResponse()

    @PostMapping("/{id}/close")
    fun close(@PathVariable id: Long): PurchaseOrderResponse = service.close(id).toResponse()
}
