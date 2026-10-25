# Admin Dashboard Guide - Unregistered Cards Management

## Quick Answer to "Where is my data?"

**Your unregistered cards data is here:**

### Navigate to:
1. **Admin Dashboard**: http://localhost:5173/admin-dashboard/home
2. **Click**: "Hardware Access" in the left sidebar
3. **Click**: "NEW CARDS" tab (first tab at the top)

That's where you'll see all unregistered cards with their status!

---

## Admin Dashboard Structure

### The Four Tabs in Hardware Access

```
┌─────────────────────────────────────────┐
│ NEW CARDS | CARD TAPS | CARDS | WAIVERS |
└─────────────────────────────────────────┘
```

#### Tab 1: **NEW CARDS** ← Use this for unregistered cards!
- Shows: Cards awaiting admin setup
- Status: PENDING → ASSIGNED → ROLE_ASSIGNED → VERIFIED → ACTIVATED
- Actions: Assign Email, Assign Role, Send OTP, Delete
- Refresh: Every 10 seconds (auto-refresh)
- **This is the NEW card registration system**

#### Tab 2: CARD TAPS
- Shows: Real-time card tap activity
- Live feed of card reader events
- Timestamp and device ID for each tap

#### Tab 3: CARDS
- Shows: Already assigned member cards
- Status: ACTIVE or INACTIVE
- For cards already linked to user accounts
- "Assign a card" section (for existing members)
- **This is the OLD system for registered members**
- Why it's empty: No active member cards currently

#### Tab 4: WAIVERS & CHECK-INS
- Shows: Facility access logs
- Historical data for check-ins and waivers
- Administrative records

---

## Unregistered Cards Workflow

### Step-by-Step Process

#### 1. User Taps New Card at Sign-In
```
User Action:
  1. Go to: http://localhost:5173/signin
  2. Click: "Card" tab
  3. Enter: Card UID (e.g., ABC123XYZ)
  4. Press: Enter

Backend Creates:
  • UnregisteredCard in database
  • Status: PENDING
  • Timestamp: first tap recorded
  • Device: "sign-in-reader"
```

#### 2. Card Appears in Admin Dashboard
```
Admin Dashboard:
  1. Go to: http://localhost:5173/admin-dashboard/hardware
  2. Click: "NEW CARDS" tab
  3. See: New card with status PENDING

Card Details Shown:
  • Card UID: ABC123XYZ
  • Status: PENDING
  • Assigned Email: —
  • Assigned Role: —
  • First Tap: [timestamp]
  • Last Tap: [timestamp]
  • Taps: 1
  • Device: sign-in-reader
```

#### 3. Admin Assigns Email
```
Action:
  1. Find card in "NEW CARDS" tab
  2. Click: "Assign Email" button
  3. Enter: user@example.com
  4. Click: Submit

Result:
  • Card status: ASSIGNED
  • Email field now shows: user@example.com
  • Ready for role assignment
```

#### 4. Admin Assigns Role
```
Action:
  1. Card still in "NEW CARDS" tab
  2. Click: "Assign Role" button
  3. Select: MEMBER, STAFF, or ADMIN
  4. Click: Submit

Result:
  • Card status: ROLE_ASSIGNED
  • Role field shows: Selected role
  • Ready for OTP (user or admin initiated)

Role Effects:
  • MEMBER → Regular member dashboard after sign-in
  • STAFF → Staff dashboard with management tools
  • ADMIN → Full admin access
```

#### 5. User Requests OTP Code
```
Option A: User Initiates
  1. Go back to: http://localhost:5173/signin
  2. Enter same card UID
  3. See: "Send Code to Email" button
  4. Click: "Send Code to Email"
  5. Backend generates and sends 6-digit code

Option B: Admin Initiates (optional)
  1. In "NEW CARDS" tab
  2. Click: "Send OTP" button
  3. Code sent to email immediately

Result:
  • Card status: VERIFIED
  • Email: Receives 6-digit OTP code
  • Code valid for: 1 hour
  • Can request new code anytime
```

#### 6. User Enters OTP Code
```
User Action:
  1. Check email for code
  2. Go to sign-in form
  3. See: "Enter OTP Code" input
  4. Enter: 6-digit code from email
  5. Click: "Sign In"

Backend Verifies:
  • OTP matches stored code
  • OTP not expired
  • Creates user account (if new)
  • Issues JWT token
```

#### 7. User Signed In
```
Result:
  • Redirected to dashboard
  • Dashboard matches assigned role:
    - MEMBER: Member features
    - STAFF: Staff controls
    - ADMIN: Admin panel
  • Card status: ACTIVATED
  • Card can now be used for equipment access

Admin Dashboard:
  • Card status in "NEW CARDS" tab: ACTIVATED
  • Card is now "registered"
  • Can manage/reassign if needed
```

---

## Card Status Definitions

| Status | Meaning | What Admin Can Do |
|--------|---------|------------------|
| PENDING | Card tapped, not yet assigned | Assign email |
| ASSIGNED | Email assigned, waiting for role | Assign role |
| ROLE_ASSIGNED | Email & role assigned, ready | Send OTP or wait for user request |
| VERIFIED | OTP generated/sent, waiting for entry | User enters code to complete |
| ACTIVATED | User signed in successfully | Card works for access, can reassign |

---

## Admin Actions on Unregistered Cards

### Assign Email
```
Button: "Assign Email"
Input: User's email address
Validates: Email format is correct
Result: Card status → ASSIGNED

Error Cases:
  • Invalid email format: Shows error
  • Email already used: May show warning (depends on policy)
```

