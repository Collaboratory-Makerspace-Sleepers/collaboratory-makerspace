# Feature Suggestions

Ideas for post-launch enhancements. None of these are required for go-live.

---

## 1. Paid Reservation Extension

Allow a user to extend an active reservation beyond its original end time, paying the incremental
cost at their applicable hourly rate, provided no other booking occupies the extended window.

### User flow

1. User is on the MyReservations page with an active reservation.
2. User clicks "Add time" and selects a new end time.
3. Frontend computes the extension cost:
   `extensionCostCents = reservation.quotedRateCents × (newDurationMinutes - originalDurationMinutes) / 60`
4. If the user does not have a zero-rate waiver (studio member), they are shown the cost and
   confirm payment before the extension is applied.
5. On confirmation, frontend calls `PATCH /api/v1/reservations/{id}/extend` with `newEndTime`.

### Backend changes needed

The current `extend` endpoint (`ReservationService.extend()`) changes the end time with no
payment step. To support paid extensions it would need to:

- Calculate the delta cost (`newDuration - currentDuration`) × `quotedRateCents`.
- If delta cost > 0: initiate a Stripe payment intent for the delta amount and only apply the
  extension once payment succeeds (webhook confirms `payment_intent.succeeded`).
- If delta cost = 0 (studio member, or free equipment): apply immediately as today.
- Update `estimatedCostCents` on the reservation to reflect the new total.

### Conflict check

Already implemented — `ReservationService.extend()` calls `findOverlapping()` against `ACTIVE`
reservations and rejects the extension if another booking starts before the new end time.

### Notes

- The extension should re-use the existing waiver for the reservation (no re-signing needed since
  the same booking session is being extended, not a new reservation).
- Staff extending a reservation on behalf of a user (`MANAGE_RESERVATIONS` permission) should be
  able to waive the payment step with an audit note.
