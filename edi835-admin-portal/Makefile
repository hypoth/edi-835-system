# Makefile for EDI 835 Admin Portal (React + TypeScript + Vite Project)

.PHONY: help clean install build dev preview test lint format type-check deploy docker-build docker-run

# Variables
NPM := npm
NODE := node
APP_NAME := edi835-admin-portal
DOCKER_IMAGE := $(APP_NAME):latest
DOCKER_CONTAINER := $(APP_NAME)-container
BUILD_DIR := dist
PORT := 5173
PREVIEW_PORT := 4173

# Default target
help:
	@echo "EDI 835 Admin Portal - Available targets:"
	@echo "  make install        - Install dependencies"
	@echo "  make dev            - Run development server"
	@echo "  make build          - Build for production"
	@echo "  make preview        - Preview production build"
	@echo "  make test           - Run tests"
	@echo "  make test-watch     - Run tests in watch mode"
	@echo "  make test-coverage  - Run tests with coverage"
	@echo "  make lint           - Run ESLint"
	@echo "  make lint-fix       - Fix ESLint issues"
	@echo "  make format         - Format code with Prettier"
	@echo "  make format-check   - Check code formatting"
	@echo "  make type-check     - Run TypeScript type checking"
	@echo "  make clean          - Clean build artifacts"
	@echo "  make clean-all      - Clean all (including node_modules)"
	@echo "  make docker-build   - Build Docker image"
	@echo "  make docker-run     - Run Docker container"
	@echo "  make docker-stop    - Stop Docker container"
	@echo "  make deploy         - Deploy application"
	@echo "  make analyze        - Analyze bundle size"
	@echo "  make upgrade        - Upgrade dependencies"

# Install dependencies
install:
	@echo "Installing dependencies..."
	$(NPM) install

# Install with clean cache
install-clean:
	@echo "Installing dependencies with clean cache..."
	$(NPM) ci

# Run development server
dev:
	@echo "Starting development server on port $(PORT)..."
	$(NPM) run dev

# Run development server with specific port
dev-port:
	@echo "Starting development server on custom port..."
	$(NPM) run dev -- --port $(PORT)

# Build for production
build:
	@echo "Building for production..."
	$(NPM) run build

# Build with type checking
build-strict:
	@echo "Building with strict type checking..."
	$(NPM) run type-check && $(NPM) run build

# Preview production build
preview:
	@echo "Previewing production build on port $(PREVIEW_PORT)..."
	$(NPM) run preview

# Run tests
test:
	@echo "Running tests..."
	$(NPM) test

# Run tests in watch mode
test-watch:
	@echo "Running tests in watch mode..."
	$(NPM) test -- --watch

# Run tests with coverage
test-coverage:
	@echo "Running tests with coverage..."
	$(NPM) test -- --coverage

# Run ESLint
lint:
	@echo "Running ESLint..."
	$(NPM) run lint

# Fix ESLint issues
lint-fix:
	@echo "Fixing ESLint issues..."
	$(NPM) run lint -- --fix

# Format code with Prettier
format:
	@echo "Formatting code..."
	@if grep -q "prettier" package.json; then \
		$(NPM) run format || npx prettier --write "src/**/*.{ts,tsx,js,jsx,json,css,scss,md}"; \
	else \
		npx prettier --write "src/**/*.{ts,tsx,js,jsx,json,css,scss,md}"; \
	fi

# Check code formatting
format-check:
	@echo "Checking code formatting..."
	@if grep -q "prettier" package.json; then \
		$(NPM) run format:check || npx prettier --check "src/**/*.{ts,tsx,js,jsx,json,css,scss,md}"; \
	else \
		npx prettier --check "src/**/*.{ts,tsx,js,jsx,json,css,scss,md}"; \
	fi

# TypeScript type checking
type-check:
	@echo "Running TypeScript type checking..."
	$(NPM) run type-check || npx tsc --noEmit

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	rm -rf $(BUILD_DIR)
	rm -rf .vite
	rm -rf coverage

# Clean all (including node_modules)
clean-all: clean
	@echo "Cleaning node_modules..."
	rm -rf node_modules
	rm -f package-lock.json

# Analyze bundle size
analyze:
	@echo "Analyzing bundle size..."
	@if grep -q "vite-plugin-bundle-analyzer" package.json; then \
		$(NPM) run build -- --mode analyze; \
	else \
		echo "Install vite-plugin-bundle-analyzer for bundle analysis"; \
		$(NPM) run build && npx vite-bundle-visualizer; \
	fi

# Upgrade dependencies
upgrade:
	@echo "Checking for dependency upgrades..."
	$(NPM) outdated
	@echo ""
	@echo "To upgrade, run: npm update"

# Upgrade dependencies interactively
upgrade-interactive:
	@echo "Upgrading dependencies interactively..."
	npx npm-check-updates -i

# Docker build
docker-build: build
	@echo "Building Docker image..."
	docker build -t $(DOCKER_IMAGE) .

# Docker run
docker-run:
	@echo "Running Docker container..."
	docker run -d --name $(DOCKER_CONTAINER) -p 3000:80 $(DOCKER_IMAGE)

# Docker stop
docker-stop:
	@echo "Stopping Docker container..."
	docker stop $(DOCKER_CONTAINER) || true
	docker rm $(DOCKER_CONTAINER) || true

# Docker logs
docker-logs:
	@echo "Showing Docker logs..."
	docker logs -f $(DOCKER_CONTAINER)

# Deploy (customize based on your deployment strategy)
deploy: build
	@echo "Deploying application..."
	@echo "Configure your deployment strategy here (e.g., AWS S3, Netlify, Vercel)"
	@echo "Example: aws s3 sync $(BUILD_DIR) s3://your-bucket-name"

# Development workflow: clean, install, type-check, lint, test
dev-check: clean install type-check lint test

# CI/CD workflow: clean, install, type-check, lint, test, build
ci: clean install type-check lint test build

# Quick check (type-check + lint)
check: type-check lint

# Fix all issues (format + lint-fix)
fix-all: format lint-fix

# Full rebuild
rebuild: clean-all install build

# Generate production build and preview
build-preview: build preview

# Serve build directory with local server
serve:
	@echo "Serving build directory..."
	@if command -v python3 > /dev/null; then \
		cd $(BUILD_DIR) && python3 -m http.server 8000; \
	elif command -v python > /dev/null; then \
		cd $(BUILD_DIR) && python -m SimpleHTTPServer 8000; \
	else \
		npx serve $(BUILD_DIR); \
	fi

# All-in-one: clean, install, check, test, build
all: clean install type-check lint test build
	@echo "Build complete!"

# Watch and rebuild on changes
watch:
	@echo "Watching for changes..."
	$(NPM) run dev -- --watch
