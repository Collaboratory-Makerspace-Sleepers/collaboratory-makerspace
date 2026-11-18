package com.makerspace.backend.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardAccountCleanup {
    @PersistenceContext private EntityManager em;
    @Transactional
    public void release(Long id) {
        em.flush();
        em.createNativeQuery("DELETE FROM member_cards WHERE user_id = :id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("UPDATE member_cards SET assigned_by_user_id = NULL WHERE assigned_by_user_id = :id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("UPDATE card_scans SET user_id = NULL WHERE user_id = :id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("UPDATE hardware_devices SET assignment_user_id = NULL, assignment_staff_id = NULL, assignment_label = NULL, assignment_expires_at = NULL WHERE assignment_user_id = :id OR assignment_staff_id = :id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("UPDATE hardware_door_overrides SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = :id AND revoked_at IS NULL").setParameter("id", id).executeUpdate();
    }
}
