package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.CurrentMembershipDTO;
import com.makerspace.backend.controller.dto.SelectPlanRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reads the caller's current membership from the billing tables.
 * Members with no active subscription fall back to the GUEST plan
 * (the default assigned to new accounts).
 */
@RestController
@RequestMapping("/api/v1/users/me/membership")
public class MembershipController {

    private final JdbcTemplate jdbcTemplate;

    public MembershipController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public CurrentMembershipDTO current(Authentication auth) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).userId();

        return jdbcTemplate.query(
                """
                SELECT mp.code, mp.display_name, mp.amount_cents
                FROM membership m
                JOIN membership_plan mp ON mp.id = m.plan_id
                WHERE m.user_id = ? AND m.status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE')
                ORDER BY m.created_at DESC
                LIMIT 1
                """,
                rs -> rs.next()
                        ? new CurrentMembershipDTO(
                                rs.getString("code"),
                                rs.getString("display_name"),
                                rs.getInt("amount_cents"),
                                true)
                        : new CurrentMembershipDTO("GUEST", "Guest", 0, false),
                userId);
    }

    /**
     * Selects a membership plan for the caller. Passing GUEST (or any unknown value
     * rejected by validation) deactivates an existing subscription so the user
     * falls back to the default Guest plan.
     */
    @PostMapping
    public CurrentMembershipDTO select(@Valid @RequestBody SelectPlanRequest req, Authentication auth) {
        Long userId = ((UserPrincipal) auth.getPrincipal()).userId();
        String code = req.planCode().toUpperCase();

        if ("GUEST".equals(code)) {
            jdbcTemplate.update(
                    "UPDATE membership SET status = 'CANCELLED', canceled_at = NOW(), updated_at = NOW() " +
                    "WHERE user_id = ? AND status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE')",
                    userId);
            return new CurrentMembershipDTO("GUEST", "Guest", 0, false);
        }

        Long planId = jdbcTemplate.query(
                "SELECT id FROM membership_plan WHERE code = ? AND active = TRUE LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                code);
        if (planId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown plan: " + code);
        }

        int updated = jdbcTemplate.update(
                """
                UPDATE membership
                SET plan_id = ?, status = 'ACTIVE', current_period_start = NOW(),
                    current_period_end = NOW() + INTERVAL '1 month', updated_at = NOW()
                WHERE user_id = ? AND status IN ('ACTIVE','PAST_DUE','TRIALING','GRACE')
                """,
                planId, userId);
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO membership (user_id, plan_id, status, current_period_start,
                                            current_period_end, created_at, updated_at)
                    VALUES (?, ?, 'ACTIVE', NOW(), NOW() + INTERVAL '1 month', NOW(), NOW())
                    """,
                    userId, planId);
        }

        return current(auth);
    }
}