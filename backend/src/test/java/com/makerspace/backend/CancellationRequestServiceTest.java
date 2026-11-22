package com.makerspace.backend;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.CancellationRequest;
import com.makerspace.backend.model.CancellationRequestStatus;
import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;
import com.makerspace.backend.model.User;
import com.makerspace.backend.repository.CancellationRequestRepository;
import com.makerspace.backend.repository.PaymentRecordRepository;
import com.makerspace.backend.repository.ReservationRepository;
import com.makerspace.backend.services.CancellationRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancellationRequestServiceTest {
    @Mock ReservationRepository reservationRepository;
    @Mock CancellationRequestRepository cancellationRequestRepository;
    @Mock PaymentRecordRepository paymentRecordRepository;
    @InjectMocks CancellationRequestService service;

    private final UserPrincipal owner = new UserPrincipal(7L, null, null, null);
    private final UserPrincipal admin = new UserPrincipal(8L, null, null, null);
    private final EquipmentReservation reservation = new EquipmentReservation();

    @BeforeEach
    void setUp() {
        reservation.setId(42L);
        reservation.setUserId(owner.userId());
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setEndTime(ZonedDateTime.now().plusHours(1));
    }

    @Test
    void refusesBlankCancellationReasonsBeforeWriting() {
        when(reservationRepository.findById(42L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> service.submit(42L, "   ", owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("reason");
        verifyNoInteractions(cancellationRequestRepository);
    }

    @Test
    void rejectionRecordsTheNoteWithoutCancellingTheReservation() {
        CancellationRequest request = new CancellationRequest();
        request.setReservation(reservation);
        request.setRequestedBy(new User());
        request.setStatus(CancellationRequestStatus.PENDING);
        when(cancellationRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
        when(cancellationRequestRepository.save(request)).thenReturn(request);
        when(paymentRecordRepository.findByReservationId(42L)).thenReturn(Optional.empty());

        service.adminResolve(1L, "REJECT", "Booking remains available.", " \n ", admin);

        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.REJECTED);
        assertThat(request.getAdminNote()).isEqualTo("Booking remains available.");
        assertThat(request.getRejectionReason()).isNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void ownerCanWithdrawPendingRequestWithoutChangingReservation() {
        CancellationRequest request = requestWithStatus(CancellationRequestStatus.PENDING);

        service.withdraw(42L, 1L, owner);

        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.WITHDRAWN);
        assertThat(request.getResolvedAt()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(cancellationRequestRepository).save(request);
        verifyNoInteractions(reservationRepository, paymentRecordRepository);
        assertThatThrownBy(() -> service.adminResolve(1L, "APPROVE_NO_REFUND", "", null, admin))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    void ownerCannotWithdrawAnApprovedRequest() {
        CancellationRequest request = requestWithStatus(CancellationRequestStatus.APPROVED_NO_REFUND);

        assertThatThrownBy(() -> service.withdraw(42L, 1L, owner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.APPROVED_NO_REFUND);
    }

    @Test
    void anotherMemberCannotWithdrawTheRequest() {
        CancellationRequest request = requestWithStatus(CancellationRequestStatus.PENDING);

        assertThatThrownBy(() -> service.withdraw(42L, 1L, new UserPrincipal(9L, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThat(request.getStatus()).isEqualTo(CancellationRequestStatus.PENDING);
    }

    private CancellationRequest requestWithStatus(CancellationRequestStatus status) {
        CancellationRequest request = new CancellationRequest();
        request.setReservation(reservation);
        request.setStatus(status);
        when(cancellationRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
        return request;
    }
}
