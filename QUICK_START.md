# 🚀 Quick Start - Admin Account

## One-Command Setup Summary

**Admin Email:** `jcs7350@g.rit.edu`  
**Admin OTP Code:** `123456`  
**Frontend URL:** http://localhost:5173  
**Backend URL:** http://localhost:8080  

## Start Backend (with OTP enabled)

```bash
cd ~/Desktop/code/collaboratory-makerspace/backend
export APP_OTP_DEV_CODE=123456
export SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

## Start Frontend  

```bash
cd ~/Desktop/code/collaboratory-makerspace/frontend
npm run dev
```

## Start Docker Services

```bash
cd ~/Desktop/code/collaboratory-makerspace/backend
docker-compose up -d
# Brings up: PostgreSQL (5434), Redis (6379), PgAdmin (5050)
```

## Login & Access Admin Dashboard

1. Open http://localhost:5173
2. Enter email: `jcs7350@g.rit.edu`
3. Click "Send Code"
4. Enter OTP: `123456`
5. Click "Continue"
6. ✅ Auto-redirects to admin dashboard!

## Verify Admin Permissions

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"jcs7350@g.rit.edu","code":"123456"}' | jq -r '.access_token')

# Check permissions (should include MANAGE_ROLES)
curl -s -X GET http://localhost:8080/api/v1/users/me/permissions \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## Key Configuration Files

- **Backend Config:** [application-local.yml](/backend/src/main/resources/application-local.yml) - DB & server config
- **Frontend Config:** [vite.config.js](/frontend/vite.config.js) - API proxy, build config
- **Database:** [docker-compose.yml](/backend/docker-compose.yml) - PostgreSQL & Redis setup
- **Admin Docs:** [ADMIN_SETUP_COMPLETE.md](/ADMIN_SETUP_COMPLETE.md) - Full technical details

## Common Issues

| Issue | Solution |
|-------|----------|
| "Could not send a code" | Start Redis: `docker-compose up -d` |
| Backend won't start | Set `SPRING_PROFILES_ACTIVE=local` and `APP_OTP_DEV_CODE=123456` |
| Can't connect to database | Verify PostgreSQL running: `docker ps \| grep postgres` |
| Regular dashboard instead of admin | Check permissions with curl command above |

---

**Full documentation:** See [ADMIN_SETUP_COMPLETE.md](ADMIN_SETUP_COMPLETE.md)
