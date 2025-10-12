# EDI 835 Remittance Processing System

Healthcare claims processing and EDI 835 file generation system with real-time monitoring.

## Features

- Real-time claim processing via Azure Cosmos DB Change Feed
- Configurable claim bucketing (Payer/Payee, BIN/PCN, Custom)
- Multiple file generation triggers (count, amount, time-based)
- Three commit modes: AUTO, MANUAL, HYBRID
- Dynamic file naming templates
- Operations dashboard with live monitoring
- Rejection analytics
- Approval workflow

## Quick Start

### With Docker Compose (Recommended)

```bash
# 1. Configure environment
cp .env.example .env
# Edit .env with your Azure Cosmos DB credentials

# 2. Start all services
docker-compose up -d

# 3. Access the application
# Backend API: http://localhost:8080/api/v1
# Frontend: http://localhost
# PostgreSQL: localhost:5432
```

### Manual Setup

**Prerequisites:**
- JDK 17+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.8+
- Azure Cosmos DB account

**Backend:**
```bash
cd edi835-processor
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Edit application.yml with your settings
mvn spring-boot:run
```

**Frontend:**
```bash
cd edi835-admin-portal
cp .env.example .env
npm install
npm run dev
```

**Database:**
```bash
createdb edi835config
psql -d edi835config -f database/schema.sql
```

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.

## Project Structure

```
edi-835-system/
├── edi835-processor/          # Java Spring Boot backend
├── edi835-admin-portal/       # React TypeScript frontend
├── database/                  # PostgreSQL schema and migrations
└── docker-compose.yml         # Docker orchestration
```

## Technology Stack

**Backend:**
- Java 17, Spring Boot 3.x
- Azure Cosmos DB (Change Feed)
- StAEDI (EDI processing)
- PostgreSQL (Configuration)

**Frontend:**
- React 18, TypeScript
- Material-UI
- Redux Toolkit
- React Query

## API Documentation

Once running, access Swagger UI at: http://localhost:8080/swagger-ui.html

## Contributing

This is a proprietary project. See your team lead for contribution guidelines.

## License

Proprietary - All Rights Reserved
