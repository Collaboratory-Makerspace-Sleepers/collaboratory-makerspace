# Admin Dashboard - New Cards Management Quick Reference

## Location
**Admin Panel → Hardware → New Cards Tab**

## When Cards Appear
Cards automatically appear in this list when:
- A new/unregistered card UID is tapped at any reader
- The system doesn't recognize the card in its database
- The card will remain here until fully activated

## The 4-Step Admin Workflow

### 🟣 Step 1: Assign Email
**When:** Card is in "PENDING" status  
**Action:** Click "Assign Email" button  
**Input:** User's email address (e.g., john@example.com)  
**Result:** Status changes to "ASSIGNED"  
**What happens next:** Card is reserved for this email

### 🟡 Step 2: Assign Role (NEW)
**When:** Card is "ASSIGNED" and has an email  
**Action:** Click "Assign Role" dropdown  
**Options:**
- **Regular Member** - Regular member access (check-ins, training)
- **Staff** - Staff member (equipment management, training)
- **Admin** - Administrator (full system access)

**Result:** Role is saved to card  
**What it controls:** Which dashboard user sees after sign-in

### 🟠 Step 3: Send OTP
**When:** Card has email AND role assigned  
**Action:** Click "Send OTP" button  
**What happens:** 
- 6-digit code generated
- Email sent to user with OTP
- User receives email in inbox

### 🟢 Step 4: User Completes Sign-In
**What happens on user's end:**
1. User receives email with OTP code
2. User goes to sign-in page
3. User enters email + OTP code
4. User signs in
5. User taps card at reader to activate

**After card is tapped:**
- MemberCard is created in system
- User account gets assigned role
- Card status becomes "ACTIVATED"
- User has full access

---

## Table Columns Explained

| Column | Shows | Purpose |
|--------|-------|---------|
| **Card UID** | Hex string (e.g., ABC123) | Unique card identifier |
| **Status** | PENDING/ASSIGNED/VERIFIED/ACTIVATED | Current stage of registration |
| **Assigned Email** | user@example.com | Email card is assigned to |
| **Assigned Role** | MEMBER/STAFF/ADMIN | User's access level |
| **First Tap** | Date & time | When card first appeared |
| **Last Tap** | Date & time | Most recent card scan |
| **Taps** | Number | How many times card has been tapped |
| **Device** | Device name | Which reader detected it (e.g., "reader-1") |
| **Actions** | Buttons | Workflow controls (Email → Role → OTP) |

---

## Status Meanings

```
PENDING  ──assign email──>  ASSIGNED  ──assign role + OTP──>  VERIFIED  ──tap card──>  ACTIVATED
  (New)                   (Email set)                       (OTP verified)            (Live)
```

| Status | What It Means | What To Do |
|--------|---------------|-----------|
| 🔵 PENDING | Brand new card, no email yet | Click "Assign Email" |
| 🟡 ASSIGNED | Email assigned, need role & OTP | Click "Assign Role" then "Send OTP" |
| 🟢 VERIFIED | OTP sent & verified, ready | User taps card to activate |
| 🟣 ACTIVATED | User has access | Done! User can use card |

---

## Quick Tips

✅ **Before Sending OTP:**
- Make sure email is correct (user will get OTP there)
- Assign role (determines what user sees)
- Both must be done before Send OTP button appears

✅ **Email Assignment:**
- Always use the user's personal email
- Check for typos (OTP won't reach wrong email)
- One card per email (can't assign same email twice)

✅ **Role Selection:**
- **MEMBER** = Regular person using space
- **STAFF** = Person managing equipment/training
- **ADMIN** = System administrator/owner
- Can change role later from Users page

✅ **Auto-Refresh:**
- Table updates every 10 seconds
- New cards appear automatically
- No need to refresh page

---

## Common Issues & Solutions

### Card Not Appearing
- Verify card was actually tapped at reader
- Check reader is connected and working
- Wait up to 10 seconds for auto-refresh

### "Send OTP" Button Grayed Out
- Email must be assigned first
- Role must be assigned (click "Assign Role")
- Both fields required before OTP

### User Didn't Receive Email
- Check email address is correct
- Check spam/junk folder
- Verify email service is running
- Try sending OTP again

### Status Stuck on "ASSIGNED"
- Verify OTP was sent (check logs)
- User may not have tapped card yet
- User may not be signed in

### Card Shows "PENDING" Multiple Times
- Same card tapped again without assignment
- Admin needs to use first instance
- Other instances will be duplicates

---

## Role Guide

### 👤 Regular Member
**Access:**
- Member Dashboard
- Check-in system
- Equipment reservations
- Training courses
- Personal profile

**Typical Users:** Makerspace members

---

### 👨‍💼 Staff
**Access:**
- Everything MEMBER can do
- Equipment management
- Training administration
- Check-in coordination
- Staff reporting

**Typical Users:** Instructors, equipment managers, staff

---

### 👨‍💻 Admin
**Access:**
- Everything above plus:
- User management
- System settings
- Admin dashboard
- Reports & analytics
- Role assignments
- System configuration

**Typical Users:** Makerspace owners, administrators

---

## Pro Tips

1. **Batch Processing:** Process multiple new cards in sequence
2. **Verify Emails:** Have users confirm email before assigning
3. **Role Consistency:** Use same role for same type of user
4. **Record Keeping:** Note which role was assigned for reference
5. **Test First:** Verify with one card before processing many

---

## Support

If cards aren't appearing or workflow is stuck:
1. Refresh the page
2. Check backend is running (`curl localhost:8080/actuator/health`)
3. Verify Redis and email service are running
4. Check admin logs for errors

---

**Last Updated:** 2026-10-25  
**Version:** 2.0
