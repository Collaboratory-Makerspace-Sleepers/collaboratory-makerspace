package com.makerspace.backend.controller.dto;

import com.makerspace.backend.model.HardwareCheckIn;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class HardwareCheckInDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private Long equipmentId;
    private String equipmentName;
    private String status;
    private Instant checkinTime;
    private Boolean accessGranted;
    private String errorMessage;
    private String deviceId;
    private Boolean waiverSigned;
    private Boolean waiverApprovedByAdmin;
    private Boolean hasSignature;
    private Long approvedByUserId;
    private String approvedByUserName;
    private Instant approvalTime;

    public HardwareCheckInDTO(HardwareCheckIn checkIn) {
        this.id = checkIn.getId();
        this.userId = checkIn.getUser().getId();
        this.userName = checkIn.getUser().getProfile() != null 
            ? checkIn.getUser().getProfile().getFirstName() + " " + 
              (checkIn.getUser().getProfile().getLastName() != null ? checkIn.getUser().getProfile().getLastName() : "")
            : checkIn.getUser().getEmail();
        this.userEmail = checkIn.getUser().getEmail();
        this.equipmentId = checkIn.getEquipment().getId();
        this.equipmentName = checkIn.getEquipment().getName();
        this.status = checkIn.getStatus().toString();
        this.checkinTime = checkIn.getCheckinTime();
        this.accessGranted = checkIn.getAccessGranted();
        this.errorMessage = checkIn.getErrorMessage();
        this.deviceId = checkIn.getDeviceId();
        this.waiverSigned = checkIn.getWaiverSigned();
        this.waiverApprovedByAdmin = checkIn.getWaiverApprovedByAdmin();
        this.hasSignature = checkIn.getSignatureData() != null && !checkIn.getSignatureData().isBlank();
        this.approvalTime = checkIn.getApprovalTime();
        
        if (checkIn.getApprovedByAdmin() != null) {
            this.approvedByUserId = checkIn.getApprovedByAdmin().getId();
            this.approvedByUserName = checkIn.getApprovedByAdmin().getProfile() != null 
                ? checkIn.getApprovedByAdmin().getProfile().getFirstName()
                : checkIn.getApprovedByAdmin().getEmail();
        }
    }

    public HardwareCheckInDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Boolean getHasSignature() { return hasSignature; }
    public void setHasSignature(Boolean hasSignature) { this.hasSignature = hasSignature; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public Long getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) { this.equipmentName = equipmentName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCheckinTime() { return checkinTime; }
    public void setCheckinTime(Instant checkinTime) { this.checkinTime = checkinTime; }

    public Boolean getAccessGranted() { return accessGranted; }
    public void setAccessGranted(Boolean accessGranted) { this.accessGranted = accessGranted; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Boolean getWaiverSigned() { return waiverSigned; }
    public void setWaiverSigned(Boolean waiverSigned) { this.waiverSigned = waiverSigned; }

    public Boolean getWaiverApprovedByAdmin() { return waiverApprovedByAdmin; }
    public void setWaiverApprovedByAdmin(Boolean waiverApprovedByAdmin) { this.waiverApprovedByAdmin = waiverApprovedByAdmin; }

    public Long getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(Long approvedByUserId) { this.approvedByUserId = approvedByUserId; }

    public String getApprovedByUserName() { return approvedByUserName; }
    public void setApprovedByUserName(String approvedByUserName) { this.approvedByUserName = approvedByUserName; }

    public Instant getApprovalTime() { return approvalTime; }
    public void setApprovalTime(Instant approvalTime) { this.approvalTime = approvalTime; }
}
