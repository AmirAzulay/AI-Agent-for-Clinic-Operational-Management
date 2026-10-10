package com.clinicagent.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "medications")
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String name;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int threshold;

    protected Medication() {
    }

    public Medication(String name, int stock, int threshold) {
        this.name = name;
        this.stock = stock;
        this.threshold = threshold;
    }

    public boolean isBelowThreshold() {
        return stock < threshold;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getThreshold() {
        return threshold;
    }
}
