# Unregistered Card Registration with Role Assignment

## Overview

The Unregistered Card Registration system now includes **role assignment**, allowing admins to specify the access level (Regular Member, Staff, or Admin) for new users during card registration. This determines which dashboard the user sees after sign-in.

## Complete Workflow

### Step 1: New Card Tap
```
User taps unregistered card at access point
    ↓
Card UID is captured by reader
    ↓
Backend receives /api/v1/hardware/card-scan POST
    ↓
CardService.scan() checks if card is registered
    ↓
If NOT registered → Creates UnregisteredCard entry (status: PENDING)
    ↓
Admin Dashboard shows new card in "New Cards" tab
```

### Step 2: Admin Assigns Email
```
Admin navigates to Admin Dashboard → Hardware → New Cards
    ↓
Admin sees new card UID with status "PENDING"
    ↓
Admin clicks "Assign Email" button
    ↓
Admin enters user's email address
    ↓
Card status changes to "ASSIGNED"
```

### Step 3: Admin Assigns Role
```
Admin sees card with status "ASSIGNED"
    ↓
Admin clicks "Assign Role" dropdown
    ↓
Admin selects one of:
  • MEMBER (Regular member - basic access)
  • STAFF (Staff member - training & equipment management)
  • ADMIN (Administrator - full system access)
    ↓
Role is stored in database: UnregisteredCard.assignedRole
    ↓
Workflow progresses to OTP step
```

### Step 4: Admin Sends OTP
```
Admin clicks "Send OTP" button
    ↓
Backend generates random 6-digit OTP code
    ↓
OTP is stored in database with 1-hour expiration
    ↓
Email service sends OTP to user's email address
    ↓
User receives email with OTP code
```

### Step 5: User Signs In
```
User navigates to sign-in page
    ↓
User enters email address
    ↓
User clicks "Send Code"
    ↓
Backend sends OTP to email
    ↓
User receives OTP in email inbox
    ↓
User enters OTP on sign-in page
    ↓
Backend verifies OTP (must be correct and not expired)
    ↓
JWT token issued to user (authenticated)
    ↓
User is now logged in
```

### Step 6: User Taps Card to Activate
```
User taps their card at access point
    ↓
Backend receives /api/v1/hardware/card-scan
    ↓
CardService.scan() checks if this is an unregistered card
    ↓
If OTP verified → CardService.activateCardAfterOtp()
    ↓
Creates MemberCard entry linking card to user
    ↓
Assigns stored role to user account
    ↓
UnregisteredCard status changes to "ACTIVATED"
    ↓
User now has full access with assigned dashboard
```

### Step 7: User Sees Role-Based Dashboard
```
After first card tap:
    ↓
If Role = MEMBER:
  → User sees: Member Dashboard
  → Access: Check-ins, Reservations, Training
    ↓
If Role = STAFF:
  → User sees: Staff Dashboard  
  → Access: Equipment management, Check-ins, Staff tools
    ↓
If Role = ADMIN:
  → User sees: Admin Dashboard
  → Access: All features, Settings, User management, Reports
```

## API Endpoints

### List Unregistered Cards
```
GET /api/v1/hardware/unregistered-cards
Authorization: Required (MANAGE_EQUIPMENT permission)

Response:
[
  {
    "id": 1,
    "cardUid": "ABC123DEF456",
    "status": "ASSIGNED",
    "assignedToEmail": "user@example.com",
    "assignedAt": "2026-10-25T13:20:00Z",
    "assignedRole": "ADMIN",
    "roleAssignedAt": "2026-10-25T13:21:00Z",
    "otpGenerated": true,
    "otpVerified": false,
    "deviceId": "reader-1",
    "scanCount": 2,
    "firstScannedAt": "2026-10-25T13:15:00Z",
    "lastScannedAt": "2026-10-25T13:25:00Z"
  }
]
```

### Assign Card to Email
```
POST /api/v1/hardware/unregistered-cards/{id}/assign
Authorization: Required (MANAGE_EQUIPMENT permission)
Content-Type: application/json

Request Body:
{
  "email": "user@example.com"
}

Response: Updated UnregisteredCard object with status="ASSIGNED"
```

### Assign Role to Card
```
POST /api/v1/hardware/unregistered-cards/{id}/assign-role
Authorization: Required (MANAGE_EQUIPMENT permission)
Content-Type: application/json

Request Body:
{
  "role": "ADMIN"  // Valid values: MEMBER, STAFF, ADMIN
}

Response: Updated UnregisteredCard object with assignedRole set
```

### Send OTP to Email
```
POST /api/v1/hardware/unregistered-cards/{id}/send-otp
Authorization: Required (MANAGE_EQUIPMENT permission)
Content-Type: application/json

Response: Updated UnregisteredCard object with OTP details
Success: 6-digit OTP sent to assignedToEmail
```

## Database Schema

### unregistered_cards Table

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| id | BIGSERIAL PRIMARY KEY | NO | Auto-increment ID |
| card_uid | VARCHAR(32) UNIQUE | NO | NFC card unique identifier |
| status | VARCHAR(50) | NO | PENDING, ASSIGNED, VERIFIED, ACTIVATED, REJECTED |
| assigned_to_email | VARCHAR(255) | YES | User's email address |
| assigned_at | TIMESTAMP | YES | When card was assigned to email |
| assigned_role | VARCHAR(50) | YES | User's role: MEMBER, STAFF, ADMIN |
| role_assigned_at | TIMESTAMP | YES | When role was assigned |
| otp_code | VARCHAR(10) | YES | 6-digit OTP code |
| otp_generated_at | TIMESTAMP | YES | When OTP was generated |
| otp_expires_at | TIMESTAMP | YES | When OTP expires (1 hour after generation) |
| otp_verified | BOOLEAN | NO DEFAULT false | Whether OTP was verified |
| verified_at | TIMESTAMP | YES | When OTP was verified |
| device_id | VARCHAR(100) | YES | Card reader device identifier |
| scan_count | INTEGER | YES | Number of times card has been tapped |
| first_scanned_at | TIMESTAMP | NO | First tap timestamp |
| last_scanned_at | TIMESTAMP | NO | Most recent tap timestamp |

