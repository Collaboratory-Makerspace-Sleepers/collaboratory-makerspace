package com.makerspace.backend.services;

import com.makerspace.backend.model.TrainingWaiverSignature;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.TrainingWaiverSignatureRepository;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class TrainingWaiverService {

    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TrainingWaiverSignatureRepository signatureRepository;

    @Transactional
    public void sign(Long userId, Long equipmentId, String signerName) {
        var equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));
        if (!equipment.isTrainingRequired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This equipment does not require training");
        }
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        TrainingWaiverSignature signature = signatureRepository
                .findByUserIdAndEquipmentId(userId, equipmentId)
                .orElseGet(TrainingWaiverSignature::new);
        signature.setUser(user);
        signature.setEquipment(equipment);
        signature.setSignerName(signerName.trim());
        signature.setSignedAt(LocalDateTime.now());
        signatureRepository.save(signature);
    }
}