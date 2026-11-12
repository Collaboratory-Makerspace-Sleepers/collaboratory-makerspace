package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.repository.EquipmentRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductUpdateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class EquipmentService {

    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private StripeClient stripeClient;

    public List<Equipment> findAll() {
        return equipmentRepository.findAll();
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
        Equipment saved = equipmentRepository.save(equipment);
        provisionStripeProduct(saved);
        return saved;
    }

    public Equipment update(Long id, Equipment updated) {
        Equipment existing = findById(id);

        String oldName        = existing.getName();
        String oldDescription = existing.getDescription();
        EquipmentStatus oldStatus = existing.getStatus();

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setImageUrl(updated.getImageUrl());
        existing.setStatus(updated.getStatus());
        existing.setRequiresTraining(updated.isRequiresTraining());
        existing.setRequiresWaiver(updated.isRequiresWaiver());
        existing.setTrainingVideoUrl(updated.getTrainingVideoUrl());
        existing.setCurrentWaiverVersion(updated.getCurrentWaiverVersion());
        existing.setHourlyRateCents(updated.getHourlyRateCents());
        existing.setMemberRateCents(updated.getMemberRateCents());
        existing.setFreeWithMembership(updated.isFreeWithMembership());
        existing.setTrainingFeeCents(updated.getTrainingFeeCents());

        Equipment saved = equipmentRepository.save(existing);
        syncStripeProduct(saved, oldName, oldDescription, oldStatus);
        return saved;
    }

    public Equipment updateStatus(Long id, EquipmentStatus status) {
        Equipment equipment = findById(id);
        EquipmentStatus oldStatus = equipment.getStatus();
        equipment.setStatus(status);
        Equipment saved = equipmentRepository.save(equipment);
        syncStripeProduct(saved, saved.getName(), saved.getDescription(), oldStatus);
        return saved;
    }

    public void delete(Long id) {
        Equipment equipment = findById(id);
        if (equipment.getStripeProductId() != null) {
            try {
                stripeClient.v1().products().update(equipment.getStripeProductId(),
                        ProductUpdateParams.builder().setActive(false).build());
            } catch (StripeException e) {
                log.warn("Could not archive Stripe product for equipment {}: {}", id, e.getMessage());
            }
        }
        equipmentRepository.delete(equipment);
    }

    // ── Stripe helpers ────────────────────────────────────────────────────────

    private void provisionStripeProduct(Equipment equipment) {
        try {
            ProductCreateParams params = ProductCreateParams.builder()
                    .setName(equipment.getName())
                    .setDescription(equipment.getDescription() != null ? equipment.getDescription() : "")
                    .putMetadata("equipment_id", String.valueOf(equipment.getId()))
                    .build();
            Product product = stripeClient.v1().products().create(params);
            equipment.setStripeProductId(product.getId());
            equipmentRepository.save(equipment);
        } catch (StripeException e) {
            log.warn("Could not create Stripe product for equipment {}: {}", equipment.getId(), e.getMessage());
        }
    }

    private void syncStripeProduct(Equipment equipment, String oldName,
                                    String oldDescription, EquipmentStatus oldStatus) {
        if (equipment.getStripeProductId() == null) return;
        try {
            boolean nameChanged = !equipment.getName().equals(oldName);
            boolean descChanged = !Objects.equals(equipment.getDescription(), oldDescription);
            boolean justRetired = equipment.getStatus() == EquipmentStatus.RETIRED
                               && oldStatus != EquipmentStatus.RETIRED;

            if (nameChanged || descChanged) {
                ProductUpdateParams.Builder b = ProductUpdateParams.builder();
                if (nameChanged) b.setName(equipment.getName());
                if (descChanged) b.setDescription(equipment.getDescription() != null ? equipment.getDescription() : "");
                stripeClient.v1().products().update(equipment.getStripeProductId(), b.build());
            }
            if (justRetired) {
                stripeClient.v1().products().update(equipment.getStripeProductId(),
                        ProductUpdateParams.builder().setActive(false).build());
            }
        } catch (StripeException e) {
            log.warn("Could not sync Stripe product for equipment {}: {}", equipment.getId(), e.getMessage());
        }
    }
}
