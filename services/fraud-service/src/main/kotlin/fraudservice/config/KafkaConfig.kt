package dev.jkiakumbo.paymentorchestrator.fraudservice.config

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConfig {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Bean
    fun kafkaAdmin(): KafkaAdmin {
        val configs = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers
        )
        return KafkaAdmin(configs)
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "fraud-service",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
            JsonDeserializer.VALUE_DEFAULT_TYPE to "dev.jkiakumbo.fraudservice.events.FraudCheckRequestedEvent"
        )
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE

        // Configure error handler with DLQ
        val errorHandler = DefaultErrorHandler { record, exception ->
            // Send to DLQ or handle dead letters
            logger.error("Failed to process record: ${record.key()}, sending to DLQ", exception)
        }
        errorHandler.setBackOffFunction { _, _ -> FixedBackOff(1000L, 2) }
        factory.setCommonErrorHandler(errorHandler)

        return factory
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun fraudCheckRequestsTopic(): NewTopic {
        return org.springframework.kafka.config.TopicBuilder.name("fraud-check-requests")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun fraudCheckResultsTopic(): NewTopic {
        return org.springframework.kafka.config.TopicBuilder.name("fraud-check-results")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun compensationRequestsTopic(): NewTopic {
        return org.springframework.kafka.config.TopicBuilder.name("compensation-requests")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun manualReviewRequestsTopic(): NewTopic {
        return org.springframework.kafka.config.TopicBuilder.name("manual-review-requests")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun fraudServiceDlqTopic(): NewTopic {
        return org.springframework.kafka.config.TopicBuilder.name("fraud-service-dlq")
            .partitions(3)
            .replicas(1)
            .build()
    }

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(KafkaConfig::class.java)
    }
}