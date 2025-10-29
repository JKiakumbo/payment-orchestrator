package dev.jkiakumbo.paymentorchestrator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry

@SpringBootApplication
@EnableRetry
class FundsServiceApplication

fun main(args: Array<String>) {
    runApplication<FundsServiceApplication>(*args)
}