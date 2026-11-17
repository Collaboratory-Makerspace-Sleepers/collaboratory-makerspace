package com.makerspace.backend.controller;

import com.makerspace.backend.repository.AccessEventRepository;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasAuthority('MANAGE_ROLES')")
@Transactional(readOnly = true)
public class AnalyticsController {

    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private MembershipRepository membershipRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private AccessEventRepository accessEventRepository;

    @GetMapping("/revenue")
    public Map<String, Object> revenue() {
        Instant since = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(12)
                .truncatedTo(ChronoUnit.DAYS).toInstant();

        Instant monthStart = ZonedDateTime.now(ZoneOffset.UTC)
                .withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant lastMonthStart = ZonedDateTime.now(ZoneOffset.UTC)
                .minusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Map<String, Object>> monthly = new ArrayList<>();
        for (Object[] row : paymentRecordRepository.revenueByMonthAndKind(since)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", row[0] == null ? null : row[0].toString());
            m.put("kind", row[1]);
            m.put("totalCents", row[2]);
            monthly.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("monthly", monthly);
        out.put("thisMonthCents", paymentRecordRepository.revenueInPeriod(monthStart, Instant.now()));
        out.put("lastMonthCents", paymentRecordRepository.revenueInPeriod(lastMonthStart, monthStart));
        return out;
    }

    @GetMapping("/memberships")
    public Map<String, Object> memberships() {
        Instant since = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(12)
                .truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Map<String, Object>> byPlan = new ArrayList<>();
        long totalActive = 0;
        for (Object[] row : membershipRepository.activeMembersByPlan()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", row[0]);
            m.put("displayName", row[1]);
            long count = row[2] == null ? 0L : ((Number) row[2]).longValue();
            m.put("count", count);
            totalActive += count;
            byPlan.add(m);
        }

        List<Map<String, Object>> newByMonth = new ArrayList<>();
        for (Object[] row : membershipRepository.newMembershipsByMonth(since)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", row[0] == null ? null : row[0].toString());
            m.put("count", row[1]);
            newByMonth.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalActive", totalActive);
        out.put("byPlan", byPlan);
        out.put("newByMonth", newByMonth);
        return out;
    }

    @GetMapping("/equipment")
    public Map<String, Object> equipment() {
        Instant since = ZonedDateTime.now(ZoneOffset.UTC).minusMonths(12)
                .truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] row : reservationRepository.topEquipmentByUsage()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", row[0]);
            m.put("reservations", row[1]);
            m.put("hours", row[2]);
            top.add(m);
        }

        List<Map<String, Object>> byHour = new ArrayList<>();
        for (Object[] row : reservationRepository.reservationsByHourOfDay(since)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hour", row[0]);
            m.put("count", row[1]);
            byHour.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("topEquipment", top);
        out.put("byHourOfDay", byHour);
        return out;
    }

    @GetMapping("/visits")
    public Map<String, Object> visits() {
        Instant since30 = ZonedDateTime.now(ZoneOffset.UTC).minusDays(30)
                .truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant todayStart = ZonedDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant weekStart = ZonedDateTime.now(ZoneOffset.UTC).minusDays(7)
                .truncatedTo(ChronoUnit.DAYS).toInstant();

        List<Map<String, Object>> daily = new ArrayList<>();
        long todayCount = 0;
        long weekCount = 0;
        for (Object[] row : accessEventRepository.dailyGrantedVisits(since30)) {
            Map<String, Object> m = new LinkedHashMap<>();
            String day = row[0] == null ? null : row[0].toString();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            m.put("day", day);
            m.put("count", count);
            daily.add(m);

            if (row[0] != null) {
                Instant dayInstant = Instant.parse(row[0].toString().replace(" ", "T") + "Z");
                if (!dayInstant.isBefore(todayStart)) todayCount += count;
                if (!dayInstant.isBefore(weekStart)) weekCount += count;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daily", daily);
        out.put("todayCount", todayCount);
        out.put("thisWeekCount", weekCount);
        return out;
    }
}