## Frontend UI

### New Cards Table

Shows all unregistered cards with the following columns:

| Column | Description |
|--------|-------------|
| Card UID | Unique identifier of the card |
| Status | PENDING → ASSIGNED → VERIFIED → ACTIVATED |
| Assigned Email | Email address the card is assigned to |
| **Assigned Role** | User's role (MEMBER/STAFF/ADMIN) |
| First Tap | Timestamp of first card scan |
| Last Tap | Timestamp of most recent scan |
| Taps | Number of times card has been tapped |
| Device | Card reader that scanned it |
| Actions | Multi-step workflow buttons |

### Action Workflow

**Step 1: Assign Email**
- Button appears when status is PENDING
- Opens email input field
- User enters email address
- Confirms to assign

**Step 2: Assign Role** (NEW)
- Button appears when email is assigned but role is not
- Opens dropdown with three options:
  - Regular Member (basic access)
  - Staff (equipment & training)
  - Admin (full system access)
- Confirms to assign role

**Step 3: Send OTP**
- Button appears when both email and role are assigned
- Sends OTP to email address
- Shows confirmation message

## Status Colors

| Status | Color | Meaning |
|--------|-------|---------|
| PENDING | Gray | New card, waiting for email assignment |
| ASSIGNED | Yellow | Email assigned, waiting for role & OTP |
| VERIFIED | Green | OTP verified, ready for card activation |
| ACTIVATED | Blue | Card activated, user has access |
| REJECTED | Red | Card rejected/deleted by admin |

## Role Permissions

### MEMBER
- Access check-in system
- View reservations
- Complete training courses
- Use assigned equipment during check-in

### STAFF
- All MEMBER permissions
- Equipment management
- Training administration
- Check-in oversight

### ADMIN
- Full system access
- User management
- Settings configuration
- Reports and analytics
- System administration

## Security Features

✅ **Authentication Required**
- All endpoints require admin authentication
- MANAGE_EQUIPMENT permission required

✅ **Email Verification**
- User must verify ownership of email via OTP
- OTP is 6 digits, valid for 1 hour only

✅ **User Verification**
- User must be signed in to activate card
- Card tap confirms user identity

✅ **Role-Based Access**
- Role assigned before user access
- Role determines accessible features
- Role can be changed by admin later

✅ **Audit Trail**
- All actions timestamped
- Scan history tracked
- Role assignment tracked

## Testing

### Manual Test Flow

1. **Generate Test Card Scan**
```bash
curl -X POST http://localhost:8080/api/v1/hardware/card-scan \
  -H "Content-Type: application/json" \
  -d '{"cardUid":"TEST123456","deviceId":"test-reader"}'
```

2. **Admin Assigns Email**
```bash
curl -X POST http://localhost:8080/api/v1/hardware/unregistered-cards/1/assign \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"email":"newuser@example.com"}'
```

3. **Admin Assigns Role**
```bash
curl -X POST http://localhost:8080/api/v1/hardware/unregistered-cards/1/assign-role \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"role":"ADMIN"}'
```

4. **Send OTP**
```bash
curl -X POST http://localhost:8080/api/v1/hardware/unregistered-cards/1/send-otp \
  -H "Authorization: Bearer <token>"
```

5. **Check Status**
```bash
curl http://localhost:8080/api/v1/hardware/unregistered-cards \
  -H "Authorization: Bearer <token>"
```

## Files Modified

### Backend
- `UnregisteredCard.java` - Added assignedRole, roleAssignedAt fields
- `V18__add_role_to_unregistered_cards.sql` - Database migration
- `CardService.java` - Added assignRole() method, updated activateCardAfterOtp()
- `UnregisteredCardController.java` - Added /assign-role endpoint

### Frontend
- `UnregisteredCardsManager.jsx` - Added role column, role assignment UI, workflow updates

## Next Steps

1. ✅ Role assignment during card registration
2. ⏳ Verify role-based dashboard access after sign-in
3. ⏳ Test complete end-to-end flow with card reader
4. ⏳ Admin ability to change assigned role after activation
5. ⏳ User management interface to view/edit user roles
6. ⏳ Audit logging for all role assignments

## Troubleshooting

### "Invalid role" Error
- Ensure role is one of: MEMBER, STAFF, ADMIN
- Case-insensitive (ADMIN, admin, Admin all work)

### OTP Not Sending
- Verify email service is running
- Check Redis is accessible
- Review email service logs

### Role Not Applied
- Verify role was assigned before OTP verification
- Check user account after card activation
- Review database for assignedRole value

## Deployment Notes

1. Run database migration V18 before deploying
2. Restart backend service after code deployment
3. Frontend updates take effect after browser refresh
4. Existing unregistered cards remain unaffected (assignedRole will be NULL)
5. Admins can assign roles to existing cards anytime

---

**Last Updated:** 2026-10-25  
**Version:** 2.0 (with role assignment)  
**Status:** ✅ Ready for testing
