package com.makerspace.backend.repository;

import com.makerspace.backend.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    List<PaymentRecord> findByUserIdOrderByOccurredAtDesc(Long userId);
    Optional<PaymentRecord> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<PaymentRecord> findByStripeInvoiceId(String stripeInvoiceId);
}
