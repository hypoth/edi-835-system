#!/bin/bash

# NCPDP D.0 Claim Ingestion Script
# This script ingests pharmacy claims from NCPDP D.0 files into the edi835-processor system
# The claims are inserted into the ncpdp_raw_claims table and automatically processed by the change feed

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DEFAULT_FILE="d0-samples/ncpdp_rx_claims.txt"
API_URL="${API_URL:-http://localhost:8080/api/v1/ncpdp}"
STOP_ON_ERROR=false

# Function to display usage
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -f, --file FILE          Path to NCPDP D.0 claims file (default: $DEFAULT_FILE)"
    echo "  -d, --default            Use default file path from application.yml"
    echo "  -s, --stop-on-error      Stop processing on first error"
    echo "  -u, --url URL            API base URL (default: $API_URL)"
    echo "  -h, --help               Display this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Ingest from default file"
    echo "  $0 -f custom-file.txt                 # Ingest from custom file"
    echo "  $0 -f claims.txt -s                   # Stop on first error"
    echo "  $0 -u http://prod:8080/api/v1/ncpdp  # Use production API"
    echo ""
}

# Parse command line arguments
FILE_PATH=""
USE_DEFAULT=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--file)
            FILE_PATH="$2"
            shift 2
            ;;
        -d|--default)
            USE_DEFAULT=true
            shift
            ;;
        -s|--stop-on-error)
            STOP_ON_ERROR=true
            shift
            ;;
        -u|--url)
            API_URL="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown option $1${NC}"
            usage
            exit 1
            ;;
    esac
done

# If no file specified and not using default, use DEFAULT_FILE
if [ -z "$FILE_PATH" ] && [ "$USE_DEFAULT" = false ]; then
    FILE_PATH="$DEFAULT_FILE"
fi

echo "========================================="
echo "NCPDP D.0 Claim Ingestion"
echo "========================================="
echo ""

# Step 1: Check API availability
echo -e "${YELLOW}Step 1: Checking API availability...${NC}"
# Extract base URL (protocol://host:port) from API_URL
BASE_URL=$(echo "$API_URL" | grep -oP 'https?://[^/]+')
HEALTH_URL="${BASE_URL}/actuator/health"

if curl -s -f "$HEALTH_URL" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ API is available at $BASE_URL${NC}"
else
    echo -e "${RED}✗ API is not available at $BASE_URL${NC}"
    echo "Please ensure the edi835-processor is running"
    echo "Start with: mvn spring-boot:run"
    echo "Health check endpoint: $HEALTH_URL"
    exit 1
fi
echo ""

# Step 2: Get current status before ingestion
echo -e "${YELLOW}Step 2: Getting current status...${NC}"
STATUS_BEFORE=$(curl -s "$API_URL/status")
PENDING_BEFORE=$(echo "$STATUS_BEFORE" | grep -o '"pending":[0-9]*' | grep -o '[0-9]*')
PROCESSED_BEFORE=$(echo "$STATUS_BEFORE" | grep -o '"processed":[0-9]*' | grep -o '[0-9]*')
FAILED_BEFORE=$(echo "$STATUS_BEFORE" | grep -o '"failed":[0-9]*' | grep -o '[0-9]*')

echo "Current status:"
echo "  Pending:   $PENDING_BEFORE"
echo "  Processed: $PROCESSED_BEFORE"
echo "  Failed:    $FAILED_BEFORE"
echo ""

# Step 3: Ingest claims
if [ "$USE_DEFAULT" = true ]; then
    echo -e "${YELLOW}Step 3: Ingesting from default file...${NC}"
    RESPONSE=$(curl -s -X POST "$API_URL/ingest/default" -H "Content-Type: application/json")
