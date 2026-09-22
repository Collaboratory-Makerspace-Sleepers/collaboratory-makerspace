package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.PlanDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public catalog of purchasable membership plans stored in membership_plan.
 */
@RestController
@RequestMapping("/api/v1/membership")
public class PlanController {

    private final JdbcTemplate jdbcTemplate;

    public PlanController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/plans")
    public List<PlanDTO> plans() {
        return jdbcTemplate.query(
                "SELECT code, display_name, amount_cents, billing_interval " +
                "FROM membership_plan WHERE active = TRUE ORDER BY amount_cents",
                (rs, rowNum) -> new PlanDTO(
                        rs.getString("code"),
                        rs.getString("display_name"),
                        rs.getInt("amount_cents"),
                        rs.getString("billing_interval")));
    }
}