package com.makerspace.backend.repository;

import com.makerspace.backend.model.PaymentRecord;
import com.makerspace.backend.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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
    @Query(nativeQuery = true, value = """
            SELECT DATE_TRUNC('month', occurred_at) AS month, kind, SUM(amount_cents) AS total
            FROM payment_record WHERE status='SUCCEEDED' AND occurred_at >= :since
            GROUP BY month, kind ORDER BY month
            """)
    List<Object[]> revenueByMonthAndKind(@Param("since") Instant since);

    @Query(nativeQuery = true, value = """
            SELECT COALESCE(SUM(amount_cents),0) FROM payment_record
            WHERE status='SUCCEEDED' AND occurred_at >= :start AND occurred_at < :end
            """)
    Long revenueInPeriod(@Param("start") Instant start, @Param("end") Instant end);
}
