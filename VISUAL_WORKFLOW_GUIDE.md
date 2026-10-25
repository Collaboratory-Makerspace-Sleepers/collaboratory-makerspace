# Visual Workflow Guide - New Cards Registration

## 🎯 The Complete Flow At A Glance

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        UNREGISTERED CARD WORKFLOW                       │
└─────────────────────────────────────────────────────────────────────────┘

STEP 1: NEW CARD TAPPED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  User taps new/unregistered card at access point
         ↓
  Card Reader captures UID (e.g., ABC123DEF456)
         ↓
  Backend receives card scan
         ↓
  Status: 🔴 PENDING
         ↓
  📊 Admin Dashboard shows card in "New Cards" table


STEP 2: ADMIN ASSIGNS EMAIL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Admin navigates to Admin Panel
         ↓
  Clicks: Hardware → New Cards Tab
         ↓
  Finds card with status 🔴 PENDING
         ↓
  Clicks: "Assign Email" button
         ↓
  Enters: user@example.com
         ↓
  Confirms assignment
         ↓
  Status: 🟡 ASSIGNED
         ↓
  Email field now shows: user@example.com


STEP 3: ADMIN ASSIGNS ROLE (NEW FEATURE ⭐)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Admin sees card with status 🟡 ASSIGNED
         ↓
  Clicks: "Assign Role" dropdown
         ↓
  Chooses role type:
    • 👤 Regular Member (basic access)
    • 👨‍💼 Staff (equipment management)
    • 👨‍💻 Admin (full system access)
         ↓
  Confirms role selection
         ↓
  Role field now shows: ADMIN | STAFF | MEMBER
         ↓
  Role saved to database


STEP 4: ADMIN SENDS OTP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Admin sees card with both email AND role assigned
         ↓
  Clicks: "Send OTP" button
         ↓
  Backend generates: 6-digit random code (e.g., 123456)
         ↓
  Email sent to: user@example.com
         ↓
  User receives email with OTP code
         ↓
  Status: 🟠 OTP Sent
         ↓
  Button changes to: "✓ Awaiting User"


STEP 5: USER SIGNS IN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  User checks email inbox
         ↓
  Finds email with subject: "Card Activation Code"
         ↓
  Email contains: OTP code (e.g., "123456")
         ↓
  User navigates to: localhost:5173/signin
         ↓
  Enters: email address (user@example.com)
         ↓
  Clicks: "Send Code"
         ↓
  Receives OTP via email
         ↓
  Enters: OTP code (123456)
         ↓
  Clicks: "Sign In"
         ↓
  Backend verifies: OTP matches & not expired
         ↓
  User authenticated ✓
         ↓
  JWT token issued to browser


STEP 6: USER TAPS CARD TO ACTIVATE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  User (now logged in) taps card at reader
         ↓
  Card Reader captures UID again
         ↓
  Backend receives: /api/v1/hardware/card-scan
         ↓
  Backend checks:
    • Is user authenticated? ✓ (has JWT token)
    • Was OTP verified? ✓ (verified in step 5)
    • Is this an unregistered card? ✓
         ↓
  Creates: MemberCard entry
         ↓
  Assigns: User's role to account
         ↓
  Status: 🟢 ACTIVATED
         ↓
  Admin dashboard shows: ✓ OTP Verified, User Active


STEP 7: USER SEES ROLE-BASED DASHBOARD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  After card tap (activation), user is granted access
         ↓
  System checks: What role was assigned?
         ↓
  If Role = 👤 MEMBER
    └─> Dashboard: Member Portal
        • Check-in system
        • Reservations
        • Training
        • Personal profile
         ↓
  If Role = 👨‍💼 STAFF
    └─> Dashboard: Staff Portal
        • Equipment management
        • Training admin
        • Check-in coordination
        • Staff reports
         ↓
  If Role = 👨‍💻 ADMIN
    └─> Dashboard: Admin Portal
        • User management
        • System settings
        • Reports & analytics
        • Full control
         ↓
  User can now use card for access! ✅

```

---

## 📋 Admin Dashboard Table View

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ NEW CARDS - Unregistered Cards Management                                          🔄 Auto-refresh (10s) │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘

│ Card UID       │ Status   │ Email            │ Role    │ Taps │ Last Tap          │ Actions              │
├────────────────┼──────────┼──────────────────┼─────────┼──────┼───────────────────┼──────────────────────┤
│ ABC123DEF456   │ 🔴 PEND. │ —                │ —       │  1   │ Oct 25 1:15 PM    │ [Assign Email]       │
│ XYZ789GHI012   │ 🟡 ASSG. │ john@gmail.com   │ —       │  2   │ Oct 25 1:20 PM    │ [Assign Role]        │
│ QWE456RTY789   │ 🟠 ASSG. │ jane@gmail.com   │ ADMIN   │  3   │ Oct 25 1:25 PM    │ [Send OTP]           │
│ ASD123FGH456   │ 🟢 VRFD. │ bob@gmail.com    │ STAFF   │  5   │ Oct 25 1:30 PM    │ ✓ Verified, awaiting │
│ ZXC789VBN012   │ 🟣 ACTV. │ alice@gmail.com  │ MEMBER  │  7   │ Oct 25 1:35 PM    │ ✓ Activated          │

Legend:
  🔴 PENDING   = New card, no email assigned yet
  🟡 ASSIGNED  = Email assigned, need role & OTP
  🟠 OTP_SENT  = OTP sent, awaiting user sign-in
  🟢 VERIFIED  = OTP verified, ready for activation
  🟣 ACTIVATED = User active, full access granted
```

