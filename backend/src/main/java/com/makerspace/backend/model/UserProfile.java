package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "photo_url")
    private String photoUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_phone_numbers", joinColumns = @JoinColumn(name = "profile_id"))
    private List<PhoneNumber> phoneNumbers = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_addresses", joinColumns = @JoinColumn(name = "profile_id"))
    private List<Address> addresses = new ArrayList<>();
}