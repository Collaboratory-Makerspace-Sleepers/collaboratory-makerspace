package com.makerspace.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.ClassSession;
import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipPlan;
import com.makerspace.backend.model.MembershipStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.model.UserProfile;
import com.makerspace.backend.repository.ClassSessionRepository;
import com.makerspace.backend.repository.MembershipPlanRepository;
import com.makerspace.backend.repository.MembershipRepository;
import com.makerspace.backend.repository.UserRepository;
import com.makerspace.backend.services.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClassBookingFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired MembershipPlanRepository membershipPlanRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired ClassSessionRepository classSessionRepository;

    @MockBean BillingService billingService;

    private User member;

    @BeforeEach
    void setUp() {
        member = saveUser("member-" + UUID.randomUUID() + "@example.com");
        MembershipPlan plan = new MembershipPlan();
        plan.setCode("MONTHLY-" + UUID.randomUUID());
        plan.setDisplayName("Monthly");
        plan.setStripePriceId("price_" + UUID.randomUUID());
        plan.setStripeProductId("prod_" + UUID.randomUUID());
        plan.setBillingInterval("MONTH");
        plan.setAmountCents(9500);
        plan.setCurrency("usd");
        MembershipPlan savedPlan = membershipPlanRepository.save(plan);

        Membership membership = new Membership();
        membership.setUser(member);
        membership.setPlan(savedPlan);
        membership.setStripeSubscriptionId("sub_" + UUID.randomUUID());
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setCurrentPeriodStart(ZonedDateTime.now().minusDays(1));
        membership.setCurrentPeriodEnd(ZonedDateTime.now().plusDays(30));
        membership.setStripeUpdatedAt(ZonedDateTime.now());
        membershipRepository.save(membership);
    }

    @Test
    void paidMemberCanReserveFreeClassAndSeeItInBookClasses() throws Exception {
        ClassSession session = saveClass("Free Intro Class", 0);

        mockMvc.perform(post("/api/v1/class-sessions/" + session.getId() + "/enroll")
                        .with(authentication(memberAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free").value(true));

        mockMvc.perform(get("/api/v1/class-sessions/me")
                        .with(authentication(memberAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classSessionId").value(session.getId()))
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));

        mockMvc.perform(get("/api/v1/class-sessions")
                        .with(authentication(memberAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + session.getId() + ")].title").value("Free Intro Class"));
    }

    @Test
    void paidMemberCanCompleteClassCheckoutAndSeeReservation() throws Exception {
        ClassSession session = saveClass("Paid Clay Class", 2500);
        when(billingService.createClassCheckoutSession(anyLong(), anyLong(), anyInt(), anyString()))
                .thenReturn("https://checkout.stripe.com/cs_test_123");

        String enrollBody = mockMvc.perform(post("/api/v1/class-sessions/" + session.getId() + "/enroll")
                        .with(authentication(memberAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free").value(false))
                .andExpect(jsonPath("$.sessionUrl").value("https://checkout.stripe.com/cs_test_123"))
                .andReturn().getResponse().getContentAsString();
        long enrollmentId = objectMapper.readTree(enrollBody).get("enrollmentId").asLong();

        mockMvc.perform(post("/api/v1/class-sessions/enrollment/" + enrollmentId + "/confirm")
                        .with(authentication(memberAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stripeSessionId", "cs_test_123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        var enrollments = classSessionRepository.findAll();
        assertThat(enrollments).extracting(ClassSession::getTitle).contains("Paid Clay Class");

        mockMvc.perform(get("/api/v1/class-sessions/me")
                        .with(authentication(memberAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].classSessionId").value(session.getId()))
                .andExpect(jsonPath("$[0].quotedPriceCents").value(1250))
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));
    }

    private User saveUser(String email) {
        UserProfile profile = new UserProfile();
        profile.setFirstName("Test");
        profile.setLastName("Member");
        User user = new User();
        user.setEmail(email);
        user.setAuth0Subject("otp:" + email);
        user.setProfile(profile);
        return userRepository.save(user);
    }

    private ClassSession saveClass(String title, int priceCents) {
        ClassSession session = new ClassSession();
        session.setTitle(title);
        session.setDescription("Booking flow test");
        session.setStartTime(ZonedDateTime.now().plusDays(2));
        session.setEndTime(ZonedDateTime.now().plusDays(2).plusHours(2));
        session.setMaxCapacity(10);
        session.setPriceCents(priceCents);
        session.setMemberPriceCents(priceCents / 2);
        session.setStatus("SCHEDULED");
        session.setLocation("Studio A");
        return classSessionRepository.save(session);
    }

    private Authentication memberAuth() {
        List<GrantedAuthority> authorities = List.of();
        var principal = new UserPrincipal(member.getId(), member.getAuth0Subject(), member.getEmail(), authorities);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
