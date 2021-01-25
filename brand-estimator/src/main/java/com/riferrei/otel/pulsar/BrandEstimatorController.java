package com.riferrei.otel.pulsar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClientException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.opentelemetry.extension.annotations.WithSpan;

import com.riferrei.otel.pulsar.domain.Brand;
import com.riferrei.otel.pulsar.domain.BrandRepository;

@RestController
public class BrandEstimatorController {

    private final Logger logger = LoggerFactory.getLogger(BrandEstimatorController.class);

    @Autowired
    private BrandRepository brandRepository;

    @RequestMapping(method = RequestMethod.GET, value = "/estimate")
    public ResponseEntity<Brand> estimate(@RequestParam String brand) {
        brand = brand.toLowerCase();
        if (brandRepository.existsById(brand)) {
            Brand brandEntity = brandRepository.findById(brand).get();
            feedAnalytics(brandEntity);
            return ResponseEntity.ok(brandEntity);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Autowired
    private Producer<Brand> producer;

    @WithSpan
    private void feedAnalytics(Brand brandEntity) {
        try {
            producer.send(brandEntity);
        } catch (PulsarClientException pce) {
            logger.debug(pce.getMessage());
        }
    }

}
