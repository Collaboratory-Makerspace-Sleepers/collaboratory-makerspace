package com.makerspace.backend.repository;

import com.makerspace.backend.model.PaymentRecord;
import com.makerspace.backend.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    long countByStatus(PaymentStatus status);
    List<PaymentRecord> findByUserIdOrderByOccurredAtDesc(Long userId);
    Optional<PaymentRecord> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<PaymentRecord> findByStripeInvoiceId(String stripeInvoiceId);
    Optional<PaymentRecord> findByReservationId(Long reservationId);
    Optional<PaymentRecord> findByStripeChargeId(String stripeChargeId);
}
