# Hardware Check-In API & Admin Dashboard

## Overview

The Hardware Check-In system allows Raspberry Pi devices to register user access attempts to equipment, with real-time status display in the admin dashboard and waiver management capabilities.

## Backend Architecture

### Models

#### HardwareCheckIn (`HardwareCheckIn.java`)
- **Fields:**
  - `id`: Unique identifier
  - `user`: User attempting access
  - `equipment`: Equipment being accessed
  - `status`: Check-in status (PENDING, SUCCESS, FAILED, NEEDS_WAIVER, WAIVER_SIGNED, WAIVER_APPROVED, DENIED)
  - `checkinTime`: Timestamp of check-in attempt
  - `accessGranted`: Boolean indicating if access was allowed
  - `errorMessage`: Reason for denial
  - `deviceId`: Raspberry Pi device identifier
  - `waiverSigned`: Whether user has signed waiver
  - `waiverApprovedByAdmin`: Whether admin has approved signed waiver
  - `approvedByAdmin`: User who approved waiver
  - `approvalTime`: When approval occurred

### API Endpoints

#### 1. **POST /api/v1/hardware/checkin**
Raspberry Pi device calls this endpoint to register a user check-in.

**Request Body:**
```json
{
  "userId": 1,
  "equipmentId": 5,
  "deviceId": "rpi-laser-01"
}
```

**Response (Success):**
```json
{
  "id": 42,
  "userId": 1,
  "userName": "John Doe",
  "userEmail": "john@example.com",
  "equipmentId": 5,
  "equipmentName": "Laser Cutter",
  "status": "SUCCESS",
  "checkinTime": "2026-10-19T20:43:00Z",
  "accessGranted": true,
  "errorMessage": null,
  "deviceId": "rpi-laser-01",
  "waiverSigned": false,
  "waiverApprovedByAdmin": false
}
```

**Response (Needs Waiver):**
```json
{
  "status": "NEEDS_WAIVER",
  "accessGranted": false,
  "errorMessage": null
}
```

#### 2. **GET /api/v1/hardware/checkins/recent**
Get recent hardware check-ins (admin only).

**Query Parameters:**
- `page`: Page number (default: 0)
- `size`: Items per page (default: 20)

**Authentication:** Requires `MANAGE_EQUIPMENT` permission

#### 3. **GET /api/v1/hardware/checkins/pending-waivers**
Get all pending waivers awaiting admin approval.

**Query Parameters:**
- `page`: Page number (default: 0)
- `size`: Items per page (default: 20)

**Authentication:** Requires `MANAGE_EQUIPMENT` permission

#### 4. **GET /api/v1/hardware/checkins/user/{userId}**
Get check-ins for a specific user.

**Authentication:** User can view own, admin can view any

#### 5. **POST /api/v1/hardware/checkins/{checkInId}/sign-waiver**
User signs waiver using rabbit signature pad.

**Request Body:**
```json
{
  "signatureData": "data:image/png;base64,iVBORw0KGgoAAAANS..."
}
```

**Response:** Updated HardwareCheckInDTO with `waiverSigned: true`

#### 6. **POST /api/v1/hardware/checkins/{checkInId}/approve-waiver**
Admin approves a signed waiver.

**Authentication:** Requires `MANAGE_EQUIPMENT` permission

**Response:** Updated HardwareCheckInDTO with `waiverApprovedByAdmin: true`

#### 7. **POST /api/v1/hardware/checkins/{checkInId}/deny**
Admin denies access for a check-in.

**Request Body:**
```json
{
  "reason": "Equipment maintenance required"
}
```

**Response:** Updated HardwareCheckInDTO with `status: DENIED`

## Frontend Components

### HardwareCheckIns Component
Display table of hardware check-ins with filtering and admin actions.

**Features:**
- Real-time status display with color coding
- Filter by "All Check-ins" or "Pending Waivers"
- Inline approve/deny buttons for unsigned waivers
- Pagination support
- Responsive design

**Location:** `/frontend/src/components/admin-dashboard/HardwareCheckIns.jsx`

**Usage:**
```jsx
import HardwareCheckIns from "./components/admin-dashboard/HardwareCheckIns";

<Route path="hardware" element={<HardwareCheckIns />} />
```

### RabbitSignaturePad Component
Canvas-based signature pad for waiver signing.

**Features:**
- Mouse and touch support
- Smooth line drawing with configurable line width
- Clear and submit functionality
- Saves as PNG data URL
- Responsive to different screen sizes

**Location:** `/frontend/src/components/admin-dashboard/RabbitSignaturePad.jsx`

**Props:**
```jsx
<RabbitSignaturePad
  checkInId={id}
  onSignatureSaved={handleSaved}
  onCancel={handleCancel}
/>
```

## Status Flow

```
User taps Raspberry Pi device
         ↓
POST /api/v1/hardware/checkin
         ↓
   ┌─────┴─────┐
   ↓           ↓
Has MEMBER   No Permission
Role?        Role?
   ↓           ↓
   ├→ Needs    └→ FAILED
     Waiver?       (accessGranted: false)
   ↓   ↓
Yes  No
 ↓    ↓
 N    S
 E    U
 E    C
 D    C
 S    E
 _    S
 W    S
 A
 I
 V
 E
 R
   ↓           ↓
   ↓      SUCCESS
   └→ Redirect to signature pad
        ↓
    User signs (Rabbit Signature)
        ↓
    WAIVER_SIGNED (waiting for admin)
        ↓
    Admin reviews in Hardware Access page
        ↓
    ┌─────────┬──────────┐
    ↓         ↓
  Approve    Deny
    ↓         ↓
 WAIVER_    DENIED
 APPROVED   (accessGranted: false)
    ↓
  Grant Access
```

