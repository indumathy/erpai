package com.example.erp

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ErpApplicationTests {

    @Test
    fun contextLoads() {
        // Boots Spring, runs Flyway against a real PostgreSQL and validates the JPA mappings.
    }
}
