# Complete Card Sign-In System - Full Testing Guide

## System Status
✅ **Backend**: Running on http://localhost:8080  
✅ **Frontend**: Running on http://localhost:5173  
✅ **Database**: PostgreSQL connected  
✅ **Email Service**: Configured  

---

## Quick Start - New Card Registration Flow

### Step 1: Sign In Form - Tap a New Card
1. Open http://localhost:5173/signin
2. Click the "Card" tab
3. In the demo input field, enter a NEW card UID (e.g., `TESTCARD001`)
4. Press Enter
5. Expected: "This card has not been assigned by an admin yet. Please contact admin."

### Step 2: Verify Card in Admin Dashboard
1. Open http://localhost:5173/admin (you may need to log in as admin first)
2. Look for "Unregistered Cards" section
3. You should see your new card with:
   - Card UID: `TESTCARD001`
   - Status: `PENDING`
   - Assigned Email: —
   - Assigned Role: —

### Step 3: Admin Assigns Email
1. In the Unregistered Cards section
2. Find your card `TESTCARD001`
3. Click "Assign Email" button
4. Enter an email (e.g., `testuser@example.com`)
5. Submit
6. Expected: Card status changes to `ASSIGNED`

### Step 4: Admin Assigns Role
1. Card is now showing status `ASSIGNED`
2. Click "Assign Role" button
3. Select a role: `MEMBER`, `STAFF`, or `ADMIN`
4. Submit
5. Expected: Card status changes to `ROLE_ASSIGNED`

### Step 5: User Requests OTP
1. Go back to http://localhost:5173/signin
2. Click "Card" tab
3. Enter the same card UID: `TESTCARD001`
4. Press Enter
5. You should see: "Card recognized!" with "Send Code to Email" button
6. Click "Send Code to Email"
7. Expected: "Code sent to your email" message

### Step 6: Check Email for OTP
- Check the email (`testuser@example.com`)
- Look for 6-digit OTP code (e.g., `880559`)
- Or check backend logs to see the generated code

### Step 7: User Signs In with OTP
1. Form now shows "Enter OTP Code" input
2. Enter the 6-digit code from email
3. Click "Sign In"
4. Expected: Redirected to dashboard with role-appropriate content

### Step 8: Verify Signed In
1. Should see member/staff/admin dashboard based on role
2. Should see role-appropriate menu and features
3. Card is now `ACTIVATED`

---

## Testing Different Roles

### Test 1: MEMBER Role
- New card: `CARD_MEMBER_001`
- Email: `member@example.com`
- Role: **MEMBER**
- Expected Dashboard: Regular member features, limited access

### Test 2: STAFF Role
- New card: `CARD_STAFF_001`
- Email: `staff@example.com`
- Role: **STAFF**
- Expected Dashboard: Staff controls, equipment management

### Test 3: ADMIN Role
- New card: `CARD_ADMIN_001`
- Email: `admin@example.com`
- Role: **ADMIN**
- Expected Dashboard: Full admin controls, user management

---

## API Testing (curl)

### 1. Simulate Card Tap (Create UnregisteredCard)
```bash
curl -X POST http://localhost:8080/api/v1/hardware/card-scan \
  -H "Content-Type: application/json" \
  -d '{
    "cardUid": "NEWCARD123",
    "deviceId": "sign-in-reader"
  }'
```
**Expected Response:**
```json
{
  "approved": false,
  "status": "DENIED",
  "message": "Card not registered - awaiting admin assignment",
  "memberName": null
}
```

### 2. Check Card Status
```bash
curl http://localhost:8080/api/v1/auth/card/check-by-uid?uid=NEWCARD123
```
**Expected Response:**
```json
{
  "found": true,
  "email": "",
  "status": "PENDING",
  "canSendOtp": false,
  "otpGenerated": false
}
```

### 3. Get All Unregistered Cards (Admin)
```bash
curl http://localhost:8080/api/v1/hardware/unregistered-cards \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```
**Expected:** List of unregistered cards

### 4. Assign Email to Card (Admin)
```bash
curl -X POST http://localhost:8080/api/v1/hardware/unregistered-cards/{id}/assign \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "email": "user@example.com"
  }'
```

### 5. Assign Role to Card (Admin)
```bash
curl -X POST http://localhost:8080/api/v1/hardware/unregistered-cards/{id}/assign-role \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "role": "MEMBER"
  }'
```

