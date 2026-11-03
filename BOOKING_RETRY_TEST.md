# Testing the Booking Retry Fix

## Scenario: Equipment Requiring Waiver + Membership Purchase

### Setup
1. Ensure both backend and frontend are running:
   - Backend: `http://localhost:8080`
   - Frontend: `http://localhost:5173`

2. Have a test user ready (new user or one without active membership)

3. Identify an equipment item that:
   - Requires a waiver (e.g., "Laser Cutter")
   - Optionally requires training
   - Should NOT be in maintenance/retired status

### Test Steps

#### Phase 1: Select Equipment & Book
1. **Navigate** to `/dashboard/rentequipment`
2. **Click** on equipment that requires a waiver (e.g., "Laser Cutter")
3. **Select a date** in the calendar (next 7 days)
4. **Select a time slot** (9:00 AM - 4:00 PM)
5. **Click "Reserve"**

**Expected**: Should route to waiver page

#### Phase 2: Sign Waiver
1. **On the waiver page**, click **"Sign Waiver"**
2. **In the RabbitSign modal**, fill out and sign the waiver
3. **Wait for "Approved"** status (watch the page, it polls every 4s)
4. **Once approved**, click **"Continue to Checkout"**

**Expected**: Should navigate to checkout with waiver signed

#### Phase 3: Purchase Membership
1. **On the checkout page**, you'll see the booking summary
2. **If you don't have an active membership**, you'll see:
   > "You don't have an active membership. Members reserve equipment for free."
3. **Click "Get a membership"**

**Expected**: Should navigate to membership page

#### Phase 4: Complete Payment
1. **On the membership page**, select a plan (Monthly, Annual, etc.)
2. **Click "Continue to Payment"** or similar button
3. **In Stripe modal**, use test card:
   - Card: `4242 4242 4242 4242`
   - Exp: `12/25`
   - CVC: `123`
4. **Click "Pay"**

**Expected**: Should show "Payment received" then auto-redirect

#### Phase 5: Auto-Booking (The Fix!)
1. **Automatically redirected to** `/dashboard/reservations`
2. **You should see** a message: "Finalizing your booking…" with a spinner
3. **Wait 5-30 seconds** while the backend processes the webhook
4. **After ~10-20 seconds**, you should see:
   - The spinner disappears
   - Your reservation appears in the "No upcoming reservations" area
   - Equipment name, date, and time are shown

**Expected**: Reservation successfully created without manual intervention ✅

### What's Being Tested
- The frontend is now retrying reservation creation for **30 seconds** (was 8)
- The backend webhook for membership creation has time to complete
- The auto-booking mechanism properly handles the 402 errors and succeeds

### Troubleshooting

#### Still Getting "We couldn't finish booking automatically" Error
1. **Check browser console** (F12) for errors
2. **Verify backend is running**: `curl http://localhost:8080/actuator/health`
3. **Check backend logs** for Stripe webhook processing errors
4. **Clear sessionStorage**: 
   ```javascript
   sessionStorage.removeItem("pendingReservationAfterPayment")
   ```
5. **Try again** from the equipment selection step

#### Spinner Showing for Too Long (>30 seconds)
1. This might indicate the webhook isn't processing
2. **Check backend logs** for webhook errors:
   ```
   grep -i "stripe" /path/to/backend.log
   ```
3. **Verify Stripe webhooks** are being received (check Stripe dashboard)

#### Reservation Never Appears After Spinner
1. **Manually refresh** the page to reload reservations from server
2. **Check database** for the reservation record:
   ```sql
   SELECT * FROM equipment_reservations WHERE user_id = <YOUR_ID> 
   ORDER BY created_at DESC LIMIT 5;
   ```

### Performance Benchmarks
With the 30-second retry window:
- **Fast case** (webhook in <2s): Reservation appears in 2-3 seconds ✅
- **Typical case** (webhook in 5-10s): Reservation appears in 6-11 seconds ✅
- **Slow case** (webhook in 15-20s): Reservation appears in 16-21 seconds ✅
- **Very slow case** (webhook >30s): Shows error after 30 seconds ⚠️

### Browser DevTools Tips
1. **Open Console** (F12 → Console tab)
2. **Watch for logs**:
   ```javascript
   console.log("MyReservations init - pendingBooking:", pendingBooking);
   ```
3. **Check Network tab** for:
   - POST `/api/v1/reservations` with 402 responses (retrying)
   - Final POST `/api/v1/reservations` with 201 response (success)
   - GET `/api/v1/reservations/me` (loading updated list)

### Success Criteria ✅
- [ ] No error message after payment
- [ ] "Finalizing your booking…" spinner shows for 5-30 seconds
- [ ] Reservation automatically appears in the list
- [ ] User doesn't need to manually re-book
- [ ] Equipment name, date, and time are correct

