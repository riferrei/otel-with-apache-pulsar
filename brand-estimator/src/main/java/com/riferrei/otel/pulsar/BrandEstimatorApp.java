package com.riferrei.otel.pulsar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.riferrei.otel.pulsar.domain.Brand;
import com.riferrei.otel.pulsar.domain.BrandRepository;

@SpringBootApplication
public class BrandEstimatorApp {

	private static final Logger logger = LoggerFactory.getLogger(BrandEstimatorApp.class);

	public static void main(String[] args) {
		SpringApplication.run(BrandEstimatorApp.class, args);
		logger.info("Starting the Brand Estimator Application");
	}

	@Autowired
	private BrandRepository brandRepository;

	@Bean
	CommandLineRunner cmdRunner() {
		return args -> {
			brandRepository.save(new Brand("ferrari", 250000));
			brandRepository.save(new Brand("mercedes", 105000));
			brandRepository.save(new Brand("nissan", 35000));
			brandRepository.save(new Brand("ford", 25000));
			brandRepository.save(new Brand("toyota", 15000));
		};
	}

}