### Assign Role
```
Button: "Assign Role"
Options: MEMBER, STAFF, ADMIN
Validates: Role is valid
Result: Card status → ROLE_ASSIGNED

Role Permissions:
  • MEMBER: Basic access, rent equipment, book classes
  • STAFF: Manage equipment, approve training
  • ADMIN: Full system access, manage users, reports
```

### Send OTP
```
Button: "Send OTP" (optional)
Triggers: Backend to generate and email OTP code
Result: Card status → VERIFIED
Recipient: The assigned email address
Code: 6-digit random number
Expires: 1 hour from sending

When to Use:
  • If user can't request themselves
  • To speed up onboarding
  • For testing purposes
```

### Delete Card
```
Button: "Delete"
Confirms: "Delete card ABC123? This removes it from queue for testing."
Result: Card removed from system
Use Case: Reset for testing, remove test data

Note:
  • Cannot be undone
  • If user needs card later, must tap again
  • Useful for starting fresh with testing
```

---

## Data Persistence

### What Happens to Card Data

When card is registered and activated:
- UnregisteredCard record stays in database
- MemberCard record created (linked to user)
- Card can be used for access checks
- History is maintained for audit trail
- Card can be reassigned to different role

### Previous Test Data

The data from your earlier testing is still in the database!
- Card 85F181D4: Already assigned to jellyh393@gmail.com with MEMBER role
- Status: VERIFIED (has OTP generated)
- Can be used for sign-in

To use it again:
1. Go to sign-in form
2. Enter: 85F181D4
3. Click: "Send Code" (or use existing OTP if still valid)
4. Check email for code
5. Enter code to sign in

---

## Common Scenarios

### Scenario 1: New Employee First Day
```
1. Employee taps card at sign-in kiosk
2. Card appears as PENDING in "NEW CARDS" tab
3. Admin assigns: email + STAFF role
4. Employee receives email with OTP
5. Employee enters OTP at sign-in
6. Redirected to staff dashboard
7. Card is now ACTIVATED and ready to use
```

### Scenario 2: Testing Multiple Roles
```
Test MEMBER:
  1. Tap card: TEST_MEMBER_001
  2. Assign: member@test.com, MEMBER role
  3. Sign in with OTP
  4. Verify: Member dashboard appears

Test STAFF:
  1. Tap card: TEST_STAFF_001
  2. Assign: staff@test.com, STAFF role
  3. Sign in with OTP
  4. Verify: Staff dashboard appears

Test ADMIN:
  1. Tap card: TEST_ADMIN_001
  2. Assign: admin@test.com, ADMIN role
  3. Sign in with OTP
  4. Verify: Admin dashboard appears
```

### Scenario 3: Reset Card for Re-testing
```
1. Go to "NEW CARDS" tab
2. Find card to reset
3. Click: "Delete" button
4. Card removed from system
5. Have user tap card again at sign-in
6. Card re-appears as PENDING
7. Repeat assignment process
```

---

## Troubleshooting

### Issue: "No cards shown in NEW CARDS tab"

Check:
1. Are you on the correct tab? (Should say "NEW CARDS")
2. Has any card been tapped at sign-in yet?
3. Check browser console (F12) for JavaScript errors
4. Refresh the page
5. Verify backend is running: `curl http://localhost:8080/actuator/health`

Solution:
1. Open sign-in form
2. Tap a new card (enter UID and press Enter)
3. Go back to admin dashboard
4. Wait 10 seconds (auto-refreshes)
5. Card should appear

### Issue: "Assign Email button doesn't work"

Check:
1. Are you logged in as admin?
2. Do you have MANAGE_EQUIPMENT permission?
3. Is the email format valid?
4. Check browser console (F12) for error messages

Try:
1. Refresh the page
2. Log out and log back in
3. Check backend logs for error messages

### Issue: "Email code not received"

Check:
1. Spam/promotions folder in email
2. Check if email address is correct
3. Check backend logs for email service errors
4. Verify email is configured in .env

Try:
1. Admin clicks "Send OTP" instead of user request
2. Try a different email address
3. Check .env file for SMTP configuration

### Issue: "OTP Expired"

Explanation: OTP codes expire after 1 hour

Solution:
1. User clicks "Send Code" again
2. New OTP generated and sent
3. Use new code to sign in

---

## FAQ

**Q: Where are my previous test cards?**
A: Still in the database! Check the "NEW CARDS" tab or use the card UID to search.

**Q: Can I change a card's role after assignment?**
A: Yes! Find the card in "NEW CARDS" tab and click "Assign Role" again.

**Q: Can I reassign a card to a different email?**
A: Currently, you'd need to delete it and start over. Or manually update via database.

**Q: How long is OTP valid?**
A: 1 hour from generation. After that, user must request a new code.

**Q: Can multiple users use the same card?**
A: No, each card is assigned to one email/role. Delete and reassign for different user.

**Q: Does the card work for equipment access after sign-in?**
A: Yes! Once ACTIVATED, card works for all equipment access and check-ins.

---

## Summary

### The Card Lifecycle:
```
PENDING (User taps)
  ↓
ASSIGNED (Admin enters email)
  ↓
ROLE_ASSIGNED (Admin selects role)
  ↓
VERIFIED (OTP generated/sent)
  ↓
ACTIVATED (User signs in)
  ↓
Ready for Equipment Access!
```

### Key URLs:
- Admin Dashboard: http://localhost:5173/admin-dashboard/home
- Hardware Access: Click "Hardware Access" in sidebar
- Sign-In Form: http://localhost:5173/signin

### Main Tab:
- **NEW CARDS** tab is your primary interface for unregistered card management

---

**Everything is working! Just navigate to the correct tab and you'll see all your data.** 🚀
