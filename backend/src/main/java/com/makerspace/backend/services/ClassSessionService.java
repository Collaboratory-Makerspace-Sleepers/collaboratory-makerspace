package com.makerspace.backend.services;

import com.makerspace.backend.controller.dto.*;
import com.makerspace.backend.model.ClassEnrollment;
import com.makerspace.backend.model.ClassSession;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.ClassEnrollmentRepository;
import com.makerspace.backend.repository.ClassSessionRepository;
import com.makerspace.backend.repository.UserRepository;
import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
public class ClassSessionService {

    @Autowired private ClassSessionRepository classSessionRepository;
    @Autowired private ClassEnrollmentRepository classEnrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MembershipService membershipService;
    @Autowired private BillingService billingService;

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClassSessionDTO> listUpcoming(Long viewerUserId) {
        List<ClassSession> sessions = classSessionRepository.findUpcoming(ZonedDateTime.now());
        return sessions.stream()
                .map(s -> toDTO(s, viewerUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClassSessionDTO> listAll(Long viewerUserId) {
        List<ClassSession> sessions = classSessionRepository.findAllOrderByStartTimeDesc();
        return sessions.stream()
                .map(s -> toDTO(s, viewerUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassSessionDTO get(Long sessionId, Long viewerUserId) {
        ClassSession session = findSession(sessionId);
        return toDTO(session, viewerUserId);
    }

    @Transactional(readOnly = true)
    public List<ClassEnrollmentDTO> getMyEnrollments(Long userId) {
        return classEnrollmentRepository.findByUserId(userId).stream()
                .filter(e -> "ENROLLED".equals(e.getStatus()))
                .map(ClassEnrollmentDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRoster(Long sessionId) {
        return classEnrollmentRepository.findByClassSessionIdAndStatus(sessionId, "ENROLLED")
                .stream()
                .map(e -> {
                    User u = e.getUser();
                    String name = u.getProfile() != null
                            ? (u.getProfile().getFirstName() + " " + u.getProfile().getLastName()).trim()
                            : u.getEmail();
                    return Map.<String, Object>of(
                            "enrollmentId", e.getId(),
                            "userId", e.getUserId(),
                            "name", name,
                            "email", u.getEmail(),
                            "enrolledAt", e.getCreatedAt()
                    );
                })
                .toList();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public ClassSessionDTO create(CreateClassSessionRequest req, Long actingUserId) {
        ClassSession session = new ClassSession();
        applyCreate(session, req);
        classSessionRepository.save(session);
        return toDTO(session, actingUserId);
    }

    public ClassSessionDTO update(Long sessionId, UpdateClassSessionRequest req, Long actingUserId, boolean hasManageClasses) {
        ClassSession session = findSession(sessionId);

        // Instructors may only edit their own sessions
        if (!hasManageClasses) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
        if (session.getInstructorId() != null
                && !session.getInstructorId().equals(actingUserId)
                && !isAdminOrStaff(actingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Instructors may only edit their own sessions");
        }

        applyUpdate(session, req);
        classSessionRepository.save(session);
        return toDTO(session, actingUserId);
    }

    public void delete(Long sessionId) {
        ClassSession session = findSession(sessionId);
        session.setStatus("CANCELLED");
        classSessionRepository.save(session);
    }

    // ── Enrollment ────────────────────────────────────────────────────────────

    public Map<String, Object> enroll(Long sessionId, Long userId) throws StripeException {
        ClassSession session = findSession(sessionId);

        if (!"SCHEDULED".equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not available for enrollment");
        }

        // Capacity check
        if (session.getMaxCapacity() > 0) {
            long enrolled = classEnrollmentRepository.countByClassSessionIdAndStatus(sessionId, "ENROLLED");
            if (enrolled >= session.getMaxCapacity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is full");
            }
        }

        // One enrollment per user per session
        classEnrollmentRepository.findByClassSessionIdAndUserId(sessionId, userId)
                .ifPresent(e -> {
                    if (!"CANCELLED".equals(e.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Already enrolled in this session");
                    }
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        int price = resolvePrice(session, userId);

        // Reuse cancelled enrollment or create new
        ClassEnrollment enrollment = classEnrollmentRepository
                .findByClassSessionIdAndUserId(sessionId, userId)
                .orElseGet(() -> {
                    ClassEnrollment e = new ClassEnrollment();
                    e.setClassSession(session);
                    e.setUser(user);
                    return e;
                });

        enrollment.setQuotedPriceCents(price);
        enrollment.setStatus("PENDING");
        enrollment.setStripeCheckoutSessionId(null);
        classEnrollmentRepository.save(enrollment);

        if (price == 0) {
            enrollment.setStatus("ENROLLED");
            classEnrollmentRepository.save(enrollment);
            return Map.of("enrollmentId", enrollment.getId(), "free", true);
        }

        String sessionUrl = billingService.createClassCheckoutSession(
                userId, enrollment.getId(), price, session.getTitle());
        return Map.of("enrollmentId", enrollment.getId(), "sessionUrl", sessionUrl, "free", false);
    }

    public ClassEnrollmentDTO confirmEnrollment(Long enrollmentId, Long userId, String stripeSessionId) throws StripeException {
        ClassEnrollment enrollment = classEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));

        if (!enrollment.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your enrollment");
        }

        if ("ENROLLED".equals(enrollment.getStatus())) {
            return ClassEnrollmentDTO.from(enrollment);
        }

        if (!"PENDING".equals(enrollment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Enrollment is not in PENDING state: " + enrollment.getStatus());
        }

        billingService.verifyClassPayment(stripeSessionId);

        enrollment.setStatus("ENROLLED");
        enrollment.setStripeCheckoutSessionId(stripeSessionId);
        classEnrollmentRepository.save(enrollment);

        return ClassEnrollmentDTO.from(enrollment);
    }

    public void cancelEnrollment(Long sessionId, Long userId) {
        ClassEnrollment enrollment = classEnrollmentRepository
                .findByClassSessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));

        if ("CANCELLED".equals(enrollment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment already cancelled");
        }

        enrollment.setStatus("CANCELLED");
        classEnrollmentRepository.save(enrollment);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    int resolvePrice(ClassSession session, Long userId) {
        if (membershipService.hasMachineRateWaiver(userId)) {
            return session.getPriceCents() / 2;
        }
        if (membershipService.hasActiveMembership(userId)) {
            return session.getMemberPriceCents();
        }
        return session.getPriceCents();
    }

    private ClassSessionDTO toDTO(ClassSession session, Long viewerUserId) {
        long enrolled = classEnrollmentRepository.countByClassSessionIdAndStatus(session.getId(), "ENROLLED");
        int resolvedPrice = viewerUserId != null ? resolvePrice(session, viewerUserId) : session.getPriceCents();
        return ClassSessionDTO.from(session, enrolled, resolvedPrice);
    }

    private ClassSession findSession(Long sessionId) {
        return classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class session not found"));
    }

    private void applyCreate(ClassSession session, CreateClassSessionRequest req) {
        session.setTitle(req.title());
        session.setDescription(req.description());
        session.setStartTime(req.startTime());
        session.setEndTime(req.endTime());
        session.setMaxCapacity(req.maxCapacity());
        session.setPriceCents(req.priceCents());
        session.setMemberPriceCents(req.memberPriceCents());
        session.setImageUrl(req.imageUrl());
        session.setLocation(req.location());
        if (req.instructorId() != null) {
            User instructor = userRepository.findById(req.instructorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found"));
            session.setInstructor(instructor);
        }
    }

    private void applyUpdate(ClassSession session, UpdateClassSessionRequest req) {
        if (req.title() != null)       session.setTitle(req.title());
        if (req.description() != null) session.setDescription(req.description());
        if (req.startTime() != null)   session.setStartTime(req.startTime());
        if (req.endTime() != null)     session.setEndTime(req.endTime());
        if (req.maxCapacity() > 0)     session.setMaxCapacity(req.maxCapacity());
        if (req.priceCents() >= 0)     session.setPriceCents(req.priceCents());
        if (req.memberPriceCents() >= 0) session.setMemberPriceCents(req.memberPriceCents());
        if (req.imageUrl() != null)    session.setImageUrl(req.imageUrl());
        if (req.location() != null)    session.setLocation(req.location());
        if (req.status() != null)      session.setStatus(req.status());
        if (req.instructorId() != null) {
            User instructor = userRepository.findById(req.instructorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instructor not found"));
            session.setInstructor(instructor);
        }
    }

    private boolean isAdminOrStaff(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRoles().stream()
                        .anyMatch(r -> "ADMIN".equals(r.getCode()) || "STAFF".equals(r.getCode())))
                .orElse(false);
    }
}
