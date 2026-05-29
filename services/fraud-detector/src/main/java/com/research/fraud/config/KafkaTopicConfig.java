package com.research.fraud.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.research.fraud.config.Constants.SINGLE_SERVICE_COMPLETION_TOPIC;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic consortiumServiceCMDTopic() {
        return TopicBuilder.name("Consortium_Service_CMD")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic consortiumServiceResponseTopic() {
        return TopicBuilder.name("Consortium_Service_Response")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic imageServiceCMDTopic() {
        return TopicBuilder.name("Image_Service_CMD")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic imageServiceResponseTopic() {
        return TopicBuilder.name("Image_Service_Response")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ruleBasedServiceCMDTopic() {
        return TopicBuilder.name("Rule_based_Service_CMD")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ruleBasedServiceResponseTopic() {
        return TopicBuilder.name("Rule_based_Service_Response")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic mlServiceCMDTopic() {
        return TopicBuilder.name("ML_Service_CMD")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic mlServiceResponseTopic() {
        return TopicBuilder.name("ML_Service_Response")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic singleServiceCompletionTopic() {
        return TopicBuilder.name(SINGLE_SERVICE_COMPLETION_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