else
    echo -e "${YELLOW}Step 3: Ingesting from file: $FILE_PATH${NC}"

    # Check if file exists
    if [ ! -f "$FILE_PATH" ]; then
        echo -e "${RED}✗ File not found: $FILE_PATH${NC}"
        exit 1
    fi

    # Get absolute path
    ABS_PATH=$(realpath "$FILE_PATH")
    echo "Absolute path: $ABS_PATH"
    echo ""

    # Create JSON payload
    PAYLOAD=$(cat <<EOF
{
  "filePath": "$ABS_PATH",
  "stopOnError": $STOP_ON_ERROR
}
EOF
)

    # Call API
    RESPONSE=$(curl -s -X POST "$API_URL/ingest" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD")
fi

# Parse response
STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
TOTAL_PROCESSED=$(echo "$RESPONSE" | grep -o '"totalProcessed":[0-9]*' | grep -o '[0-9]*')
TOTAL_SUCCESS=$(echo "$RESPONSE" | grep -o '"totalSuccess":[0-9]*' | grep -o '[0-9]*')
TOTAL_FAILED=$(echo "$RESPONSE" | grep -o '"totalFailed":[0-9]*' | grep -o '[0-9]*')

if [ "$STATUS" = "SUCCESS" ] || [ "$STATUS" = "PARTIAL" ]; then
    echo -e "${GREEN}✓ Ingestion completed: $STATUS${NC}"
    echo ""
    echo "Ingestion Results:"
    echo "  Total Processed: $TOTAL_PROCESSED"
    echo "  Successful:      $TOTAL_SUCCESS"
    echo "  Failed:          $TOTAL_FAILED"

    # Show errors if any
    if [ "$TOTAL_FAILED" -gt 0 ]; then
        echo ""
        echo -e "${YELLOW}Errors:${NC}"
        echo "$RESPONSE" | grep -o '"errors":\[[^]]*\]' | sed 's/\\n/\n/g'
    fi
else
    echo -e "${RED}✗ Ingestion failed${NC}"
    echo "Response: $RESPONSE"
    exit 1
fi
echo ""

# Step 4: Wait for change feed processing
echo -e "${YELLOW}Step 4: Waiting for change feed processing...${NC}"
echo "Monitoring status (updates every 2 seconds, max 60 seconds)..."
echo ""

MAX_WAIT=60
ELAPSED=0
PREV_PROCESSED=$PROCESSED_BEFORE

while [ $ELAPSED -lt $MAX_WAIT ]; do
    sleep 2
    ELAPSED=$((ELAPSED + 2))

    # Get current status
    CURRENT_STATUS=$(curl -s "$API_URL/status")
    PENDING=$(echo "$CURRENT_STATUS" | grep -o '"pending":[0-9]*' | grep -o '[0-9]*')
    PROCESSING=$(echo "$CURRENT_STATUS" | grep -o '"processing":[0-9]*' | grep -o '[0-9]*')
    PROCESSED=$(echo "$CURRENT_STATUS" | grep -o '"processed":[0-9]*' | grep -o '[0-9]*')
    FAILED=$(echo "$CURRENT_STATUS" | grep -o '"failed":[0-9]*' | grep -o '[0-9]*')

    # Calculate deltas
    PROCESSED_DELTA=$((PROCESSED - PREV_PROCESSED))

    # Display progress
    echo -ne "\r  Pending: $PENDING | Processing: $PROCESSING | Processed: +$PROCESSED_DELTA | Failed: $FAILED | Elapsed: ${ELAPSED}s"

    # Check if all claims processed
    if [ "$PENDING" -eq 0 ] && [ "$PROCESSING" -eq 0 ]; then
        echo ""
        echo ""
        echo -e "${GREEN}✓ All claims processed!${NC}"
        break
    fi

    PREV_PROCESSED=$PROCESSED
done

echo ""
echo ""

# Step 5: Final status
echo -e "${YELLOW}Step 5: Final status summary...${NC}"
FINAL_STATUS=$(curl -s "$API_URL/status")
PENDING_FINAL=$(echo "$FINAL_STATUS" | grep -o '"pending":[0-9]*' | grep -o '[0-9]*')
PROCESSING_FINAL=$(echo "$FINAL_STATUS" | grep -o '"processing":[0-9]*' | grep -o '[0-9]*')
PROCESSED_FINAL=$(echo "$FINAL_STATUS" | grep -o '"processed":[0-9]*' | grep -o '[0-9]*')
FAILED_FINAL=$(echo "$FINAL_STATUS" | grep -o '"failed":[0-9]*' | grep -o '[0-9]*')
TOTAL_FINAL=$(echo "$FINAL_STATUS" | grep -o '"total":[0-9]*' | grep -o '[0-9]*')
SUCCESS_RATE=$(echo "$FINAL_STATUS" | grep -o '"successRate":[0-9.]*' | grep -o '[0-9.]*')

echo "Final Status:"
echo "  Total:        $TOTAL_FINAL"
echo "  Pending:      $PENDING_FINAL"
echo "  Processing:   $PROCESSING_FINAL"
echo "  Processed:    $PROCESSED_FINAL (from $PROCESSED_BEFORE)"
echo "  Failed:       $FAILED_FINAL (from $FAILED_BEFORE)"
echo "  Success Rate: ${SUCCESS_RATE}%"
echo ""

# Summary
if [ "$PENDING_FINAL" -eq 0 ] && [ "$PROCESSING_FINAL" -eq 0 ]; then
    echo "========================================="
    echo -e "${GREEN}✓ INGESTION COMPLETE${NC}"
    echo "========================================="
    echo ""
    echo "Next steps:"
    echo "  1. View dashboard: http://localhost:8080/api/v1/dashboard/summary"
    echo "  2. Check active buckets: http://localhost:8080/api/v1/dashboard/buckets/metrics"
    echo "  3. View failed claims: curl $API_URL/claims/failed"
    echo ""
else
    echo "========================================="
    echo -e "${YELLOW}⚠ INGESTION PARTIALLY COMPLETE${NC}"
    echo "========================================="
    echo ""
    echo "There are still claims being processed or pending"
    echo "  View status: curl $API_URL/status"
    echo "  View pending: curl $API_URL/claims/pending"
    echo "  View processing: curl $API_URL/claims/processing"
    echo ""
fi

# Show failed claims if any
if [ "$FAILED_FINAL" -gt "$FAILED_BEFORE" ]; then
    echo -e "${RED}⚠ New failed claims detected${NC}"
    echo "To view failed claims: curl $API_URL/claims/failed"
    echo ""
fi
