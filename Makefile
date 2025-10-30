# Root Makefile for EDI 835 System
# Manages both edi835-processor (Backend) and edi835-admin-portal (Frontend)

.PHONY: help clean install build test run dev deploy docker-up docker-down status

# Variables
PROCESSOR_DIR := edi835-processor
PORTAL_DIR := edi835-admin-portal
MAVEN := mvn
NPM := npm

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m # No Color

# Default target
help:
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(GREEN)       EDI 835 Remittance Processing System$(NC)"
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo ""
	@echo "$(YELLOW)Project Management:$(NC)"
	@echo "  make install          - Install dependencies for both projects"
	@echo "  make build            - Build both projects"
	@echo "  make clean            - Clean both projects"
	@echo "  make test             - Run tests for both projects"
	@echo "  make dev              - Run both projects in development mode"
	@echo "  make deploy           - Deploy both projects"
	@echo ""
	@echo "$(YELLOW)Backend (edi835-processor):$(NC)"
	@echo "  make backend-install  - Install backend dependencies"
	@echo "  make backend-build    - Build backend"
	@echo "  make backend-test     - Test backend"
	@echo "  make backend-run      - Run backend"
	@echo "  make backend-clean    - Clean backend"
	@echo "  make backend-package  - Package backend as JAR"
	@echo ""
	@echo "$(YELLOW)Frontend (edi835-admin-portal):$(NC)"
	@echo "  make frontend-install - Install frontend dependencies"
	@echo "  make frontend-build   - Build frontend"
	@echo "  make frontend-test    - Test frontend"
	@echo "  make frontend-dev     - Run frontend dev server"
	@echo "  make frontend-clean   - Clean frontend"
	@echo ""
	@echo "$(YELLOW)Docker:$(NC)"
	@echo "  make docker-build     - Build Docker images for both"
	@echo "  make docker-up        - Start all services with docker-compose"
	@echo "  make docker-down      - Stop all services"
	@echo "  make docker-logs      - View logs from all containers"
	@echo "  make docker-clean     - Clean Docker images and containers"
	@echo ""
	@echo "$(YELLOW)Database:$(NC)"
	@echo "  make db-start         - Start local databases (PostgreSQL, Cosmos emulator)"
	@echo "  make db-stop          - Stop local databases"
	@echo "  make db-migrate       - Run database migrations"
	@echo ""
	@echo "$(YELLOW)Utilities:$(NC)"
	@echo "  make status           - Check status of both projects"
	@echo "  make logs             - Tail logs from both projects"
	@echo "  make health           - Check health of running services"
	@echo "  make ci               - Run CI pipeline (test + build)"
	@echo "  make all              - Clean, install, test, build everything"
	@echo ""

# ═══════════════════════════════════════════════════════════
# Combined Targets (Both Projects)
# ═══════════════════════════════════════════════════════════

install: backend-install frontend-install
	@echo "$(GREEN)✓ All dependencies installed$(NC)"

build: backend-build frontend-build
	@echo "$(GREEN)✓ Both projects built successfully$(NC)"

clean: backend-clean frontend-clean
	@echo "$(GREEN)✓ Both projects cleaned$(NC)"

test: backend-test frontend-test
	@echo "$(GREEN)✓ All tests completed$(NC)"

dev:
	@echo "$(YELLOW)Starting both projects in development mode...$(NC)"
	@echo "$(BLUE)Backend will run on http://localhost:8080$(NC)"
	@echo "$(BLUE)Frontend will run on http://localhost:5173$(NC)"
	@$(MAKE) -j2 backend-run frontend-dev

deploy: backend-deploy frontend-deploy
	@echo "$(GREEN)✓ Both projects deployed$(NC)"

# CI/CD pipeline
ci: clean install test build
	@echo "$(GREEN)✓ CI pipeline completed successfully$(NC)"

# All-in-one target
all: clean install test build
	@echo "$(GREEN)✓ Complete build finished!$(NC)"

# ═══════════════════════════════════════════════════════════
# Backend Targets (edi835-processor)
# ═══════════════════════════════════════════════════════════

backend-install:
	@echo "$(YELLOW)Installing backend dependencies...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) install

