# Makefile Usage Guide

This project includes Makefiles at three levels to simplify development, testing, and deployment:

1. **Root Makefile** - Orchestrates both backend and frontend
2. **edi835-processor/Makefile** - Backend (Spring Boot) operations
3. **edi835-admin-portal/Makefile** - Frontend (React + TypeScript) operations

## Quick Start

```bash
# From root directory
make help              # Show all available commands
make setup             # Setup development environment
make dev               # Start both backend and frontend
```

---

## Root Makefile Commands

The root Makefile orchestrates both projects from the root directory.

### Getting Help

```bash
make help              # Display all available commands with descriptions
```

### Complete Workflows

```bash
make all               # Clean, install, test, and build everything
make ci                # Run full CI pipeline (test + build)
make setup             # Setup complete development environment
make quick-start       # Install and start development servers
make prod-build        # Production build with all checks
```

### Installation & Build

```bash
make install           # Install dependencies for both projects
make build             # Build both projects
make clean             # Clean build artifacts from both projects
```

### Testing

```bash
make test              # Run tests for both projects
make verify            # Run tests and health checks
```

### Development

```bash
make dev               # Run both projects in development mode
                       # Backend: http://localhost:8080
                       # Frontend: http://localhost:5173
```

### Docker Operations

```bash
make docker-build      # Build Docker images for both projects
make docker-up         # Start all services with docker-compose
make docker-down       # Stop all services
make docker-logs       # View logs from all containers
make docker-ps         # Show running containers
make docker-clean      # Clean Docker resources
```

### Database Operations

```bash
make db-start          # Start PostgreSQL database
make db-stop           # Stop PostgreSQL database
make db-migrate        # Run database migrations
make db-logs           # View database logs
```

### Utilities

```bash
make status            # Check status of all services
make health            # Check health of running services
make logs              # Tail logs from both projects
make update            # Update all dependencies
```

---

## Backend Makefile (edi835-processor)

Commands for the Spring Boot backend application.

### Basic Operations

```bash
cd edi835-processor
make help              # Show backend-specific commands
make clean             # Clean build artifacts
make build             # Compile the project
make test              # Run unit tests
make package           # Package as JAR (skip tests)
make package-full      # Package with tests
make install           # Install to local Maven repository
```

### Running the Application

```bash
make run               # Run with default profile
make run-dev           # Run with dev profile
make run-prod          # Run with prod profile
make run-jar           # Run packaged JAR
make run-jar-dev       # Run JAR with dev profile
make run-jar-prod      # Run JAR with prod profile
```

### Testing & Quality

```bash
make test              # Run tests
make test-coverage     # Run tests with coverage report
make verify            # Run Maven verify (tests + checks)
make lint              # Run code quality checks
make format            # Format code
```

### Development Workflows

```bash
make dev               # Clean, build, test
make ci                # Clean, verify, package
make full-build        # Clean, verify, package
make quick-build       # Clean, package (skip tests)
```

### Dependencies & Analysis

```bash
make dependency-tree   # Show dependency tree
make update-deps       # Show dependency updates
```

### Docker

```bash
make docker-build      # Build Docker image
make docker-run        # Run in Docker container
make docker-stop       # Stop Docker container
make docker-logs       # View Docker logs
```

### Database

```bash
make db-migrate        # Run database migrations (Flyway/Liquibase)
```

### Monitoring

```bash
make logs              # Tail application logs
make health            # Check application health endpoint
```

---

## Frontend Makefile (edi835-admin-portal)

Commands for the React + TypeScript + Vite frontend application.

### Basic Operations

```bash
cd edi835-admin-portal
make help              # Show frontend-specific commands
make install           # Install npm dependencies
make install-clean     # Clean install (npm ci)
make clean             # Remove build artifacts
make clean-all         # Remove build artifacts and node_modules
```

### Development

```bash
make dev               # Start dev server (http://localhost:5173)
make dev-port          # Start dev server on custom port
                       # PORT=3000 make dev-port
```

### Building

```bash
make build             # Build for production
make build-strict      # Build with type checking
make preview           # Preview production build
make build-preview     # Build and preview
```

### Testing

```bash
make test              # Run tests
make test-watch        # Run tests in watch mode
make test-coverage     # Run tests with coverage
```

### Code Quality

```bash
make lint              # Run ESLint
make lint-fix          # Fix ESLint issues
make format            # Format code with Prettier
make format-check      # Check code formatting
make type-check        # Run TypeScript type checking
```

### Combined Checks

