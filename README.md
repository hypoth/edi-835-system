# EDI 835 Remittance Processing System

A comprehensive system for processing healthcare claims and generating EDI 835 remittance advice files with a web-based configuration portal.

## Project Structure

```
edi-835-system/
├── edi835-processor/          # Java Backend Service
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/healthcare/edi835/
│   │   │   │   ├── changefeed/
│   │   │   │   ├── config/
│   │   │   │   ├── model/
│   │   │   │   ├── repository/
│   │   │   │   ├── service/
│   │   │   │   ├── controller/
│   │   │   │   └── util/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── edi-schemas/
│   │   └── test/
│   └── pom.xml
│
├── edi835-admin-portal/       # React Frontend
│   ├── src/
│   │   ├── components/
│   │   │   ├── dashboard/
│   │   │   ├── bucketing/
│   │   │   ├── thresholds/
│   │   │   ├── filenaming/
│   │   │   ├── approvals/
│   │   │   └── common/
│   │   ├── services/
│   │   ├── types/
│   │   ├── store/
│   │   └── App.tsx
│   ├── package.json
│   └── tsconfig.json
│
└── database/
    └── schema.sql
```

## Technology Stack

### Backend (Java)
- **Java 17+**
- **Spring Boot 3.x**
- **Azure Cosmos DB** (Change Feed for streaming - Production)
- **SQLite** (Change Feed for local development/testing)
- **PostgreSQL** (Configuration and audit)
- **StAEDI** (EDI processing library)
- **Maven** (Build tool)

### Frontend (React)
- **React 18** with TypeScript
- **Material-UI** (UI components)
- **Redux Toolkit** (State management)
- **React Query** (Data fetching)
- **Formik & Yup** (Forms and validation)
- **Recharts** (Data visualization)

## Prerequisites

### Backend
- JDK 17 or higher
- Maven 3.8+
- **For Local Development**: SQLite (included automatically)
- **For Production**: Azure Cosmos DB account + PostgreSQL 14+

### Frontend
- Node.js 18+
- npm or yarn

## Quick Start with Makefiles

This project includes comprehensive Makefiles for easy development. See [MAKEFILE_USAGE.md](./MAKEFILE_USAGE.md) for detailed documentation.

### One-Command Setup

```bash
# Setup everything (install dependencies + start databases)
make setup

# Start development environment (both backend and frontend)
make dev
```

### Common Commands

```bash
make help              # Show all available commands
make install           # Install all dependencies
make build             # Build both projects
make test              # Run all tests
make clean             # Clean all build artifacts
make docker-up         # Start with Docker Compose
make status            # Check status of all services
```

## Manual Setup Instructions

### Option A: Local Development with SQLite (Recommended for Testing)

**No external database setup required!** The system automatically uses SQLite.

```bash
cd edi835-processor

# Run with SQLite profile (default)
mvn spring-boot:run

# Or explicitly set the profile
mvn spring-boot:run -Dspring-boot.run.profiles=sqlite
```

The SQLite database will be created automatically at `./data/edi835-local.db`.

**Load Sample Data:**
```bash
# Install sqlite3 if not available
sudo apt-get install sqlite3  # Ubuntu/Debian
brew install sqlite3          # macOS

# Load sample claims and configuration
sqlite3 ./data/edi835-local.db < src/main/resources/db/sqlite/sample-data.sql
```

See [README-SQLITE.md](./edi835-processor/README-SQLITE.md) for detailed SQLite configuration and usage.

### Option B: Production Setup with Cosmos DB and PostgreSQL

### 1. Database Setup

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE edi835db

# Run schema
psql -U postgres -d edi835db -f database/schema.sql
```

Or use the Makefile:
```bash
make db-start          # Start PostgreSQL in Docker
```

### 2. Cosmos DB Setup

Create a Cosmos DB account in Azure Portal:
1. Create database: `claims`
2. Create container: `claims` (partition key: `/payerId`)
3. Create container: `leases` (partition key: `/id`)

### 3. Backend Configuration

**For SQLite (Local Development):**

The application uses SQLite by default. Configuration is in `application-sqlite.yml`.
No changes needed for basic usage.

**For Cosmos DB (Production):**

Edit `edi835-processor/src/main/resources/application.yml` or set environment variables:

```yaml
spring:
  profiles:
    active: cosmos  # Switch to cosmos profile

  datasource:
    url: jdbc:postgresql://localhost:5432/edi835config
    username: your_username
    password: your_password

  cloud:
    azure:
      cosmos:
        endpoint: https://your-account.documents.azure.com:443/
        key: your-cosmos-key
        database: claims

changefeed:
  type: cosmos
  cosmos:
    enabled: true
  sqlite:
    enabled: false
```

### 4. Build and Run Backend

**Using Makefile (Recommended):**
```bash
cd edi835-processor
make install           # Install dependencies
make run-dev           # Run with dev profile
```

**Manual:**
```bash
cd edi835-processor

# Build
mvn clean install

# Run
mvn spring-boot:run

# Or run JAR
java -jar target/edi835-processor-0.0.1-SNAPSHOT.jar
```

The backend API will be available at `http://localhost:8080`

### 5. Frontend Configuration

Create `.env` file in `edi835-admin-portal/`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

Or copy from example:
```bash
cp .env.example .env
# Edit .env with your values
```

### 6. Build and Run Frontend

**Using Makefile (Recommended):**
```bash
cd edi835-admin-portal
make install           # Install dependencies
make dev               # Run dev server
```

