package com.example.kafkamiddleware.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-group"
})
class KafkaConfigTest {

    @Autowired(required = false)
    private ProducerFactory<String, String> producerFactory;
    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired(required = false)
    private ConsumerFactory<String, String> consumerFactory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void kafkaBeans_presentWhenPropertyEnabled() {
        assertNotNull(producerFactory);
        assertNotNull(kafkaTemplate);
        assertNotNull(consumerFactory);
    }


}
