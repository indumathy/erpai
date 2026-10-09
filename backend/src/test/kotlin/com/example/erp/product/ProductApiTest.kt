package com.example.erp.product

import com.example.erp.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@IntegrationTest
class ProductApiTest(@Autowired private val mvc: MockMvc) {

    private fun createProduct(sku: String) =
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sku":"$sku","name":"Hex bolt M8x40","unitOfMeasure":"PIECE"}"""
        }

    @Test
    fun create_normalisesSku() {
        createProduct("bolt-m8-40").andExpect {
            status { isCreated() }
            jsonPath("$.sku") { value("BOLT-M8-40") }
            jsonPath("$.unitOfMeasure") { value("PIECE") }
        }
    }

    @Test
    fun duplicateSku_ignoringCase_returns409() {
        createProduct("BOLT-M8-40").andExpect { status { isCreated() } }
        createProduct("bolt-m8-40").andExpect { status { isConflict() } }
    }

    @Test
    fun unknownUnitOfMeasure_returns400() {
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"sku":"X1","name":"X","unitOfMeasure":"BUCKET"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun update_changesDetailsButNotSku() {
        val id = createProduct("BOLT-M8-40").andReturn().response.getHeader("Location")!!.substringAfterLast('/')

        mvc.put("/api/products/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Hex bolt M8x40, zinc","description":"DIN 933","unitOfMeasure":"BOX"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.sku") { value("BOLT-M8-40") }
            jsonPath("$.unitOfMeasure") { value("BOX") }
        }
    }
}
