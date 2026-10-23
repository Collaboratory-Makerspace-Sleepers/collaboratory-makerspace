package com.makerspace.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Completed waiver PDF that staff attached to a waiver request. */
@Entity
@Getter
@Setter
@Table(name = "waiver_documents")
public class WaiverDocument {

    @Id
    @Column(name = "check_in_id")
    private Long checkInId;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
}