backend-build:
	@echo "$(YELLOW)Building backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) build

backend-test:
	@echo "$(YELLOW)Testing backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) test

backend-run:
	@echo "$(YELLOW)Running backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) run

backend-run-dev:
	@echo "$(YELLOW)Running backend (dev profile)...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) run-dev

backend-run-prod:
	@echo "$(YELLOW)Running backend (prod profile)...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) run-prod

backend-clean:
	@echo "$(YELLOW)Cleaning backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) clean

backend-package:
	@echo "$(YELLOW)Packaging backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) package

backend-deploy:
	@echo "$(YELLOW)Deploying backend...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) deploy

backend-logs:
	@echo "$(YELLOW)Showing backend logs...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) logs

backend-health:
	@echo "$(YELLOW)Checking backend health...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) health

# ═══════════════════════════════════════════════════════════
# Frontend Targets (edi835-admin-portal)
# ═══════════════════════════════════════════════════════════

frontend-install:
	@echo "$(YELLOW)Installing frontend dependencies...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) install

frontend-build:
	@echo "$(YELLOW)Building frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) build

frontend-test:
	@echo "$(YELLOW)Testing frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) test

frontend-dev:
	@echo "$(YELLOW)Running frontend dev server...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) dev

frontend-clean:
	@echo "$(YELLOW)Cleaning frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) clean

frontend-lint:
	@echo "$(YELLOW)Linting frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) lint

frontend-format:
	@echo "$(YELLOW)Formatting frontend code...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) format

frontend-type-check:
	@echo "$(YELLOW)Type checking frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) type-check

frontend-deploy:
	@echo "$(YELLOW)Deploying frontend...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) deploy

frontend-preview:
	@echo "$(YELLOW)Previewing frontend build...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) preview

# ═══════════════════════════════════════════════════════════
# Docker Targets
# ═══════════════════════════════════════════════════════════

docker-build:
	@echo "$(YELLOW)Building Docker images...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) docker-build
	cd $(PORTAL_DIR) && $(MAKE) docker-build
	@echo "$(GREEN)✓ Docker images built$(NC)"

docker-up:
	@echo "$(YELLOW)Starting services with docker-compose...$(NC)"
	@if [ -f docker-compose.yml ]; then \
		docker-compose up -d; \
		echo "$(GREEN)✓ Services started$(NC)"; \
	else \
		echo "$(RED)✗ docker-compose.yml not found$(NC)"; \
	fi

docker-down:
	@echo "$(YELLOW)Stopping services...$(NC)"
	@if [ -f docker-compose.yml ]; then \
		docker-compose down; \
		echo "$(GREEN)✓ Services stopped$(NC)"; \
	else \
		echo "$(RED)✗ docker-compose.yml not found$(NC)"; \
	fi

docker-logs:
	@echo "$(YELLOW)Showing Docker logs...$(NC)"
	@if [ -f docker-compose.yml ]; then \
		docker-compose logs -f; \
	else \
		echo "$(RED)✗ docker-compose.yml not found$(NC)"; \
	fi

docker-ps:
	@echo "$(YELLOW)Docker containers status:$(NC)"
	docker ps -a | grep edi835 || echo "No EDI 835 containers running"

docker-clean:
	@echo "$(YELLOW)Cleaning Docker resources...$(NC)"
	@if [ -f docker-compose.yml ]; then \
		docker-compose down -v --rmi local; \
	fi
	docker images | grep edi835 | awk '{print $$3}' | xargs -r docker rmi -f || true
	@echo "$(GREEN)✓ Docker resources cleaned$(NC)"

# ═══════════════════════════════════════════════════════════
# Database Targets
# ═══════════════════════════════════════════════════════════

db-start:
	@echo "$(YELLOW)Starting local databases...$(NC)"
	@echo "Starting PostgreSQL..."
	@docker run -d --name edi835-postgres \
		-e POSTGRES_DB=edi835db \
		-e POSTGRES_USER=edi835user \
		-e POSTGRES_PASSWORD=edi835pass \
		-p 5432:5432 \
		postgres:15-alpine || echo "PostgreSQL already running or failed to start"
	@echo "$(GREEN)✓ Databases started$(NC)"

