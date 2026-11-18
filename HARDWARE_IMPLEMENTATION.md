# Hardware Check-In & Admin Dashboard Implementation

## Summary

This implementation adds a complete hardware access control system with:

1. **Backend API** - Raspberry Pi devices can register user check-in attempts
2. **Admin Dashboard** - Real-time display of hardware access logs
3. **Waiver Management** - Users sign waivers with digital signatures
4. **Access Control** - Admin approves/denies equipment access

## Components Added

### Backend (Java/Spring)

#### Models
- **`HardwareCheckIn.java`** - JPA entity for check-in records
  - Tracks user, equipment, timestamp, status
  - Manages waiver signing and admin approval
  - Status enum: PENDING, SUCCESS, FAILED, NEEDS_WAIVER, WAIVER_SIGNED, WAIVER_APPROVED, DENIED

#### Repository
- **`HardwareCheckInRepository.java`** - JPA repository with custom queries
  - Find by user, equipment, status
  - Find pending waivers
  - Pagination support

#### Service
- **`HardwareCheckInService.java`** - Business logic
  - `checkIn()` - Process hardware access attempt
  - `approveWaiver()` - Admin approves signed waiver
  - `denyAccess()` - Admin denies access
  - `signWaiver()` - User signs with rabbit signature
  - `getPendingWaivers()` - Get waivers awaiting approval

#### Controller
- **`HardwareCheckInController.java`** - REST API endpoints
  - `POST /api/v1/hardware/checkin` - Raspberry Pi calls this
  - `GET /api/v1/hardware/checkins/recent` - Recent check-ins
  - `GET /api/v1/hardware/checkins/pending-waivers` - Pending waivers
  - `POST /api/v1/hardware/checkins/{id}/sign-waiver` - User signs
  - `POST /api/v1/hardware/checkins/{id}/approve-waiver` - Admin approves
  - `POST /api/v1/hardware/checkins/{id}/deny` - Admin denies

#### DTO
- **`HardwareCheckInDTO.java`** - Data transfer object
  - Converts entity to JSON with user/equipment details
  - Safely exposes check-in information to frontend

#### Database Migration
- **`V12__hardware_checkins.sql`** - Flyway migration
  - Creates `hardware_checkins` table with indexes
  - Adds `requires_waiver` column to equipment
  - Optimized for common queries

### Frontend (React)

#### Components
- **`HardwareCheckIns.jsx`** - Admin dashboard page
  - Table view of all check-ins
  - Filter: "All Check-ins" vs "Pending Waivers"
  - Status color coding
  - Approve/deny buttons for waivers
  - Pagination
  - Real-time updates

- **`RabbitSignaturePad.jsx`** - Signature capture
  - Canvas-based drawing pad
  - Mouse and touch support
  - Clear and submit buttons
  - Converts to PNG base64
  - Responsive design

#### Styling
- **`HardwareCheckIns.css`** - Table styling with status colors
- **`RabbitSignaturePad.css`** - Signature pad styling

#### Integration
- Added route in `App.jsx`:
  - `<Route path="hardware" element={<HardwareCheckIns />} />`
- Added navigation in `AdminDashboard.jsx`:
  - "Hardware Access" menu item links to admin hardware page

## API Workflow

### 1. Raspberry Pi Check-In
```json
POST /api/v1/hardware/checkin
{
  "userId": 1,
  "equipmentId": 5,
  "deviceId": "rpi-laser-01"
}
```

Response (success):
```json
{
  "status": "SUCCESS",
  "accessGranted": true
}
```

Response (needs waiver):
```json
{
  "status": "NEEDS_WAIVER",
  "accessGranted": false
}
```

### 2. User Signs Waiver
```json
POST /api/v1/hardware/checkins/42/sign-waiver
{
  "signatureData": "data:image/png;base64,..."
}
```

### 3. Admin Approves Waiver
```json
POST /api/v1/hardware/checkins/42/approve-waiver
(no body)
```

## Status Codes

| Status | Meaning |
|--------|---------|
| `PENDING` | Just registered, awaiting processing |
| `SUCCESS` | User granted access |
| `FAILED` | User denied access (no permissions) |
| `NEEDS_WAIVER` | Equipment requires signed waiver |
| `WAIVER_SIGNED` | User signed waiver, awaiting admin approval |
| `WAIVER_APPROVED` | Admin approved waiver, access granted |
| `DENIED` | Admin explicitly denied access |

## Database Schema

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
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

## Setup Instructions

### 1. Backend Setup

