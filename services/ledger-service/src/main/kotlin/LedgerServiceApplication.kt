package dev.jkiakumbo.paymentorchestrator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableRetry
@EnableScheduling
class LedgerServiceApplication

fun main(args: Array<String>) {
    runApplication<LedgerServiceApplication>(*args)
}