## Integration with Raspberry Pi

### Hardware Device Communication

**Device must send HTTP POST request:**
```bash
curl -X POST http://server:8080/api/v1/hardware/checkin \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "equipmentId": 5,
    "deviceId": "rpi-laser-01"
  }'
```

**Device should:**
1. Read user ID from RFID/NFC card
2. Send check-in request
3. Display response status on local display
4. Grant/deny access based on `accessGranted` field
5. If status is `NEEDS_WAIVER`, redirect to tablet for signature

## Admin Workflow

1. **Navigate to:** `/admin-dashboard/hardware`
2. **View Tabs:**
   - **All Check-ins:** See all recent hardware access attempts
   - **Pending Waivers:** See waivers awaiting approval (counted badge)
3. **For each pending waiver:**
   - Review user information
   - See signature status
   - Click **Approve** to grant access
   - Click **Deny** to reject and provide reason
4. **Approved waivers:** User gets equipment access on next check-in

## Database Schema

### hardware_checkins Table
```sql
CREATE TABLE hardware_checkins (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  equipment_id BIGINT NOT NULL REFERENCES equipment(id),
  status VARCHAR(50) NOT NULL,
  checkin_time TIMESTAMP NOT NULL,
  access_granted BOOLEAN,
  error_message TEXT,
  device_id VARCHAR(100),
  waiver_signed BOOLEAN DEFAULT false,
  waiver_approved_by_admin BOOLEAN DEFAULT false,
  approved_by_user_id BIGINT REFERENCES users(id),
  approval_time TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Configuration

### Add to Equipment Model
```java
@Column(name = "requires_waiver")
private Boolean requiresWaiver;
```

### Environment Variables
None required for basic setup. All configuration is in application properties.

## Security Considerations

1. **Access Control:**
   - Check-in endpoint is PUBLIC (device can call without auth)
   - Admin endpoints require `MANAGE_EQUIPMENT` permission
   - Users can only view own check-ins

2. **Signature Storage:**
   - Signature data stored as base64-encoded PNG
   - Should be archived separately for compliance
   - Consider external storage (S3, etc.)

3. **Device Authentication:**
   - Consider adding device API key validation
   - Rate limiting per device ID recommended
   - Log all check-in attempts

## Testing

### Simulate Hardware Check-In
```bash
# User with MEMBER role attempting access
curl -X POST http://localhost:8080/api/v1/hardware/checkin \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "equipmentId": 1,
    "deviceId": "rpi-test-01"
  }'

# Get pending waivers
curl -X GET "http://localhost:8080/api/v1/hardware/checkins/pending-waivers" \
  -H "Authorization: Bearer $TOKEN"

# Sign waiver
curl -X POST http://localhost:8080/api/v1/hardware/checkins/1/sign-waiver \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"signatureData": "data:image/png;base64,..."}'

# Approve waiver
curl -X POST http://localhost:8080/api/v1/hardware/checkins/1/approve-waiver \
  -H "Authorization: Bearer $TOKEN"
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| User sees "Equipment not found" | Verify equipment exists in database |
| User sees "Waiver required" | Create equipment with `requires_waiver=true` or approve pending waiver |
| Signature not saving | Verify S3/file storage is configured |
| Admin can't approve waivers | Verify user has `MANAGE_EQUIPMENT` permission |
| Device communication fails | Check network connectivity, device IP allowlist |

## Future Enhancements

1. **Signature Storage:** Integrate with external storage (S3)
2. **Audit Logging:** Track all approval/denial actions
3. **Batch Approvals:** Admin can approve multiple waivers at once
4. **Email Notifications:** Notify users of approval/denial
5. **Device Dashboard:** Show real-time check-in stats on device
6. **QR Code Generation:** Generate QR codes for equipment quick access

## Card scans (Raspberry Pi)

Each card UID is linked to a member in the admin **Hardware → Cards** tab (or via **Assign card** on an
unknown tap in **Card taps**). The Pi only sends the UID; membership, training, waiver and permissions are
evaluated live on every scan, so staff changes apply without rescanning.

```bash
curl -X POST http://localhost:8080/api/v1/hardware/card-scan \
  -H 'Content-Type: application/json' \
  -H 'X-Device-Key: <app.hardware.device-key, if configured>' \
  -d '{"cardUid":"04:A3:2B:1C","deviceId":"pi-front-door","equipmentId":3}'
# {"approved":true,"status":"APPROVED","message":"Access granted","memberName":"Zhijun Liang"}
# {"approved":false,"status":"DENIED","message":"Card not registered","memberName":null}
```

`equipmentId` is optional (omit for door access). `X-Device-Key` is only enforced when
`app.hardware.device-key` is set. Staff endpoints (`MANAGE_EQUIPMENT`): `GET/POST /hardware/cards`,
`PATCH/DELETE /hardware/cards/{id}`, `GET /hardware/card-scans/recent`, `GET /hardware/cards/members?q=`.

Waivers are now signed by typing the member's name (`signedName`), and a PDF copy is emailed to the member.
7. **API Key Management:** Device authentication via API keys
8. **Rate Limiting:** Prevent abuse of check-in endpoint
