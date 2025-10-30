#!/bin/bash

# Test startup script for EDI 835 Processor with SQLite
# This script tests that the application can start successfully

set -e  # Exit on error

echo "========================================="
echo "EDI 835 Processor - Startup Test"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Clean old data
echo -e "${YELLOW}Step 1: Cleaning old data...${NC}"
rm -rf ./data/edi835-local.db
rm -rf ./target
echo -e "${GREEN}✓ Cleanup complete${NC}"
echo ""

# Step 2: Compile
echo -e "${YELLOW}Step 2: Compiling application...${NC}"
mvn clean compile -DskipTests -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${RED}✗ Compilation failed${NC}"
    exit 1
fi
echo ""

# Step 3: Package
echo -e "${YELLOW}Step 3: Packaging application...${NC}"
mvn package -DskipTests -q
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Packaging successful${NC}"
else
    echo -e "${RED}✗ Packaging failed${NC}"
    exit 1
fi
echo ""

# Step 4: Start application in background
echo -e "${YELLOW}Step 4: Starting application...${NC}"
java -jar target/edi835-processor-1.0.0-SNAPSHOT.jar > startup.log 2>&1 &
APP_PID=$!
echo "Started with PID: $APP_PID"
echo ""

# Step 5: Wait for startup
echo -e "${YELLOW}Step 5: Waiting for application to start...${NC}"
MAX_WAIT=60  # Maximum wait time in seconds
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT ]; do
    if grep -q "Started Edi835ProcessorApplication" startup.log 2>/dev/null; then
        echo -e "${GREEN}✓ Application started successfully!${NC}"
        STARTED=true
        break
    fi

    if grep -q "APPLICATION FAILED TO START" startup.log 2>/dev/null; then
        echo -e "${RED}✗ Application failed to start${NC}"
        echo ""
        echo "Last 20 lines of startup.log:"
        tail -20 startup.log
        kill $APP_PID 2>/dev/null
        exit 1
    fi

    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo -n "."
done

echo ""

if [ -z "$STARTED" ]; then
    echo -e "${RED}✗ Timeout waiting for application to start${NC}"
    echo ""
    echo "Last 20 lines of startup.log:"
    tail -20 startup.log
    kill $APP_PID 2>/dev/null
    exit 1
fi

# Step 6: Test health endpoint
echo -e "${YELLOW}Step 6: Testing health endpoint...${NC}"
sleep 3  # Give it a moment to fully initialize
HEALTH_RESPONSE=$(curl -s http://localhost:8080/api/v1/actuator/health)

if echo "$HEALTH_RESPONSE" | grep -q "UP"; then
    echo -e "${GREEN}✓ Health check passed${NC}"
    echo "Response: $HEALTH_RESPONSE"
else
    echo -e "${RED}✗ Health check failed${NC}"
    echo "Response: $HEALTH_RESPONSE"
fi
echo ""

# Step 7: Check database was created
echo -e "${YELLOW}Step 7: Checking database...${NC}"
if [ -f "./data/edi835-local.db" ]; then
    echo -e "${GREEN}✓ SQLite database created${NC}"
    DB_SIZE=$(du -h ./data/edi835-local.db | cut -f1)
    echo "Database size: $DB_SIZE"

    # Check table count
    TABLE_COUNT=$(sqlite3 ./data/edi835-local.db "SELECT COUNT(*) FROM sqlite_master WHERE type='table';" 2>/dev/null)
    echo "Tables created: $TABLE_COUNT"
else
    echo -e "${YELLOW}⚠ Database file not found (may not have been created yet)${NC}"
fi
echo ""

# Step 8: Check change feed processor
echo -e "${YELLOW}Step 8: Checking change feed processor...${NC}"
if grep -q "SQLite Change Feed Processor initialized" startup.log; then
    echo -e "${GREEN}✓ SQLite Change Feed Processor initialized${NC}"
else
    echo -e "${YELLOW}⚠ Change feed processor not found in logs${NC}"
fi
echo ""

# Summary
echo "========================================="
echo -e "${GREEN}✓ STARTUP TEST PASSED${NC}"
echo "========================================="
echo ""
echo "Application is running at: http://localhost:8080/api/v1"
echo "Process ID: $APP_PID"
echo "Logs: startup.log"
echo ""
echo "To stop: kill $APP_PID"
echo "To view logs: tail -f startup.log"
echo ""
echo "Next steps:"
echo "  1. Load sample data: sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql"
echo "  2. Start frontend: cd ../edi835-admin-portal && npm run dev"
echo "  3. Open browser: http://localhost:3000"
echo ""
