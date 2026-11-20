package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.Permission;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberCancellationHistoryTest {
    private static final String HISTORY = "/api/v1/reservations/me/cancellation-requests";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired EquipmentRepository equipment;
    @Autowired ReservationRepository reservations;
    @Autowired EntityManager entityManager;

    @Test
    void ownerSeesPendingWithdrawnAndApprovedRequestsWithoutExposingStaffData() throws Exception {
        User owner = saveUser("history-owner@example.test");
        User other = saveUser("history-other@example.test");
        Equipment tool = new Equipment();
        tool.setName("Rachel");
        EquipmentReservation reservation = new EquipmentReservation();
        reservation.setUser(owner);
        reservation.setEquipment(equipment.save(tool));
        reservation.setStartTime(ZonedDateTime.now().plusDays(1));
        reservation.setEndTime(reservation.getStartTime().plusHours(1));
        Long reservationId = reservations.saveAndFlush(reservation).getId();
        entityManager.clear();
        String submitPath = "/api/v1/reservations/" + reservationId + "/cancellation-request";
        RequestPostProcessor member = asUser(owner, List.of());

        long first = submit(submitPath, member);
        mvc.perform(get(HISTORY).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentName").value("Rachel"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].reservationStatus").value("ACTIVE"));
        mvc.perform(get(HISTORY).with(asUser(other, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mvc.perform(post(submitPath + "/" + first + "/withdraw").with(member))
                .andExpect(status().isNoContent());
        mvc.perform(get(HISTORY).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$[0].reservationStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].resolvedAt").isNotEmpty());

        RequestPostProcessor reviewer = asUser(other,
                List.of(new SimpleGrantedAuthority(Permission.MANAGE_RESERVATIONS)));
        long declined = submit(submitPath, member);
        mvc.perform(post("/api/v1/admin/cancellation-requests/" + declined + "/resolve")
                        .with(reviewer)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECT","rejectionReason":"  Please contact the front desk.  ",
                                 "adminNote":"Private staff note"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get(HISTORY).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].reservationStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].rejectionReason").value("Please contact the front desk."))
                .andExpect(jsonPath("$[0].adminNote").doesNotHaveJsonPath());
        mvc.perform(get(HISTORY).with(asUser(other, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        long second = submit(submitPath, member);
        mvc.perform(post("/api/v1/admin/cancellation-requests/" + second + "/resolve")
                        .with(reviewer)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE_NO_REFUND","adminNote":"Internal note"}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get(HISTORY).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED_NO_REFUND"))
                .andExpect(jsonPath("$[0].reservationStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].adminNote").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[0].stripePaymentIntentId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[1].status").value("REJECTED"))
                .andExpect(jsonPath("$[2].status").value("WITHDRAWN"));

        String activity = "/api/v1/users/activity/feed";
        RequestPostProcessor activityAdmin = asUser(other,
                List.of(new SimpleGrantedAuthority(Permission.MANAGE_USERS)));
        mvc.perform(get(activity).with(activityAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'CANCELLATION_REQUESTED')].detail",
                        containsInAnyOrder("Rachel · Request #" + first, "Rachel · Request #" + declined,
                                "Rachel · Request #" + second)))
                .andExpect(jsonPath("$[?(@.type == 'CANCELLATION_WITHDRAWN')].userId",
                        containsInAnyOrder(owner.getId().intValue())))
                .andExpect(jsonPath("$[?(@.type == 'CANCELLATION_APPROVED')].status",
                        containsInAnyOrder("APPROVED_NO_REFUND")));
        mvc.perform(get(activity).with(member)).andExpect(status().isForbidden());
    }

    private long submit(String path, RequestPostProcessor member) throws Exception {
        String body = mvc.perform(post(path).with(member)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"Change of plans\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setAuth0Subject("otp:" + email);
        return users.save(user);
    }

    private RequestPostProcessor asUser(User user, List<GrantedAuthority> authorities) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getAuth0Subject(), user.getEmail(), authorities);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
