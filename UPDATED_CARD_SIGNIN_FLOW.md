# 📋 Updated Card Sign-In Flow

## ✅ What Changed

The card sign-in flow has been updated to allow **users** to request OTP codes instead of admins having to send them manually.

### Old Flow ❌
1. Admin assigns email to card
2. Admin clicks "Send OTP" → code sent to email
3. User taps card
4. User enters the code admin sent
5. User signs in

### New Flow ✅
1. Admin assigns email + role to card
2. Admin does NOT need to send OTP
3. User taps card
4. User sees their email and clicks "Send Code"
5. User receives code at their email
6. User enters code and signs in

---

## 🎯 Benefits

- ✅ Admin doesn't need to manually send OTP for each card
- ✅ User can request code anytime (resend multiple times)
- ✅ Same security: OTP verification, JWT token, role-based access
- ✅ Simpler workflow for both admin and user
- ✅ Admin can still manually send OTP if needed (future enhancement)

---

## 🔧 Backend Changes

### New Endpoint: POST /api/v1/auth/card/send-otp

**Request:**
```json
{
  "email": "user@example.com",
  "cardUid": "85F181D4"
}
```

**Response (Success):**
```json
{
  "success": "true",
  "message": "Code sent to your email"
}
```

**Response (Error):**
```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Card has not been assigned by admin yet"
}
```

### Updated Endpoint: GET /api/v1/auth/card/check-by-uid

**Changes:**
- `needsOtp` → `canSendOtp` (more accurate name)
- Card must be in ASSIGNED, ROLE_ASSIGNED, or VERIFIED status

**Response:**
```json
{
  "found": true,
  "email": "user@example.com",
  "status": "ASSIGNED",
  "canSendOtp": true,
  "otpGenerated": false
}
```

### Updated CardService

**New Method: `generateAndSendOtp(UnregisteredCard)`**
- Generates 6-digit random OTP
- Sets OTP expiration (1 hour)
- Sends OTP to email
- Updates card status to VERIFIED

---

## 📱 Frontend Changes

### CardSignInForm.jsx - New States

```javascript
// New step: "sending-otp" - when user clicks "Send Code"
const [step, setStep] = useState("waiting"); 
// Sequence: waiting → card-found → sending-otp → entering-otp → processing
```

### New Steps in Flow

1. **waiting**: Waiting for card tap
2. **card-found**: Card tapped, show "Send Code" button
3. **sending-otp**: Sending request to backend
4. **entering-otp**: Show OTP input field
5. **processing**: Verifying OTP and signing in

### New Handler: handleSendOtp()

```javascript
// Called when user clicks "Send Code"
// Calls: POST /api/v1/auth/card/send-otp
// Updates step to: "entering-otp"
```

---

## 🧪 Testing the New Flow

### Step 1: Admin Setup (Same as Before)
```
1. Go to http://localhost:5173
2. Log in as admin
3. Navigate to Hardware > New Cards
4. Find card UID: 85F181D4
5. Click "Assign Email" → user@test.com
6. Click "Assign Role" → MEMBER
7. Click "Send OTP" (OPTIONAL - user can request it)
   → Card status now shows VERIFIED
```

**Important:** The "Send OTP" button in admin is still there but OPTIONAL now. User can request their own code.

### Step 2: User Login with New Flow
```
1. Open http://localhost:5173/signin
2. Click "Card" tab
3. Enter card UID: 85F181D4 (or tap card)
4. ✅ NEW: See email and "Send Code" button
5. ✅ NEW: Click "Send Code to Email"
6. ✅ NEW: Code is sent to user@test.com
7. User checks email for code
8. User enters code (6 digits)
9. ✅ User is signed in and redirected to dashboard
```

---

## 🔐 Security Flow

```
User taps card
     ↓
GET /api/v1/auth/card/check-by-uid?uid=...
     ↓
Response: { found: true, email: "...", canSendOtp: true }
     ↓
User clicks "Send Code"
     ↓
POST /api/v1/auth/card/send-otp { email, cardUid }
     ↓
Backend generates 6-digit random OTP
     ↓
Backend saves OTP + expiration (1 hour) to database
     ↓
Backend sends email to user
     ↓
User receives email with code
     ↓
User enters code in form
     ↓
POST /api/v1/auth/card/verify-otp { email, otp }
     ↓
Backend verifies OTP matches saved code
     ↓
Backend checks OTP not expired
     ↓
Backend creates user (if new)
     ↓
Backend generates JWT token
     ↓
POST /api/v1/auth/card/activate with JWT
     ↓
Backend activates card
     ↓
User redirected to dashboard with role access
```

---

## 🛠️ API Endpoints Summary

### Public Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/auth/card/check-by-uid?uid=...` | Check card status |
| POST | `/api/v1/auth/card/send-otp` | **NEW** - Send OTP to email |
| POST | `/api/v1/auth/card/verify-otp` | Verify OTP & get token |
| POST | `/api/v1/auth/card/activate` | Activate card (authenticated) |

