#!/bin/bash

echo "🔍 Verifying Card-Based Sign-In System Setup"
echo "=============================================="
echo ""

# Check Backend
echo "1️⃣  Backend Status:"
if curl -s http://localhost:8080/health &> /dev/null; then
    echo "   ✅ Backend running on port 8080"
    curl -s http://localhost:8080/api/v1/auth/card/check-by-uid?uid=TEST123 | grep -q "Card not found" && \
    echo "   ✅ Card endpoint responding correctly" || \
    echo "   ⚠️  Card endpoint needs investigation"
else
    echo "   ❌ Backend NOT running - start with: cd backend && ./mvnw spring-boot:run"
fi
echo ""

# Check Frontend
echo "2️⃣  Frontend Status:"
if curl -s http://localhost:5173 &> /dev/null; then
    echo "   ✅ Frontend running on port 5173"
    curl -s http://localhost:5173 | grep -q "root" && \
    echo "   ✅ Frontend HTML loaded correctly" || \
    echo "   ⚠️  Frontend HTML needs investigation"
else
    echo "   ❌ Frontend NOT running - start with: cd frontend && npm run dev"
fi
echo ""

# Check Database
echo "3️⃣  Database Status:"
if psql -U postgres -d collaboratory -c "SELECT 1" &> /dev/null; then
    echo "   ✅ PostgreSQL connected"
    TABLE_COUNT=$(psql -U postgres -d collaboratory -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='unregistered_cards';" 2>/dev/null | tail -1)
    if [ "$TABLE_COUNT" -eq "1" ]; then
        echo "   ✅ UnregisteredCard table exists"
    else
        echo "   ⚠️  UnregisteredCard table not found"
    fi
else
    echo "   ❌ PostgreSQL NOT running - check database connection"
fi
echo ""

# Check Files
echo "4️⃣  Implementation Files:"
BACKEND_FILE="/Users/zhijunliang/Desktop/code/collaboratory-makerspace/backend/src/main/java/com/makerspace/backend/controller/CardAuthController.java"
FRONTEND_FILE="/Users/zhijunliang/Desktop/code/collaboratory-makerspace/frontend/src/components/CardSignInForm.jsx"

[ -f "$BACKEND_FILE" ] && echo "   ✅ CardAuthController.java exists" || echo "   ❌ CardAuthController.java missing"
[ -f "$FRONTEND_FILE" ] && echo "   ✅ CardSignInForm.jsx exists" || echo "   ❌ CardSignInForm.jsx missing"
echo ""

# Check Documentation
echo "5️⃣  Documentation:"
DOC_FILES=(
    "/Users/zhijunliang/Desktop/code/collaboratory-makerspace/COMPLETE_SUMMARY.md"
    "/Users/zhijunliang/Desktop/code/collaboratory-makerspace/QUICK_START_GUIDE.md"
    "/Users/zhijunliang/Desktop/code/collaboratory-makerspace/SYSTEM_STATUS.md"
)

for doc in "${DOC_FILES[@]}"; do
    if [ -f "$doc" ]; then
        echo "   ✅ $(basename $doc)"
    else
        echo "   ❌ $(basename $doc) missing"
    fi
done
echo ""

# Summary
echo "=============================================="
echo "📊 Summary:"
echo ""
if curl -s http://localhost:8080/health &> /dev/null && curl -s http://localhost:5173 &> /dev/null; then
    echo "   🎉 All systems operational!"
    echo "   ✅ Backend: port 8080"
    echo "   ✅ Frontend: port 5173"
    echo "   "
    echo "   Next: Open http://localhost:5173 and start testing"
else
    echo "   ⚠️  Some systems not running"
    echo "   📖 See QUICK_START_GUIDE.md for startup instructions"
fi
echo ""
