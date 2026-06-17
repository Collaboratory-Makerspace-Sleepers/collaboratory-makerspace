package com.makerspace.backend.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Address {

    public enum Label { HOME, MAILING, BILLING, BUSINESS }

    @Enumerated(EnumType.STRING)
    private Label label;

    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    public Address(Label label, String street, String city, String state, String zipCode, String country) {
        this.label = label;
        this.street = street;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.country = country;
    }
}