No additional setup required. The Flyway migration will:
- Create `hardware_checkins` table on startup
- Add `requires_waiver` column to equipment table
- Create optimized indexes

### 2. Frontend Setup

The components are already integrated:
- `HardwareCheckIns` route available at `/admin-dashboard/hardware`
- Navigation link added to admin sidebar

### 3. Configuration

To require waivers for specific equipment:
```sql
UPDATE equipment SET requires_waiver = true WHERE id = 1;
```

### 4. Test the Flow

1. **Start all services:**
   ```bash
   # Backend
   cd backend
   export SPRING_PROFILES_ACTIVE=local
   mvn spring-boot:run

   # Frontend
   cd frontend
   npm run dev
   ```

2. **Simulate hardware check-in:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/hardware/checkin \
     -H "Content-Type: application/json" \
     -d '{
       "userId": 1,
       "equipmentId": 1,
       "deviceId": "rpi-test-01"
     }'
   ```

3. **Admin Dashboard:**
   - Login to `/admin-dashboard`
   - Click "Hardware Access" in sidebar
   - See check-in attempts
   - Filter to "Pending Waivers"
   - Review and approve/deny waivers

## File Locations

### Backend
```
backend/
├── src/main/java/com/makerspace/backend/
│   ├── model/
│   │   └── HardwareCheckIn.java
│   ├── repository/
│   │   └── HardwareCheckInRepository.java
│   ├── services/
│   │   └── HardwareCheckInService.java
│   └── controller/
│       ├── HardwareCheckInController.java
│       └── dto/
│           └── HardwareCheckInDTO.java
└── src/main/resources/db/migration/
    └── V12__hardware_checkins.sql
```

### Frontend
```
frontend/src/
├── components/admin-dashboard/
│   ├── HardwareCheckIns.jsx
│   ├── HardwareCheckIns.css
│   ├── RabbitSignaturePad.jsx
│   └── RabbitSignaturePad.css
├── pages/
│   ├── App.jsx (updated with route)
│   └── AdminDashboard.jsx (updated with navigation)
```

### Documentation
```
/HARDWARE_API.md - Complete API documentation
```

## Security Features

1. **Access Control:**
   - Check-in endpoint is public (device authentication via device ID)
   - Admin endpoints require `MANAGE_EQUIPMENT` permission
   - Users can only view own check-ins

2. **Signature Storage:**
   - Stored as base64-encoded PNG
   - Can be archived separately for compliance

3. **Audit Trail:**
   - All check-in attempts logged with timestamp
   - Admin approvals tracked with user and time
   - Error messages captured for troubleshooting

## Performance Optimizations

- **Indexed Queries:**
  - User ID, Equipment ID, Status, Device ID
  - Waiver signed/approved status
  - Check-in time (descending) for recent queries

- **Pagination:**
  - 20 items per page by default
  - Configurable via query parameters

- **Lazy Loading:**
  - User and Equipment relationships lazy-loaded
  - DTO only includes necessary fields

## Next Steps

1. **Raspberry Pi Integration:**
   - Configure device to POST to `/api/v1/hardware/checkin`
   - Display response on local screen
   - Grant/deny access based on `accessGranted` field

2. **Waiver Capture UI:**
   - Integrate signature pad on device/tablet
   - Call `POST /api/v1/hardware/checkins/{id}/sign-waiver`
   - Show approval status

3. **Email Notifications:**
   - Notify users when waiver approved/denied
   - Notify admins of pending waivers

4. **Enhanced Logging:**
   - Track who approved/denied each waiver
   - Generate compliance reports
   - Archive signatures separately

## Troubleshooting

### "User not found" error
- Verify user ID exists in database
- Check if user has been deleted

### "Equipment not found" error
- Verify equipment ID exists
- Check equipment hasn't been deleted

### "Waiver required" but user can't sign
- Equipment has `requires_waiver=true`
- User needs to access device/tablet for signature
- Signature must be submitted before access granted

### Admin can't see hardware page
- Verify user has `MANAGE_EQUIPMENT` permission
- Check admin role includes this permission
- Clear browser cache and reload

## Support

For issues or questions:
1. Check `/HARDWARE_API.md` for complete API documentation
2. Review backend logs for error messages
3. Check browser console for frontend errors
4. Verify database migration ran successfully

## Related Files

- `ADMIN_SETUP_COMPLETE.md` - Admin account setup
- `QUICK_START.md` - Quick reference
- `SETUP_COMPLETE.txt` - Setup summary