---

## 🎯 Button States & Actions

### 🔴 Status: PENDING
```
Action Available: [ Assign Email ]

User sees: Email input field
Action: Enter user's email address
Result: Status → ASSIGNED
```

### 🟡 Status: ASSIGNED (No Role)
```
Action Available: [ Assign Role ]

User sees: Dropdown menu with:
  • Regular Member (👤)
  • Staff (👨‍💼)
  • Admin (👨‍💻)

Action: Select appropriate role
Result: Role saved, ready for OTP
```

### 🟡 Status: ASSIGNED (With Role)
```
Action Available: [ Send OTP ]

User sees: Ready to send button
Action: Click to send OTP email
Result: OTP generated and emailed
```

### 🟠 Status: OTP_SENT
```
Action: NONE (user must sign in)

What's Happening:
  • OTP sent to user's email
  • User checks inbox
  • User signs in with OTP
  • User gets JWT token
  • User must tap card to activate
```

### 🟢 Status: VERIFIED
```
Action: NONE (awaiting user card tap)

What's Happening:
  • User is logged in ✓
  • OTP is verified ✓
  • User taps card...
  • Card detected
  • MemberCard created
  • Role assigned
```

### 🟣 Status: ACTIVATED
```
Status: ✓ COMPLETE

Showing: "✓ Card Active"

What Happened:
  • User tapped card ✓
  • MemberCard created ✓
  • Role assigned to user ✓
  • User has dashboard access ✓
  • Card scan history recorded ✓
```

---

## 🔍 What Admin Sees in Each Column

```
┌─────────────────────────────────────────────────────────────────┐
│ Column        │ What It Shows                   │ What To Notice  │
├─────────────────────────────────────────────────────────────────┤
│ Card UID      │ Unique hex code (ABC123...)     │ Each card unique│
│               │                                 │ Can't change    │
├─────────────────────────────────────────────────────────────────┤
│ Status        │ Current stage of registration   │ See status key  │
│               │ Shows color + text              │ above           │
├─────────────────────────────────────────────────────────────────┤
│ Email         │ User's email (john@gmail.com)   │ Must be correct │
│               │ Shows "—" if not assigned       │ for OTP to work │
├─────────────────────────────────────────────────────────────────┤
│ Role          │ User's access level             │ Determines      │
│               │ MEMBER, STAFF, or ADMIN         │ dashboard       │
│               │ Shows "—" if not assigned       │ access level    │
├─────────────────────────────────────────────────────────────────┤
│ Taps          │ How many times card was scanned │ Duplicate cards │
│               │ Starts at 1, increases each tap │ possible        │
├─────────────────────────────────────────────────────────────────┤
│ Last Tap      │ Most recent scan time           │ Shows if active │
│               │ Updates each time card tapped   │ or dormant      │
├─────────────────────────────────────────────────────────────────┤
│ Device        │ Which reader detected it        │ For debugging   │
│               │ Device name/ID from reader      │ card issue      │
├─────────────────────────────────────────────────────────────────┤
│ Actions       │ Available workflow buttons      │ Changes based   │
│               │ Changes as you progress         │ on current      │
│               │ through workflow                │ status          │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✅ Checklist for Admins

When a new card appears:

```
[ ] Step 1: Click "Assign Email"
    └─ [ ] Enter correct email address
    └─ [ ] Verify spelling (OTP goes there!)
    └─ [ ] Confirm

[ ] Step 2: Click "Assign Role"
    └─ [ ] Select appropriate role
         • Regular Member? Team member using space
         • Staff? Instructor or equipment manager
         • Admin? System administrator
    └─ [ ] Confirm selection

[ ] Step 3: Click "Send OTP"
    └─ [ ] Verify email and role are set
    └─ [ ] Send OTP to email
    └─ [ ] Notify user to check email

[ ] Step 4: Wait for User
    └─ [ ] User receives email ✓
    └─ [ ] User signs in ✓
    └─ [ ] User taps card ✓
    └─ [ ] Status shows ACTIVATED ✓

[ ] Complete! Card is now active
```

---

## 🚨 Common Issues & Quick Fixes

```
Issue: "Assign Role" button is grayed out
Fix:   Make sure email is assigned first
       Both email AND role required before OTP

Issue: User didn't receive OTP email
Fix:   Check email address is correct
       Check spam/junk folder
       Verify email service is running
       Try sending OTP again

Issue: Card shows PENDING multiple times
Fix:   This is duplicate registration
       Use the first instance
       Ignore duplicates

Issue: Status stuck on ASSIGNED
Fix:   Make sure OTP was actually sent
       Check if user signed in
       Verify user tapped card at reader

Issue: User sees wrong dashboard
Fix:   Check UnregisteredCard.assignedRole in DB
       Verify role assignment went through
       Check user's role in database
```

---

## 📊 System Status Quick Check

To verify everything is working:

```bash
# Check Backend
curl http://localhost:8080/actuator/health
Response should be: {"status":"UP"}

# Check Frontend
Open browser: http://localhost:5173
Should load the makerspace website

# Check New Cards Dashboard
Navigate to: Admin → Hardware → New Cards
Should show table with unregistered cards

# Check Endpoints
curl http://localhost:8080/api/v1/hardware/unregistered-cards \
  -H "Authorization: Bearer <token>"
Should return list of cards
```

---

**Version:** 2.0  
**Last Updated:** October 25, 2026  
**Status:** ✅ Production Ready
