package com.makerspace.backend.repository;

import com.makerspace.backend.model.UnregisteredCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnregisteredCardRepository extends JpaRepository<UnregisteredCard, Long> {
    Optional<UnregisteredCard> findByCardUid(String cardUid);

    Page<UnregisteredCard> findByStatus(String status, Pageable pageable);

    Page<UnregisteredCard> findByStatusOrderByLastScannedAtDesc(String status, Pageable pageable);

    List<UnregisteredCard> findByAssignedToEmail(String email);

    Optional<UnregisteredCard> findByOtpCode(String otpCode);

    Page<UnregisteredCard> findByOtpExpiresAtAfterOrderByLastScannedAtDesc(Instant time, Pageable pageable);
}