**Manual:**
```bash
cd edi835-admin-portal

# Install dependencies
npm install

# Run development server
npm run dev

# Build for production
npm run build
```

The frontend will be available at `http://localhost:5173`

### 7. Docker Deployment

**Using Docker Compose (Recommended):**
```bash
# From root directory
make docker-build      # Build images
make docker-up         # Start all services
make docker-logs       # View logs

# Services:
# - Frontend: http://localhost:3000
# - Backend: http://localhost:8080
# - PostgreSQL: localhost:5432
# - SFTP Server: localhost:2222
```

**Manual Docker:**
```bash
# Build
docker-compose build

# Start
docker-compose up -d

# View logs
docker-compose logs -f

# Stop
docker-compose down
```

## Key Features

### 1. Change Feed Processing
- **Production**: Real-time claim processing from Cosmos DB with partition-based parallel processing
- **Development**: SQLite-based version tracking with automatic triggers for local testing
- Automatic bucketing based on configurable rules
- Checkpoint management for resumability
- Replay capability for testing (SQLite mode)

### 2. Configurable Bucketing
- **Payer/Payee** combination (default)
- **BIN/PCN** based grouping
- **Custom** grouping expressions

### 3. File Generation Triggers
- **Claim Count**: Max claims per file
- **Amount**: Max total amount per file
- **Time-based**: Daily/Weekly/Biweekly/Monthly
- **Hybrid**: Combination of above

### 4. Commit Modes
- **AUTO**: Automatic generation when thresholds met
- **MANUAL**: Admin approval required
- **HYBRID**: Mixed approach with soft/hard limits

### 5. Dynamic File Naming
Configurable templates with variables:
- `{payerId}`, `{payeeId}`, `{bucketId}`
- `{timestamp}`, `{date}`, `{time}`
- `{sequenceNumber}`, `{claimCount}`
- `{binNumber}`, `{pcnNumber}`

Example: `{payerId}_{payeeId}_{date}_{sequenceNumber}.835`

### 6. Operations Dashboard
- Real-time active bucket monitoring
- Pending approval queue
- Rejection analytics
- File generation history
- Performance metrics

## API Endpoints

### Dashboard
```
GET /api/v1/dashboard/summary
GET /api/v1/dashboard/active-buckets
GET /api/v1/dashboard/rejections
GET /api/v1/dashboard/pending-approvals
```

### Bucketing Rules
```
GET    /api/v1/bucketing-rules
POST   /api/v1/bucketing-rules
PUT    /api/v1/bucketing-rules/{id}
DELETE /api/v1/bucketing-rules/{id}
```

### Thresholds
```
GET    /api/v1/generation-thresholds
POST   /api/v1/generation-thresholds
PUT    /api/v1/generation-thresholds/{id}
DELETE /api/v1/generation-thresholds/{id}
```

### File Naming
```
GET    /api/v1/file-naming-templates
POST   /api/v1/file-naming-templates
POST   /api/v1/file-naming-templates/{id}/preview
GET    /api/v1/file-naming-templates/variables
```

### Approvals
```
GET    /api/v1/files/buckets/pending-approval
POST   /api/v1/files/buckets/{id}/approve
POST   /api/v1/files/buckets/{id}/reject
POST   /api/v1/files/buckets/{id}/generate
```

## EDI 835 File Structure

Generated files follow HIPAA 5010 X12 835 standard:

```
ISA*...                    (Interchange Control Header)
  GS*...                   (Functional Group Header)
    ST*835*...             (Transaction Set Header)
    BPR*...                (Financial Information)
    TRN*...                (Trace Number)
    N1*PR*...              (Payer Identification Loop)
    N1*PE*...              (Payee Identification Loop)
    CLP*...                (Claim Payment Information)
      SVC*...              (Service Line)
      CAS*...              (Adjustments)
    SE*...                 (Transaction Set Trailer)
  GE*...                   (Functional Group Trailer)
IEA*...                    (Interchange Control Trailer)
```

## Authentication

Authentication is handled by the ecosystem's auth provider. The system expects:
- Auth tokens in request headers
- Role-based access control via token claims
- No internal authentication mechanism

## Monitoring

### Health Checks
```
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

### Logging
Logs are written to:
- Console (stdout)
- File: `logs/edi835-processor.log`

## Deployment

### Backend (Docker)
```dockerfile
FROM openjdk:17-jdk-slim
COPY target/edi835-processor-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

### Frontend (Nginx)
```dockerfile
FROM node:18 AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
```

## Testing

### Backend Tests
```bash
mvn test
```

### Frontend Tests
```bash
npm test
```

## Troubleshooting

### SQLite Change Feed (Local Development)
1. **Changes not processing**: Check that the database file exists at `./data/edi835-local.db`
2. **Triggers not firing**: Verify triggers are created with `SELECT name FROM sqlite_master WHERE type = 'trigger';`
3. **Database locked**: Ensure no other process is accessing the database (SQLite is single-writer)
4. See [README-SQLITE.md](./edi835-processor/README-SQLITE.md) for detailed troubleshooting

### Cosmos DB Change Feed (Production)
1. Check Cosmos DB connection string
2. Verify lease container exists
3. Check application logs for errors

### EDI Generation Fails
1. Verify StAEDI schema file exists
2. Check claim data completeness
3. Review validation errors in logs

### Frontend Can't Connect
1. Verify API_BASE_URL in .env
2. Check CORS configuration in backend
3. Verify backend is running

## License

Proprietary - All rights reserved

## Support

For issues or questions, contact the development team.