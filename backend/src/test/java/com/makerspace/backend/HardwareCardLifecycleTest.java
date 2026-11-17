package com.makerspace.backend;
import com.makerspace.backend.model.*;
import com.makerspace.backend.repository.*;
import com.makerspace.backend.services.*;
import com.makerspace.backend.services.HardwareDeviceService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties={"spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","app.jwt.secret=review-only-secret"})
@ContextConfiguration(classes=HardwareDevicePersistenceTest.Config.class)
@Import({CardAccountCleanup.class, HardwareDoorOverrideService.class})
class HardwareCardLifecycleTest {
 @Autowired HardwareDeviceService service;
 @Autowired CardService cardService;
 @Autowired CardAccountCleanup cleanup;
 @Autowired HardwareDoorOverrideService overrides;
 @Autowired HardwareDoorOverrideRepository grants;
 @Autowired UserRepository users;
 @Autowired MemberCardRepository cards;
 @Autowired CardScanRepository scans;
 @Autowired EquipmentRepository equipment;
 @Autowired TestEntityManager em;
 @MockBean HardwareCheckInService checkIns;
 User member(String email){ User u=new User();u.setEmail(email);u.setAccountStatus(AccountStatus.ACTIVE);return users.saveAndFlush(u); }
 Pairing pi(String name,Long equipmentId){return service.create(new DeviceSettings(name,"Entrance",equipmentId,true,true,5));}
 com.makerspace.backend.config.security.DevicePrincipal pair(Pairing d){var p=service.pair(d.code(),"pi");return service.authenticate(d.device().id().toString(),(String)p.get("deviceKey")).orElseThrow();}
 @Test void deletionReleasesCardPreservesHistoryAndAllowsReassignment(){
  User u=member("deleted@example.test"); Long id=u.getId();cardService.assign("AABBCCDD",id,null,id);
  cardService.scan("AABBCCDD","old",null);em.flush();cleanup.release(id);users.delete(u);em.flush();em.clear();
  assertThat(cards.findByCardUid("AABBCCDD")).isEmpty();
  assertThat(cardService.listCards()).isEmpty();assertThat(cardService.recentScans(10)).hasSize(1);
  assertThat(scans.findAll().get(0).getOriginalUserId()).isEqualTo(id);
  assertThat(scans.findAll().get(0).getUser()).isNull();
  assertThat(cardService.scan("AABBCCDD","old",null).message()).isEqualTo("Card not registered");
  User another=member("new@example.test");assertThat(cardService.assign("AABBCCDD",another.getId(),null,another.getId()).getUser().getId()).isEqualTo(another.getId());
 }
 @Test void enrollmentAndDirectAssignmentCannotEnableDisabledCard(){
  User u=member("disabled@example.test");MemberCard c=cardService.assign("00112233",u.getId(),null,u.getId());c.setActive(false);cards.saveAndFlush(c);
  Pairing d=pi("Pi",null);var principal=pair(d);
  service.startAssignment(d.device().id(),u.getId(),null,u.getId());
  assertThat(service.scan(principal,"00112233")).containsEntry("status","ASSIGNMENT_REJECTED");
  assertThatThrownBy(()->cardService.assign("00112233",u.getId(),null,u.getId())).hasMessageContaining("disabled");
  em.flush();em.clear();assertThat(cards.findByCardUid("00112233").orElseThrow().isActive()).isFalse();
 }
 @Test void overrideIsScopedExpiringRevocableAndNeverBypassesDisabledAccountOrCard(){
  User u=member("override@example.test");MemberCard c=cardService.assign("A1B2",u.getId(),null,u.getId());
  Pairing first=pi("First",null),second=pi("Second",null);var one=pair(first);var two=pair(second);
  var grant=overrides.grant(u.getId(),first.device().id(),"Pending waiver",60,u.getId());
  assertThat(service.scan(one,"A1B2")).containsEntry("approved",true).containsEntry("message","Access granted by admin door override");
  assertThat(scans.findAll()).anyMatch(s->grant.getId().equals(s.getDoorOverrideId()));
  assertThat(service.scan(two,"A1B2")).containsEntry("approved",false);
  assertThat(cardService.scan("A1B2",first.device().id().toString(),null).approved()).isFalse();
  c.setActive(false);assertThat(service.scan(one,"A1B2")).containsEntry("approved",false);c.setActive(true);
  u.setAccountStatus(AccountStatus.PRE_REGISTERED);assertThat(service.scan(one,"A1B2")).containsEntry("approved",false);u.setAccountStatus(AccountStatus.ACTIVE);
  grant.setExpiresAt(Instant.now().minusSeconds(1));assertThat(service.scan(one,"A1B2")).containsEntry("approved",false);
  grant.setExpiresAt(Instant.now().plusSeconds(100));overrides.revoke(u.getId(),grant.getId(),u.getId());
  assertThat(service.scan(one,"A1B2")).containsEntry("approved",false);assertThat(grants.findById(grant.getId())).isPresent();
 }
 @Test void equipmentBindingCannotUseDoorOverride(){
  User u=member("equipment@example.test");cardService.assign("ABCD",u.getId(),null,u.getId());
  Equipment e=new Equipment();e.setName("Laser");e.setRequiresTraining(true);e=equipment.saveAndFlush(e);
  Pairing d=pi("Door",null);var p=pair(d);overrides.grant(u.getId(),d.device().id(),"Temporary",60,u.getId());
  service.update(d.device().id(),new DeviceSettings("Laser","Workshop",e.getId(),true,true,5));
  assertThat(service.scan(p,"ABCD")).containsEntry("approved",false);
  assertThatThrownBy(()->overrides.grant(u.getId(),d.device().id(),"Not allowed",60,u.getId())).hasMessageContaining("general-entry");
 }
 @Test void invalidUidIsRejectedInsteadOfSilentlyChanged(){
  assertThat(CardService.normalizeUid("95:E2-63 D4")).isEqualTo("95E263D4");
  assertThat(CardService.normalizeUid("garbage95E263D4")).isEmpty();
  assertThat(CardService.normalizeUid("12345")).isEmpty();
 }

 @Test void newlyRegisteredMemberCanAssignCardButCannotYetEnter(){
  User u=member("new-registration@example.test");u.setAccountStatus(AccountStatus.PRE_REGISTERED);users.saveAndFlush(u);
  Pairing d=pi("Entry",null);var p=pair(d);
  service.startAssignment(d.device().id(),u.getId(),null,u.getId());
  assertThat(service.scan(p,"1234")).containsEntry("status","CARD_ASSIGNED").containsEntry("approved",false);
  assertThat(service.scan(p,"1234")).containsEntry("approved",false).containsEntry("message","Account is not active");
 }
}
