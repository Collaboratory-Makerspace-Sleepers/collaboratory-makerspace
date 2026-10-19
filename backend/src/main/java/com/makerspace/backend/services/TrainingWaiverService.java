package com.makerspace.backend.services;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.TrainingWaiverSignature;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.TrainingWaiverSignatureRepository;
import com.makerspace.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Service
public class TrainingWaiverService {

    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TrainingWaiverSignatureRepository signatureRepository;

    /**
     * Records a waiver signature for a specific booking intent (equipment + time window).
     * The caller must supply the current waiver version for the equipment; if it does not
     * match, the signing is rejected so the frontend can show the updated waiver text.
     *
     * Returns the new signature row. ReservationService links reservation_id onto it when
     * the reservation is subsequently created.
     */
    @Transactional
    public TrainingWaiverSignature sign(Long userId, Long equipmentId, String signerName,
                                        ZonedDateTime startTime, ZonedDateTime endTime,
                                        int waiverVersion) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipment not found"));

        if (waiverVersion != equipment.getCurrentWaiverVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Waiver version mismatch — please reload the waiver and sign the current version ("
                            + equipment.getCurrentWaiverVersion() + ")");
        }

        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "End time must be after start time");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        TrainingWaiverSignature signature = new TrainingWaiverSignature();
        signature.setUser(user);
        signature.setEquipment(equipment);
        signature.setSignerName(signerName.trim());
        signature.setSignedAt(LocalDateTime.now());
        signature.setWaiverVersion(waiverVersion);
        signature.setStartTime(startTime);
        signature.setEndTime(endTime);

        return signatureRepository.save(signature);
    }
}