db-stop:
	@echo "$(YELLOW)Stopping local databases...$(NC)"
	docker stop edi835-postgres || true
	docker rm edi835-postgres || true
	@echo "$(GREEN)✓ Databases stopped$(NC)"

db-migrate:
	@echo "$(YELLOW)Running database migrations...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) db-migrate

db-logs:
	@echo "$(YELLOW)Showing database logs...$(NC)"
	docker logs -f edi835-postgres

# ═══════════════════════════════════════════════════════════
# Utility Targets
# ═══════════════════════════════════════════════════════════

status:
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo "$(GREEN)       EDI 835 System Status$(NC)"
	@echo "$(BLUE)═══════════════════════════════════════════════════════════$(NC)"
	@echo ""
	@echo "$(YELLOW)Backend (edi835-processor):$(NC)"
	@if [ -d "$(PROCESSOR_DIR)/target" ]; then \
		echo "  Build: $(GREEN)✓ Built$(NC)"; \
	else \
		echo "  Build: $(RED)✗ Not built$(NC)"; \
	fi
	@curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1 && \
		echo "  Status: $(GREEN)✓ Running$(NC)" || \
		echo "  Status: $(RED)✗ Not running$(NC)"
	@echo ""
	@echo "$(YELLOW)Frontend (edi835-admin-portal):$(NC)"
	@if [ -d "$(PORTAL_DIR)/dist" ]; then \
		echo "  Build: $(GREEN)✓ Built$(NC)"; \
	else \
		echo "  Build: $(RED)✗ Not built$(NC)"; \
	fi
	@if [ -d "$(PORTAL_DIR)/node_modules" ]; then \
		echo "  Dependencies: $(GREEN)✓ Installed$(NC)"; \
	else \
		echo "  Dependencies: $(RED)✗ Not installed$(NC)"; \
	fi
	@echo ""
	@echo "$(YELLOW)Docker:$(NC)"
	@docker ps -a | grep edi835 > /dev/null 2>&1 && \
		echo "  Containers: $(GREEN)✓ Running$(NC)" || \
		echo "  Containers: $(RED)✗ Not running$(NC)"
	@echo ""

logs:
	@echo "$(YELLOW)Tailing logs from both projects...$(NC)"
	@$(MAKE) -j2 backend-logs frontend-logs

health:
	@echo "$(YELLOW)Checking health of services...$(NC)"
	@echo "Backend Health:"
	@curl -f http://localhost:8080/actuator/health 2>/dev/null | jq . || \
		echo "$(RED)Backend not responding$(NC)"
	@echo ""
	@echo "Frontend:"
	@curl -f http://localhost:5173 > /dev/null 2>&1 && \
		echo "$(GREEN)Frontend dev server is running$(NC)" || \
		echo "$(RED)Frontend dev server not running$(NC)"

# Quick start for development
quick-start: install
	@echo "$(GREEN)Starting quick development environment...$(NC)"
	@$(MAKE) dev

# Production build
prod-build: clean install test build
	@echo "$(GREEN)✓ Production build complete$(NC)"

# Verify everything is working
verify: test health
	@echo "$(GREEN)✓ System verification complete$(NC)"

# Update all dependencies
update:
	@echo "$(YELLOW)Updating backend dependencies...$(NC)"
	cd $(PROCESSOR_DIR) && $(MAKE) update-deps
	@echo "$(YELLOW)Updating frontend dependencies...$(NC)"
	cd $(PORTAL_DIR) && $(MAKE) upgrade

# Setup development environment
setup: install db-start
	@echo "$(GREEN)✓ Development environment setup complete$(NC)"
	@echo ""
	@echo "$(BLUE)Next steps:$(NC)"
	@echo "  1. Configure application.yml in $(PROCESSOR_DIR)/src/main/resources/"
	@echo "  2. Configure API endpoint in $(PORTAL_DIR)/src/services/apiClient.ts"
	@echo "  3. Run: make dev"

# Teardown development environment
teardown: docker-down db-stop clean
	@echo "$(GREEN)✓ Development environment torn down$(NC)"
