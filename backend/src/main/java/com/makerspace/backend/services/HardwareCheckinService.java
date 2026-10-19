package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.CheckinResponse;
import com.makerspace.backend.model.AccessEvent;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.AccessEventRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Service
public class HardwareCheckinService {

    /** How many minutes before a reservation start time entry is permitted. */
    private static final int EARLY_ENTRY_MINUTES = 30;

    @Autowired private UserRepository        userRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private MembershipService     membershipService;
    @Autowired private AccessEventRepository accessEventRepository;

    /**
     * Called by the NFC reader when a card is tapped.
     *
     * Grants building entry when ANY of the following are true:
     *   1. The user has a studio assignment (machine rate waiver → permanent member).
     *   2. The user has an active/trialing/past-due/grace membership.
     *   3. The user has an ACTIVE reservation starting within the next
     *      {@value EARLY_ENTRY_MINUTES} minutes, or currently in progress.
     *
     * Every attempt — granted or denied — is written to {@code access_events}.
     */
    @Transactional
    public CheckinResponse checkin(String cardId) {
        // 1. Resolve card to a user account.
        User user = userRepository.findByNfcCardId(cardId).orElse(null);
        if (user == null) {
            log.info("Checkin denied — unknown card: {}", cardId);
            return record(null, cardId, CheckinResponse.denied("Card not registered to any account"));
        }

        Long userId = user.getId();
        String name = resolveName(user);

        // 2. Studio members always have building access.
        if (membershipService.hasMachineRateWaiver(userId)) {
            log.info("Checkin granted — studio member: userId={}", userId);
            return record(userId, cardId, CheckinResponse.granted(name));
        }

        // 3. Any active-ish membership grants general building access.
        MembershipStatus status = membershipService.statusOf(userId);
        if (MembershipStatus.INDEX_STATUSES.contains(status)) {
            log.info("Checkin granted — membership {}: userId={}", status, userId);
            return record(userId, cardId, CheckinResponse.granted(name));
        }

        // 4. Check for an ACTIVE reservation in the entry window (covers day-pass holders).
        ZonedDateTime now       = ZonedDateTime.now();
        ZonedDateTime windowEnd = now.plusMinutes(EARLY_ENTRY_MINUTES);
        boolean hasReservation  = reservationRepository.hasActiveReservationInWindow(userId, now, windowEnd);
        if (hasReservation) {
            log.info("Checkin granted — active reservation in window: userId={}", userId);
            return record(userId, cardId, CheckinResponse.granted(name));
        }

        log.info("Checkin denied — no access right: userId={}", userId);
        return record(userId, cardId,
                CheckinResponse.denied("No active membership or reservation found"));
    }

    // -------------------------------------------------------------------------

    private CheckinResponse record(Long userId, String cardId, CheckinResponse response) {
        AccessEvent event = new AccessEvent();
        event.setUserId(userId);
        event.setCardId(cardId);
        event.setGranted(response.granted());
        event.setDenialReason(response.granted() ? null : response.reason());
        accessEventRepository.save(event);
        return response;
    }

    private String resolveName(User user) {
        UserProfile profile = user.getProfile();
        if (profile == null) return user.getEmail();
        if (profile.getFirstName() != null && profile.getLastName() != null) {
            return profile.getFirstName() + " " + profile.getLastName();
        }
        if (profile.getFirstName() != null) return profile.getFirstName();
        return user.getEmail();
    }
}