### 6. Request OTP (User)
```bash
curl -X POST http://localhost:8080/api/v1/auth/card/send-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "cardUid": "NEWCARD123"
  }'
```
**Expected Response:**
```json
{
  "success": "true",
  "message": "Code sent to your email"
}
```

### 7. Verify OTP (User)
```bash
curl -X POST http://localhost:8080/api/v1/auth/card/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "otp": "880559"
  }'
```
**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

## Card Status Progression

```
PENDING
  ↓ (Admin assigns email)
ASSIGNED
  ↓ (Admin assigns role)
ROLE_ASSIGNED
  ↓ (Admin sends OTP OR user requests OTP)
VERIFIED
  ↓ (User enters correct OTP)
ACTIVATED (Card now works for sign-in)
```

---

## Troubleshooting

### Issue: Card appears in admin dashboard but "not found" in sign-in
**Solution:** 
- Make sure to assign email first
- Make sure to assign role second
- Status must be at least ROLE_ASSIGNED

### Issue: "OTP has expired"
**Solution:**
- OTP codes expire after 1 hour
- Request a new code by clicking "Send Code" again

### Issue: "Invalid OTP"
**Solution:**
- Double-check the code from email
- Make sure you copied the entire 6-digit code
- Try sending a new code

### Issue: "Email code not received"
**Solution:**
- Check spam/promotions folder
- Check backend logs for email errors
- Verify email service is configured (SMTP settings)
- Try admin sending OTP instead of user request

### Issue: Card shows "not assigned by admin"
**Solution:**
- Go to admin dashboard
- Find the card in "Unregistered Cards"
- Click "Assign Email" and enter an email
- Click "Assign Role" and select a role

### Issue: "Server could not send a code"
**Solution:**
- This means email service failed
- Check backend configuration for SMTP
- Verify email credentials are correct
- Check backend logs for email service errors

---

## Deactivate Accounts for Testing

When you want to test with a fresh card, you can deactivate the user account:

1. Go to http://localhost:5173/admin
2. Find the user in the "Users" section
3. Click "Deactivate" or similar option
4. User account is now inactive
5. Card can be reused with a new email/role

Alternatively, delete the unregistered card:

1. Go to admin dashboard
2. Find card in "Unregistered Cards"
3. Click "Delete" button
4. Card is removed and can be re-created

---

## Complete Test Scenario

### Scenario: New Employee First-Day Setup

**Setup Phase:**
1. New employee taps their card at sign-in kiosk
2. Card appears in admin dashboard as PENDING
3. Admin logs in and assigns:
   - Email: `newemployee@makerspace.org`
   - Role: `STAFF`

**Activation Phase:**
1. Employee receives email with 6-digit code
2. Employee goes to sign-in portal
3. Employee taps their card
4. Employee enters code from email
5. Employee signed in to staff dashboard

**Verification:**
- Employee can now use their card for equipment access
- Card read at equipment shows "APPROVED"
- Access logs show employee name and timestamp

---

## Performance Notes

- OTP codes: Generated fresh, random 6-digit numbers
- OTP expiration: 1 hour from generation
- JWT tokens: Valid for configured duration (default 1 hour)
- Card scans: Logged to CardScan table for audit trail
- Database: All data persisted in PostgreSQL

---

## Security Features

✅ 6-digit random OTP (not sequential)  
✅ 1-hour expiration on codes  
✅ Email verification required  
✅ JWT token authentication  
✅ Role-based access control  
✅ Card status tracking  
✅ Admin audit trail  

---

## File Locations

- **Frontend Component:** `frontend/src/components/CardSignInForm.jsx`
- **Backend Controller:** `backend/src/main/java/com/makerspace/backend/controller/CardAuthController.java`
- **Backend Service:** `backend/src/main/java/com/makerspace/backend/services/CardService.java`
- **Admin Dashboard:** `frontend/src/components/admin-dashboard/UnregisteredCardsManager.jsx`

---

## Next Steps

1. ✅ Test with different card UIDs
2. ✅ Test each role type (MEMBER, STAFF, ADMIN)
3. ✅ Test OTP expiration
4. ✅ Test email delivery
5. ✅ Test multiple concurrent cards
6. ⬜ Deploy to production
7. ⬜ Configure real NFC readers
8. ⬜ Set up real email service

---

**System Ready for Testing! 🚀**
