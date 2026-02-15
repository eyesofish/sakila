package com.example.demo.sakila.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "film")
public class Film {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "film_id", nullable = false)
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(name = "rental_rate")
    private BigDecimal rentalRate;
    private Integer length;

    protected Film() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getRentalRate() {
        return rentalRate;
    }

    public Integer getLength() {
        return length;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setRentalRate(BigDecimal rentalRate) {
        this.rentalRate = rentalRate;
    }

    public void setLength(Integer length) {
        this.length = length;
    }
}
