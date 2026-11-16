package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.repository.EquipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EquipmentService {

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private MembershipService membershipService;

    public List<Equipment> findAll() {
        return equipmentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    public List<Equipment> forViewer(List<Equipment> equipment, Long userId) {
        boolean member = userId != null && membershipService.hasActiveMembership(userId);
        boolean studio = userId != null && membershipService.hasMachineRateWaiver(userId);
        equipment.forEach(item -> item.setResolvedHourlyRateCents(resolveRate(item, member, studio)));
        return equipment;
    }

    public static int resolveRate(Equipment equipment, boolean member, boolean studio) {
        if (studio || (member && equipment.isFreeWithMembership())) return 0;
        if (member && "CLASS".equals(equipment.getCategory())) return equipment.getHourlyRateCents() / 2;
        return equipment.getHourlyRateCents();
    }

    public Equipment findById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Equipment not found"));
    }

    public List<Equipment> findByStatus(EquipmentStatus status) {
        return equipmentRepository.findByStatus(status);
    }

    public List<Equipment> findByCategory(String category) {
        return equipmentRepository.findByCategory(category);
    }

    public List<Equipment> search(String query) {
        return equipmentRepository.findByNameContainingIgnoreCase(query);
    }

    public Equipment create(Equipment equipment) {
        validate(equipment);
        equipment.setId(null);
        equipment.setCreatedAt(java.time.LocalDateTime.now());
        equipment.setMemberRateCents(equipment.getHourlyRateCents());
        return equipmentRepository.save(equipment);
    }

    public Equipment update(Long id, Equipment updated) {
        validate(updated);
        Equipment existing = findById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setImageUrl(updated.getImageUrl());
        existing.setStatus(updated.getStatus());
        existing.setRequiresTraining(updated.isRequiresTraining());
        existing.setRequiresWaiver(updated.isRequiresWaiver());
        existing.setTrainingVideoUrl(updated.getTrainingVideoUrl());
        existing.setHourlyRateCents(updated.getHourlyRateCents());
        existing.setMemberRateCents(updated.getHourlyRateCents());
        existing.setFreeWithMembership(updated.isFreeWithMembership());
        existing.setCurrentWaiverVersion(updated.getCurrentWaiverVersion());
        return equipmentRepository.save(existing);
    }

    private void validate(Equipment equipment) {
        if (equipment.getName() == null || equipment.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Equipment name is required");
        }
        if (equipment.getHourlyRateCents() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hourly rate cannot be negative");
        }
        if (equipment.isRequiresTraining()) {
            try {
                var video = java.net.URI.create(equipment.getTrainingVideoUrl() == null
                        ? "" : equipment.getTrainingVideoUrl().trim());
                if (!("http".equals(video.getScheme()) || "https".equals(video.getScheme())) || video.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A valid HTTP or HTTPS training video URL is required when training is enabled");
            }
        }
        equipment.setName(equipment.getName().trim());
        if (equipment.getCategory() != null) equipment.setCategory(equipment.getCategory().trim());
        if (equipment.getTrainingVideoUrl() != null) equipment.setTrainingVideoUrl(equipment.getTrainingVideoUrl().trim());
    }

    public Equipment updateStatus(Long id, EquipmentStatus status) {
        Equipment equipment = findById(id);
        equipment.setStatus(status);
        return equipmentRepository.save(equipment);
    }

    public void delete(Long id) {
        Equipment equipment = findById(id);
        equipmentRepository.delete(equipment);
    }
}
