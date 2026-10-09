package com.example.erp

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

/**
 * Full Spring context + real PostgreSQL (Testcontainers) + MockMvc.
 * All classes using this annotation share one cached context and one container.
 * @Transactional rolls back every test, so tests don't see each other's data.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@Transactional
annotation class IntegrationTest
