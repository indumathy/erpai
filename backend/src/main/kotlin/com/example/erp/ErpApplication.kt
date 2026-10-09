package com.example.erp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ErpApplication

fun main(args: Array<String>) {
    runApplication<ErpApplication>(*args)
}
