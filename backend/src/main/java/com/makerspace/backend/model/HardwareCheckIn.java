package com.makerspace.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "hardware_checkins")
public class HardwareCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckInStatus status;

    @Column(nullable = false)
    private Instant checkinTime;

    @Column(name = "access_granted")
    private Boolean accessGranted;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "waiver_signed")
    private Boolean waiverSigned = false;

    @Column(name = "waiver_approved_by_admin")
    private Boolean waiverApprovedByAdmin = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByAdmin;

    @Column(name = "approval_time")
    private Instant approvalTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public HardwareCheckIn() {
    }

    public HardwareCheckIn(User user, Equipment equipment, String deviceId) {
        this.user = user;
        this.equipment = equipment;
        this.deviceId = deviceId;
        this.status = CheckInStatus.PENDING;
        this.checkinTime = Instant.now();
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public CheckInStatus getStatus() {
        return status;
    }

    public void setStatus(CheckInStatus status) {
        this.status = status;
    }

    public Instant getCheckinTime() {
        return checkinTime;
    }

    public void setCheckinTime(Instant checkinTime) {
        this.checkinTime = checkinTime;
    }

    public Boolean getAccessGranted() {
        return accessGranted;
    }

    public void setAccessGranted(Boolean accessGranted) {
        this.accessGranted = accessGranted;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Boolean getWaiverSigned() {
        return waiverSigned;
    }

    public void setWaiverSigned(Boolean waiverSigned) {
        this.waiverSigned = waiverSigned;
    }

    public Boolean getWaiverApprovedByAdmin() {
        return waiverApprovedByAdmin;
    }

    public void setWaiverApprovedByAdmin(Boolean waiverApprovedByAdmin) {
        this.waiverApprovedByAdmin = waiverApprovedByAdmin;
    }

    public User getApprovedByAdmin() {
        return approvedByAdmin;
    }

    public void setApprovedByAdmin(User approvedByAdmin) {
        this.approvedByAdmin = approvedByAdmin;
    }

    public Instant getApprovalTime() {
        return approvalTime;
    }

    public void setApprovalTime(Instant approvalTime) {
        this.approvalTime = approvalTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public enum CheckInStatus {
        PENDING,
        SUCCESS,
        FAILED,
        NEEDS_WAIVER,
        WAIVER_SIGNED,
        WAIVER_APPROVED,
        DENIED
    }
}
