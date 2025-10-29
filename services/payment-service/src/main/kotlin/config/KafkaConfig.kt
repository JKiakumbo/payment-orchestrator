package dev.jkiakumbo.paymentorchestrator.config

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

@Configuration
class KafkaConfig {

    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092"
        )
        return KafkaAdmin(configs)
    }

    @Bean
    fun paymentEventsTopic(): NewTopic {
        return TopicBuilder.name("payment-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun fraudEventsTopic(): NewTopic {
        return TopicBuilder.name("fraud-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun fundsEventsTopic(): NewTopic {
        return TopicBuilder.name("funds-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun processorEventsTopic(): NewTopic {
        return TopicBuilder.name("processor-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun ledgerEventsTopic(): NewTopic {
        return TopicBuilder.name("ledger-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun compensationEventsTopic(): NewTopic {
        return TopicBuilder.name("compensation-events")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun dlqTopic(): NewTopic {
        return TopicBuilder.name("payment-dlq")
            .partitions(3)
            .replicas(1)
            .build()
    }
}