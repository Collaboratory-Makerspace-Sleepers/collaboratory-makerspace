package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@Table(
    name = "class_session",
    indexes = {
        @Index(name = "idx_class_session_start_time", columnList = "start_time"),
        @Index(name = "idx_class_session_status",     columnList = "status"),
        @Index(name = "idx_class_session_instructor", columnList = "instructor_id")
    }
)
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private User instructor;

    @Column(name = "instructor_id", insertable = false, updatable = false)
    private Long instructorId;

    @Column(name = "start_time", nullable = false)
    private ZonedDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private ZonedDateTime endTime;

    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity = 10;

    @Column(name = "price_cents", nullable = false)
    private int priceCents = 0;

    @Column(name = "member_price_cents", nullable = false)
    private int memberPriceCents = 0;

    @Column(nullable = false)
    private String status = "SCHEDULED";

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 255)
    private String location;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();
}