### Admin Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/admin/hardware/unregistered-cards` | List cards |
| POST | `/api/v1/admin/hardware/unregistered-cards/{id}/assign-email` | Assign email |
| POST | `/api/v1/admin/hardware/unregistered-cards/{id}/assign-role` | Assign role |
| POST | `/api/v1/admin/hardware/unregistered-cards/{id}/send-otp` | Manually send OTP (optional) |
| DELETE | `/api/v1/admin/hardware/unregistered-cards/{id}` | Delete card |

---

## 📊 Card Status Progression

```
PENDING
  ↓ (admin assigns email)
ASSIGNED
  ↓ (admin assigns role)
ROLE_ASSIGNED
  ↓ (user clicks "Send Code" OR admin clicks "Send OTP")
VERIFIED ← User can now enter code
  ↓ (user enters valid code)
USER CREATED
  ↓ (user taps card again to activate)
ACTIVATED ✅ (user can log in anytime with card)
```

---

## ✨ Key Improvements

✅ **More User-Friendly**
- Users control when they request codes
- Users can resend codes if they miss the email
- Admin doesn't bottleneck the process

✅ **Same Security**
- OTP codes still 6-digit random
- OTP codes still expire in 1 hour
- JWT tokens still required
- Email verification still required

✅ **Simpler Admin Dashboard**
- Admin assigns email and role
- Admin doesn't need to manually send OTP
- Users request codes when they're ready to sign in

✅ **Better Error Messages**
- "Card has not been assigned" if missing email/role
- "Email does not match" if mismatch
- "Code sent successfully" confirmation

---

## 🚀 New Test Scenarios

### Scenario 1: User-Initiated OTP (NEW)
```
1. Admin assigns card email + role
2. Admin does NOT click "Send OTP"
3. User taps card
4. User clicks "Send Code"
5. User receives code and signs in
✅ Status: READY TO TEST
```

### Scenario 2: Multiple OTP Requests (NEW)
```
1. Admin assigns card
2. User clicks "Send Code" (gets code 1)
3. User deletes email by accident
4. User clicks "Send Code" again (gets code 2)
5. User uses code 2 to sign in
✅ Status: READY TO TEST
```

### Scenario 3: Admin Manual Send (OPTIONAL)
```
1. Admin assigns card + role
2. Admin clicks "Send OTP" anyway
3. User immediately has code
4. User signs in with admin-sent code
✅ Status: STILL WORKS (backward compatible)
```

### Scenario 4: Card Already Has OTP
```
1. Admin assigns card + sends OTP manually
2. User taps card
3. User sees code was already sent
4. User uses that code to sign in
✅ Status: STILL WORKS
```

---

## 🔍 Troubleshooting

### Issue: User clicks "Send Code" but nothing happens
**Solution:**
- Check backend logs: `CardService] OTP sent to ...`
- Verify email service configured in .env
- Check user's email for code

### Issue: "Code not matching" error
**Solution:**
- Make sure user entered all 6 digits
- Code expires after 1 hour
- Can request new code by tapping card again

### Issue: Admin dashboard still shows "Send OTP" button
**Solution:**
- That's correct! Admin can still manually send OTP
- It's now optional instead of required
- Users can request their own codes

### Issue: Card doesn't show "Send Code" button
**Solution:**
- Card must have email assigned
- Card must be in ASSIGNED status or higher
- Check admin dashboard - might not be fully assigned yet

---

## 📝 Database Changes

The UnregisteredCard table structure remains the same:
- `card_uid` - Card ID
- `status` - Current status (PENDING, ASSIGNED, ROLE_ASSIGNED, VERIFIED, ACTIVATED)
- `assigned_to_email` - Email assigned by admin
- `assigned_role` - Role (MEMBER/STAFF/ADMIN)
- `otp_code` - Generated code
- `otp_generated_at` - When OTP was generated
- `otp_expires_at` - When OTP expires (1 hour later)
- `otp_verified` - Whether user entered correct code

**No migration needed** - all existing data still works!

---

## 🎯 Summary

The card sign-in system now supports **user-initiated OTP requests**:

1. Admin assigns email + role (required)
2. Admin sends OTP (optional - user can request it)
3. User taps card and clicks "Send Code"
4. User receives OTP at their email
5. User enters OTP to sign in
6. User redirected to their role dashboard

**Status**: ✅ READY FOR TESTING

---

## 📊 Verification Checklist

- [ ] Backend compiles without errors
- [ ] New endpoint `/api/v1/auth/card/send-otp` exists
- [ ] CardSignInForm has "Send Code" button
- [ ] Card status shows correctly in admin dashboard
- [ ] Test basic flow: tap → send code → enter code → sign in
- [ ] Test multiple OTP requests
- [ ] Test expired OTP (wait 1+ hour)
- [ ] Test wrong OTP code
- [ ] Test with different roles (MEMBER/STAFF/ADMIN)
- [ ] Verify email is received
- [ ] Verify JWT token is issued
- [ ] Verify role-appropriate dashboard loads

---

**Last Updated**: October 25, 2026  
**Status**: ✅ COMPLETE & READY FOR TESTING
