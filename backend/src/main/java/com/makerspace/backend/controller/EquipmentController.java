package com.makerspace.backend.controller;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import com.makerspace.backend.services.EquipmentService;
import com.makerspace.backend.config.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    @Autowired
    private EquipmentService equipmentService;

    /*
     * Anyone authenticated can view the equipment.
     */
    @GetMapping
    public List<Equipment> getAll(@AuthenticationPrincipal UserPrincipal principal) {
        return equipmentService.forViewer(equipmentService.findAll(), principal == null ? null : principal.userId());
    }

    @GetMapping("/{id}")
    public Equipment getById(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return equipmentService.forViewer(List.of(equipmentService.findById(id)),
                principal == null ? null : principal.userId()).get(0);
    }

    @GetMapping("/status/{status}")
    public List<Equipment> getByStatus(@PathVariable EquipmentStatus status, @AuthenticationPrincipal UserPrincipal principal) {
        return equipmentService.forViewer(equipmentService.findByStatus(status), principal == null ? null : principal.userId());
    }

    @GetMapping("/category/{category}")
    public List<Equipment> getByCategory(@PathVariable String category, @AuthenticationPrincipal UserPrincipal principal) {
        return equipmentService.forViewer(equipmentService.findByCategory(category), principal == null ? null : principal.userId());
    }

    @GetMapping("/search")
    public List<Equipment> search(@RequestParam String q, @AuthenticationPrincipal UserPrincipal principal) {
        return equipmentService.forViewer(equipmentService.search(q), principal == null ? null : principal.userId());
    }

    /*
    * Admins and staff can create and update
    */
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Equipment create(@RequestBody Equipment equipment) {
        return equipmentService.create(equipment);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Equipment update(@PathVariable Long id, @RequestBody Equipment equipment) {
        return equipmentService.update(id, equipment);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Equipment updateStatus(@PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        EquipmentStatus status = EquipmentStatus.valueOf(body.get("status"));
        return equipmentService.updateStatus(id, status);
    }

    /*
    * Only Admin can delete
    */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleEquipmentError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "Equipment request failed" : error.getReason()));
    }
}