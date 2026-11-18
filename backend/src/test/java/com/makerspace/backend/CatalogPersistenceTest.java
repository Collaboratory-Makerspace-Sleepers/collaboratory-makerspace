package com.makerspace.backend;

import com.makerspace.backend.controller.dto.CreateClassSessionRequest;
import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.repository.EquipmentRepository;
import com.makerspace.backend.services.BillingService;
import com.makerspace.backend.services.ClassSessionService;
import com.makerspace.backend.services.EquipmentService;
import com.makerspace.backend.services.MembershipService;
import com.stripe.StripeClient;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ContextConfiguration(classes = CatalogPersistenceTest.Config.class)
class CatalogPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Equipment.class)
    @EnableJpaRepositories(basePackageClasses = EquipmentRepository.class)
    @Import({EquipmentService.class, ClassSessionService.class})
    static class Config {}

    @Autowired private EquipmentService equipmentService;
    @Autowired private ClassSessionService classService;
    @Autowired private TestEntityManager entityManager;
    @MockBean private MembershipService membership;
    @MockBean private BillingService billing;
    @MockBean(answer = Answers.RETURNS_DEEP_STUBS) private StripeClient stripe;

    @Test
    void createdAndEditedEquipmentIsPersistedAndVisibleWithTheMemberPrice() {
        Equipment item = new Equipment();
        item.setName("New laser");
        item.setCategory("LASER");
        item.setRequiresTraining(true);
        item.setTrainingVideoUrl("https://example.test/laser.mp4");
        item.setHourlyRateCents(725);
        Long id = equipmentService.create(item).getId();
        entityManager.flush();
        entityManager.clear();

        Equipment saved = equipmentService.findById(id);
        assertThat(saved.getTrainingVideoUrl()).isEqualTo(item.getTrainingVideoUrl());
        assertThat(saved.isRequiresTraining()).isTrue();
        assertThat(saved.getMemberRateCents()).isEqualTo(725);
        saved.setHourlyRateCents(1225);
        equipmentService.update(id, saved);
        entityManager.flush();
        entityManager.clear();

        when(membership.hasActiveMembership(1L)).thenReturn(true);
        var catalog = equipmentService.forViewer(equipmentService.findAll(), 1L);
        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).getResolvedHourlyRateCents()).isEqualTo(1225);
        assertThat(catalog.get(0).getHourlyRateCents()).isEqualTo(1225);
        assertThat(catalog.get(0).getMemberRateCents()).isEqualTo(1225);
    }

    @Test
    void createdClassIsPersistedAndVisibleInBothAdminAndMemberCatalogs() throws Exception {
        Product product = new Product();
        product.setId("prod_catalog_test");
        Price price = new Price();
        price.setId("price_catalog_test");
        when(stripe.v1().products().create(any(ProductCreateParams.class))).thenReturn(product);
        when(stripe.v1().prices().create(any(PriceCreateParams.class))).thenReturn(price);
        var start = ZonedDateTime.now().plusDays(7);
        var request = new CreateClassSessionRequest("New class", null, null,
                start, start.plusHours(1), 10, 2525, 1263, null, "Workshop");
        var created = classService.create(request, null);
        entityManager.flush();
        entityManager.clear();

        when(membership.hasActiveMembership(1L)).thenReturn(true);
        var memberCatalog = classService.listUpcoming(1L);
        assertThat(memberCatalog).hasSize(1);
        assertThat(memberCatalog.get(0).id()).isEqualTo(created.id());
        assertThat(memberCatalog.get(0).title()).isEqualTo("New class");
        assertThat(memberCatalog.get(0).resolvedPriceCents()).isEqualTo(1263);
        assertThat(classService.listAll(null)).extracting(session -> session.id()).containsExactly(created.id());
    }
}
