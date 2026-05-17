package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.repository.EquipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EquipmentService {

    @Autowired
    private EquipmentRepository equipmentRepository;

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
        return equipmentRepository.save(equipment);
    }

    public Equipment update(Long id, Equipment updated) {
        Equipment existing = findById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setImageUrl(updated.getImageUrl());
        existing.setStatus(updated.getStatus());
        return equipmentRepository.save(existing);
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
