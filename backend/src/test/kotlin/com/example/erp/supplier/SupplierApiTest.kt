package com.example.erp.supplier

import com.example.erp.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@IntegrationTest
class SupplierApiTest(@Autowired private val mvc: MockMvc) {

    private fun createSupplier(number: String = "S-1000", currency: String = "EUR") =
        mvc.post("/api/suppliers") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"supplierNumber":"$number","name":"Acme Industrial GmbH",
                 "vatId":"DE123456789","currency":"$currency","paymentTermsDays":30}
            """
        }

    @Test
    fun create_thenGet() {
        val location = createSupplier()
            .andExpect {
                status { isCreated() }
                jsonPath("$.supplierNumber") { value("S-1000") }
                jsonPath("$.active") { value(true) }
            }
            .andReturn().response.getHeader("Location")!!

        mvc.get(location).andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Acme Industrial GmbH") }
        }
    }

    @Test
    fun duplicateSupplierNumber_returns409() {
        createSupplier().andExpect { status { isCreated() } }
        createSupplier().andExpect { status { isConflict() } }
    }

    @Test
    fun invalidRequest_returns400WithFieldErrors() {
        mvc.post("/api/suppliers") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"supplierNumber":"","name":"X","currency":"euro","paymentTermsDays":-5}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.length()") { value(3) }
        }
    }

    @Test
    fun unknownIsoCurrency_returns422() {
        // Passes the [A-Z]{3} format check, fails the domain rule.
        createSupplier(currency = "ABC").andExpect { status { isUnprocessableContent() } }
    }

    @Test
    fun unknownId_returns404() {
        mvc.get("/api/suppliers/999999").andExpect { status { isNotFound() } }
    }

    @Test
    fun deactivate_hidesFromActiveList() {
        val id = createSupplier().andReturn().response.getHeader("Location")!!.substringAfterLast('/')

        mvc.post("/api/suppliers/$id/deactivate").andExpect { jsonPath("$.active") { value(false) } }

        mvc.get("/api/suppliers?active=true").andExpect { jsonPath("$.length()") { value(0) } }
        mvc.get("/api/suppliers?active=false").andExpect { jsonPath("$.length()") { value(1) } }
    }
}
