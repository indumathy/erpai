package com.example.erp.goodsreceipt

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/goods-receipts")
class GoodsReceiptController(private val service: GoodsReceiptService) {

    @PostMapping
    fun post(@Valid @RequestBody request: CreateGoodsReceiptRequest): ResponseEntity<GoodsReceiptResponse> {
        val gr = service.post(request)
        return ResponseEntity.created(URI("/api/goods-receipts/${gr.id}")).body(gr)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GoodsReceiptResponse = service.get(id)

    @GetMapping
    fun list(@RequestParam(required = false) purchaseOrderId: Long?): List<GoodsReceiptSummaryResponse> =
        service.list(purchaseOrderId)
}
