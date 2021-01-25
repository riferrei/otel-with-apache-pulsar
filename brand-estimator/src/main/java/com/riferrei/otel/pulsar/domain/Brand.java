package com.riferrei.otel.pulsar.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash("Brand")
public class Brand {

    @Id private String brand;
    private double price;

    public Brand() {}

    public Brand(String brand, double price) {
        setBrand(brand);
        setPrice(price);
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Brand [brand=" + brand + ", price=" + price + "]";
    }
    
}
