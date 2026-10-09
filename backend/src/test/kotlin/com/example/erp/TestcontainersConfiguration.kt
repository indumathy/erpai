package com.example.erp

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Starts a real PostgreSQL (same major version as docker-compose) for integration tests.
 * @ServiceConnection wires its JDBC URL/credentials into Spring, overriding application.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
}
