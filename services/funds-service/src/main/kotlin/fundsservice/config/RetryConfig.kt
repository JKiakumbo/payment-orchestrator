package dev.jkiakumbo.paymentorchestrator.fundsservice.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class RetryConfig {
    // Retry is enabled at application level with @EnableRetry
}