```bash
make check             # Type-check + lint
make fix-all           # Format + lint-fix
make dev-check         # Clean, install, type-check, lint, test
make ci                # Full CI pipeline
```

### Dependencies

```bash
make upgrade           # Show outdated dependencies
make upgrade-interactive # Upgrade dependencies interactively
```

### Docker

```bash
make docker-build      # Build Docker image
make docker-run        # Run in Docker container (port 3000)
make docker-stop       # Stop Docker container
make docker-logs       # View Docker logs
```

### Analysis & Deployment

```bash
make analyze           # Analyze bundle size
make serve             # Serve build directory locally
make deploy            # Deploy application
```

### Development Workflows

```bash
make rebuild           # Clean all, install, build
make all               # Clean, install, type-check, lint, test, build
```

---

## Common Usage Patterns

### Starting Development

From the root directory:

```bash
# First time setup
make setup             # Install dependencies + start databases

# Daily development
make dev               # Start both backend and frontend
```

Or run them separately:

```bash
# Terminal 1 - Backend
cd edi835-processor
make run-dev

# Terminal 2 - Frontend
cd edi835-admin-portal
make dev
```

### Building for Production

```bash
# From root directory
make prod-build        # Complete production build

# Or individually
cd edi835-processor && make package
cd edi835-admin-portal && make build
```

### Running Tests

```bash
# From root directory
make test              # Test both projects

# Or individually
cd edi835-processor && make test
cd edi835-admin-portal && make test
```

### Docker Workflow

```bash
# Build images
make docker-build

# Start services
make docker-up

# View logs
make docker-logs

# Stop services
make docker-down
```

### Checking Project Status

```bash
# From root directory
make status            # Show status of all services
make health            # Check health endpoints
```

### Cleaning Up

```bash
# Clean build artifacts
make clean

# Clean everything including dependencies
cd edi835-processor && make clean
cd edi835-admin-portal && make clean-all

# Clean Docker resources
make docker-clean
```

---

## Environment Variables

Copy `.env.example` to `.env` and update with your values:

```bash
cp .env.example .env
# Edit .env with your Azure Cosmos DB credentials
```

Required variables:
- `AZURE_COSMOS_ENDPOINT` - Your Cosmos DB endpoint
- `AZURE_COSMOS_KEY` - Your Cosmos DB key
- `AZURE_COSMOS_DATABASE` - Database name
- `AZURE_COSMOS_CONTAINER` - Container name

---

## Tips & Best Practices

### Parallel Execution

The root `make dev` command runs backend and frontend in parallel. Use `Ctrl+C` to stop both.

### Custom Ports

Frontend dev server:
```bash
cd edi835-admin-portal
PORT=3000 make dev-port
```

### Incremental Builds

For faster rebuilds during development:
```bash
# Backend - skip tests
cd edi835-processor
make quick-build

# Frontend - just rebuild
cd edi835-admin-portal
make build
```

### Database Management

Start PostgreSQL for local development:
```bash
make db-start          # Start PostgreSQL in Docker
# Configure backend to use localhost:5432
make db-stop           # Stop when done
```

### Watching Logs

Monitor application logs in real-time:
```bash
# From root
make logs

# Or individually
cd edi835-processor && make logs
```

### Code Quality Workflow

Before committing:
```bash
cd edi835-admin-portal
make fix-all           # Format and fix lint issues
make type-check        # Ensure no TypeScript errors
```

### CI/CD Integration

For CI/CD pipelines:
```bash
make ci                # Runs: clean, install, test, build
```

---

## Troubleshooting

### Port Already in Use

```bash
# Backend (8080)
lsof -ti:8080 | xargs kill -9

# Frontend (5173)
lsof -ti:5173 | xargs kill -9
```

### Database Connection Issues

```bash
# Check if PostgreSQL is running
make db-start
docker ps | grep postgres

# Check logs
make db-logs
```

### Docker Issues

```bash
# Clean everything and restart
make docker-down
make docker-clean
make docker-build
make docker-up
```

### Build Failures

```bash
# Backend - clean and rebuild
cd edi835-processor
make clean
make full-build

# Frontend - clean and rebuild
cd edi835-admin-portal
make clean-all
make install
make build
```

---

## Additional Resources

- **Backend**: See `edi835-processor/README.md` for detailed backend documentation
- **Frontend**: See `edi835-admin-portal/README.md` for detailed frontend documentation
- **Docker**: See `docker-compose.yml` for container configuration
- **Environment**: See `.env.example` for required environment variables
