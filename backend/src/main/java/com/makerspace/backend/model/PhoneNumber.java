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
public class PhoneNumber {

    public enum Label { PRIMARY, MOBILE, HOME, WORK, FAX }

    @Enumerated(EnumType.STRING)
    private Label label;

    private String number;

    public PhoneNumber(Label label, String number) {
        this.label = label;
        this.number = number;
    }
}
