package com.riferrei.otel.pulsar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.riferrei.otel.pulsar.OTelProducerInterceptor;
import com.riferrei.otel.pulsar.domain.Brand;

@Configuration
public class PulsarConfiguration {

	private final Logger logger = LoggerFactory.getLogger(PulsarConfiguration.class);

	@Value("${pulsar.service.url}")
	private String pulsarServiceURL;

	@Bean
	public PulsarClient pulsarClient() throws PulsarClientException {
		logger.info("Connecting to Pulsar: " + pulsarServiceURL);
		return PulsarClient.builder()
			.serviceUrl(pulsarServiceURL)
			.build();
	}

	@Bean
	public Producer<Brand> producer(PulsarClient pulsarClient) throws PulsarClientException {
		return pulsarClient.newProducer(Schema.JSON(Brand.class))
			.topic("estimates")
			.intercept(new OTelProducerInterceptor())
			.create();
	}

}
