package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.CreateReservationRequest;
import com.makerspace.backend.controller.dto.ExtendReservationRequest;
import com.makerspace.backend.controller.dto.ReservationDTO;
import com.makerspace.backend.services.ReservationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    @Autowired private ReservationService reservationService;
    @Autowired private UserSecurity userSecurity;

    // -------------------------------------------------------------------------
    // User-facing
    // -------------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationDTO create(@Valid @RequestBody CreateReservationRequest req, Authentication auth) {
        Long userId = userSecurity.getUserId(auth);
        return ReservationDTO.from(
                reservationService.create(userId, req.equipmentId(), req.startTime(), req.endTime()));
    }

    @GetMapping("/me")
    public List<ReservationDTO> myReservations(Authentication auth) {
        return reservationService.findByUser(userSecurity.getUserId(auth))
                .stream().map(ReservationDTO::from).toList();
    }

    @GetMapping("/me/{id}")
    public ReservationDTO myReservation(@PathVariable Long id, Authentication auth) {
        return ReservationDTO.from(reservationService.findByIdForUser(id, userSecurity.getPrincipal(auth)));
    }

    @PatchMapping("/{id}/cancel")
    public ReservationDTO cancel(@PathVariable Long id, Authentication auth) {
        return ReservationDTO.from(reservationService.cancel(id, userSecurity.getPrincipal(auth)));
    }

    // -------------------------------------------------------------------------
    // Staff
    // -------------------------------------------------------------------------

    @PatchMapping("/{id}/extend")
    @PreAuthorize("hasAuthority('MANAGE_RESERVATIONS')")
    public ReservationDTO extend(@PathVariable Long id,
                                 @Valid @RequestBody ExtendReservationRequest req) {
        return ReservationDTO.from(reservationService.extend(id, req.newEndTime()));
    }

    // -------------------------------------------------------------------------
    // Admin
    // -------------------------------------------------------------------------

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('VIEW_ALL_RESERVATIONS')")
    public Page<ReservationDTO> allReservations(@PageableDefault(size = 50) Pageable pageable) {
        return reservationService.findAll(pageable).map(ReservationDTO::from);
    }

    @GetMapping("/admin/equipment/{equipmentId}")
    @PreAuthorize("hasAuthority('VIEW_ALL_RESERVATIONS')")
    public List<ReservationDTO> byEquipment(@PathVariable Long equipmentId) {
        return reservationService.findByEquipment(equipmentId)
                .stream().map(ReservationDTO::from).toList();
    }

    // -------------------------------------------------------------------------

}
