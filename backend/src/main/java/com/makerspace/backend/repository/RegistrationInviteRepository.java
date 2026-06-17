package com.makerspace.backend.repository;

import com.makerspace.backend.model.RegistrationInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegistrationInviteRepository extends JpaRepository<RegistrationInvite, Long> {

    Optional<RegistrationInvite> findByTokenHash(String tokenHash);
}