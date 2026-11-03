# Quick Testing Checklist for Booking Fix

## Pre-Test Setup
- [ ] Backend running on `localhost:8080` (PID: 31605)
- [ ] Frontend running on `localhost:5173` (Vite dev mode)
- [ ] PostgreSQL database accessible
- [ ] Redis running (if required)
- [ ] Logged in as test user

## Test Case 1: Normal Booking (No Membership Required)
- [ ] Select equipment that doesn't require membership
- [ ] Verify no "Get membership" button appears
- [ ] Booking should complete immediately
- [ ] Reservation appears in My Reservations

## Test Case 2: Booking with Membership (Happy Path)
**Equipment to use**: Laser Cutter (requires waiver + training)

Steps:
1. [ ] Select Laser Cutter from equipment list
2. [ ] System shows "This equipment requires training"
3. [ ] Sign the required waiver
4. [ ] Click proceed to checkout
5. [ ] Click "Get a membership"
6. [ ] **Stripe Payment**:
   - Card: `4242 4242 4242 4242`
   - Exp: `12/25`
   - CVC: `123`
7. [ ] Watch for "Setting up membership (n/30)" progress display
8. [ ] Should see ✅ within 10-20 seconds

**Expected**: After payment → redirected to My Reservations → see progress counter → reservation appears

## Test Case 3: Booking with Membership (Dev Bypass - if webhook fails)
Same as Test Case 2, but if it fails at 30/30:

1. [ ] Error message appears: "We couldn't finish booking automatically. Your payment went through..."
2. [ ] **DEV MODE**: Button appears: "Create Test Membership & Retry"
3. [ ] Click the button
4. [ ] System calls `/api/dev/membership/create-active-for-testing`
5. [ ] Automatically retries the booking
6. [ ] **Expected**: Reservation created immediately

## Test Case 4: Console Logging
1. [ ] Open browser DevTools Console (F12)
2. [ ] Complete a booking with membership required
3. [ ] Look for logs like:
   ```
   [Booking Attempt 1/30] Attempting to create reservation...
   [Booking Attempt 2/30] Attempting to create reservation...
   ...
   [Booking Attempt n/30] Reservation created successfully!
   ```
4. [ ] [ ] Verify attempt number increases each time

## Test Case 5: Manual Dev Endpoints (Optional)
Get your JWT token first:
```bash
# After logging in, in browser console:
console.log(localStorage.getItem('token'))  # or however token is stored
```

Then test endpoints:
```bash
# Check membership
curl -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/dev/membership/check

# Create test membership
curl -X POST -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/dev/membership/create-active-for-testing

# Delete for cleanup
curl -X DELETE -H "Authorization: Bearer YOUR_TOKEN" \
  http://localhost:8080/api/dev/membership
```

## Test Case 6: Edge Cases
- [ ] **Waiver not signed**: Show "Please sign the equipment waiver first" (doesn't attempt booking)
- [ ] **Already have membership**: Booking completes immediately (no retry loop)
- [ ] **Multiple equipment in cart**: First item requiring membership triggers membership flow
- [ ] **Session timeout**: If payment takes too long, session storage may expire (test edge)

## Success Criteria
✅ All test cases pass without errors
✅ Progress counter displays correctly (n/30)
✅ Reservation is created and visible after 10-30 seconds
✅ Console logs show attempt progression
✅ Dev bypass button works in dev mode
✅ No errors in browser console or backend logs

## Common Issues & Troubleshooting

| Issue | Symptom | Fix |
|-------|---------|-----|
| Webhook not firing | Stuck at 30/30, use dev bypass | Use dev bypass button or manually trigger webhook with `stripe listen` |
| Database connection lost | Backend DOWN status | Check PostgreSQL is running on port 5432 |
| Frontend not updated | Old code still running | Hard refresh (Cmd+Shift+R) or restart Vite dev server |
| Auth token missing | 401 Unauthorized on dev endpoints | Make sure you're logged in; check localStorage for token |
| Session storage cleared | "No pending reservation" | Re-complete payment flow - session TTL is 2 hours |

## Debug Commands

**Check backend is responsive:**
```bash
curl http://localhost:8080/actuator/health
```

**Check frontend is responsive:**
```bash
curl http://localhost:5173
```

**Check if user is authenticated (in browser console):**
```javascript
console.log(JSON.parse(localStorage.getItem('user')))
```

**Check pending reservation in session (in browser console):**
```javascript
console.log(JSON.parse(sessionStorage.getItem('pendingReservationAfterPayment')))
```

**Manually create test membership (after getting token):**
```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  http://localhost:8080/api/dev/membership/create-active-for-testing
```

## Files to Monitor During Testing

**Frontend**:
- `frontend/src/components/dashboard/MyReservations.jsx` (lines 112, 174, 182, 281-322)
- Browser console for retry logs

**Backend**:
- `backend/src/main/java/com/makerspace/backend/controller/DevMembershipController.java` (dev endpoints)
- `backend/src/main/java/com/makerspace/backend/services/ReservationService.java` (402 check)
- Server logs for webhook events

## Test Data Reference

| Field | Value |
|-------|-------|
| **Test Card** | 4242 4242 4242 4242 |
| **Expiry** | 12/25 |
| **CVC** | 123 |
| **Frontend URL** | http://localhost:5173 |
| **Backend API** | http://localhost:8080 |
| **Dev Membership Check** | GET /api/dev/membership/check |
| **Dev Create Membership** | POST /api/dev/membership/create-active-for-testing |
| **Dev Delete Membership** | DELETE /api/dev/membership |
| **Max Retry Attempts** | 30 |
| **Retry Interval** | 1000ms (1 second) |
| **Total Retry Window** | 30 seconds |

## Notes
- Stripe webhook takes 10-20+ seconds in production
- In local dev, webhooks may not fire automatically
- Dev bypass button only shows in development mode (`import.meta.env.DEV`)
- Test endpoints only available with dev profile active
- After testing, don't forget to clean up test memberships!
