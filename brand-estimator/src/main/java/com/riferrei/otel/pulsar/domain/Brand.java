package com.riferrei.otel.pulsar.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash("Brand")
public class Brand {

    @Id private String id;
    private double price;

    public Brand() {}

    public Brand(String id, double price) {
        setId(id);
        setPrice(price);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Brand [id=" + id + ", price=" + price + "]";
    }
    
}
