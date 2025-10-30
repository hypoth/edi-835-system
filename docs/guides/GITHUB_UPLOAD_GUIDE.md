# GitHub Upload Guide

## Overview

This guide provides a comprehensive plan for uploading the EDI 835 Healthcare Claims Processing System to GitHub. It includes file analysis, security considerations, and step-by-step instructions.

---

## Files Analysis

### Files That SHOULD BE UPLOADED

#### Documentation & Configuration
- `CLAUDE.md` - Project instructions for AI assistants
- `README.md` - Project documentation
- `.env.example` - Environment variable template (safe, no secrets)
- `.gitignore` - Git ignore configuration (already properly configured)
- `docker-compose.yml` - Docker setup
- `Makefile` - Build automation
- All files in `docs/` - Documentation

#### Backend (Java/Spring Boot)
- `edi835-processor/src/` - All source code
- `edi835-processor/pom.xml` - Maven configuration
- `edi835-processor/Makefile` - Build commands
- `edi835-processor/test-startup.sh` - Test script
- All application config files in `src/main/resources/`

#### Frontend (React/TypeScript)
- `edi835-admin-portal/src/` - All source code
- `edi835-admin-portal/package.json` - Dependencies
- `edi835-admin-portal/package-lock.json` - Locked versions
- `edi835-admin-portal/tsconfig.json` - TypeScript config
- `edi835-admin-portal/tsconfig.node.json` - Node TypeScript config
- `edi835-admin-portal/vite.config.ts` - Vite build config
- `edi835-admin-portal/vitest.config.ts` - Test configuration
- `edi835-admin-portal/.nvmrc` - Node version specification
- `edi835-admin-portal/.prettierrc`, `.prettierignore` - Code formatting config
- `edi835-admin-portal/Makefile` - Build commands

#### Database
- `database/schema.sql` - Database schema
- `database/migrations/` - Migration scripts

#### Scripts
- `test-change-feed.sh` - Change feed testing script
- `inspect-changefeed.sh` - Change feed inspection script

---

### Files That SHOULD NOT BE UPLOADED

These files are already excluded by `.gitignore`:

- `.env` files - Contain secrets and sensitive configuration
- `edi835-processor/data/*.db` - Local SQLite database files
- `edi835-processor/data/*.db-shm` - SQLite shared memory files
- `edi835-processor/data/*.db-wal` - SQLite write-ahead log files
- `edi835-processor/logs/*.log` - Application log files
- `edi835-processor/target/` - Maven build artifacts
- `edi835-admin-portal/node_modules/` - NPM dependencies (installed via npm)
- `edi835-admin-portal/dist/` - Vite build output
- IDE files (`.idea/`, `.vscode/`, `*.iml`) - IDE-specific files

---

### Optional Files (Your Decision)

- `TBD.txt` - Personal to-do list (recommended to exclude)

**Recommendation:** Add `TBD.txt` to `.gitignore` as it contains personal notes not relevant to other developers.

---

## Upload Plan & Steps

### Step 1: Pre-Upload Checklist

Before uploading, verify the following:

1. **Review `.env.example`** - Ensure it contains only placeholder values, no actual secrets
2. **Verify `.gitignore` is working** - Check that sensitive files are excluded
3. **Decide on optional files** - Determine if `TBD.txt` should be excluded
4. **Review staged files** - Ensure all wanted files are ready for commit

### Step 2: Prepare Git Repository

Execute the following preparatory steps:

1. **Add `TBD.txt` to `.gitignore`** (recommended):
   ```bash
   echo "TBD.txt" >> .gitignore
   ```

2. **Stage all changes**:
   ```bash
   git add .
   ```

3. **Review what will be committed**:
   ```bash
   git status
   ```

4. **Check for any unwanted files**:
   ```bash
   git status --ignored
   ```

5. **Create a meaningful commit**:
   ```bash
   git commit -m "Initial commit: EDI 835 Healthcare Claims Processing System

   - Java Spring Boot backend with Cosmos DB change feed processing
   - React TypeScript admin portal for configuration
   - StAEDI EDI 835 file generation
   - PostgreSQL/SQLite configuration database
   - Complete documentation and deployment guides"
   ```

### Step 3: Create GitHub Repository

1. Navigate to [github.com](https://github.com) and sign in
2. Click the "+" icon in the top-right corner
3. Select "New repository"
4. Configure repository:
   - **Repository name**: `edi-835-system` (or your preferred name)
   - **Description**: "Healthcare EDI 835 Claims Processing System with Azure Cosmos DB Change Feed"
   - **Visibility**: **Private** (recommended - healthcare data is sensitive)
   - **DO NOT** initialize with README, .gitignore, or license (you already have these)
5. Click "Create repository"

### Step 4: Push to GitHub

1. **Add GitHub remote** (replace with your actual repository URL):
   ```bash
   git remote add origin https://github.com/YOUR_USERNAME/edi-835-system.git
   ```

2. **Verify remote was added**:
   ```bash
   git remote -v
   ```

3. **Rename branch to master** (if needed):
   ```bash
   git branch -M master
   ```

4. **Push to GitHub**:
   ```bash
   git push -u origin master
   ```

5. **Verify upload**:
   - Navigate to your repository on GitHub
   - Verify all files and folders are present
   - Check that no sensitive files were uploaded

### Step 5: Post-Upload Configuration

#### Add Repository Details

1. **Add repository description**:
   - Go to repository settings
   - Add description: "Healthcare EDI 835 Claims Processing System"
   - Add topics: `edi`, `healthcare`, `spring-boot`, `react`, `azure`, `cosmos-db`, `staedi`

2. **Add repository README preview**:
   - GitHub should automatically display your `README.md`
   - Verify it renders correctly

#### Security Configuration

1. **Review Security Tab**:
   - Navigate to "Security" tab
   - Check "Secret scanning" alerts
   - Review any dependency vulnerabilities

2. **Enable Security Features** (for private repos):
   - Navigate to Settings → Security & analysis
   - Enable "Dependency graph"
   - Enable "Dependabot alerts"
   - Enable "Dependabot security updates"

3. **Configure Branch Protection** (optional but recommended):
   - Go to Settings → Branches
   - Add rule for `master` branch
   - Enable:
     - "Require pull request reviews before merging"
     - "Require status checks to pass before merging"
     - "Require branches to be up to date before merging"

#### Access Management

1. **Invite collaborators** (if applicable):
   - Go to Settings → Collaborators
   - Add team members with appropriate permissions

2. **Set up deploy keys** (if needed for CI/CD):
   - Go to Settings → Deploy keys
   - Add SSH keys for automated deployments

---

## Complete Command Sequence

Here's the complete sequence of commands ready to execute:

```bash
# Navigate to project directory
cd /home/kp/work/edi/change_feed_edi/edi-835-system

# 1. Add TBD.txt to .gitignore (recommended)
echo "TBD.txt" >> .gitignore

# 2. Stage all changes
git add .

# 3. Review what will be committed
git status

# 4. View ignored files (verify .env and sensitive files are excluded)
git status --ignored

# 5. Create commit
git commit -m "Initial commit: EDI 835 Healthcare Claims Processing System

- Java Spring Boot backend with Cosmos DB change feed processing
- React TypeScript admin portal for configuration
- StAEDI EDI 835 file generation
- PostgreSQL/SQLite configuration database
- Complete documentation and deployment guides"

# 6. Create GitHub repository manually at github.com
# (Make it Private due to healthcare data sensitivity)

# 7. Add remote (replace YOUR_USERNAME and REPO_NAME)
git remote add origin https://github.com/YOUR_USERNAME/REPO_NAME.git

# 8. Verify remote
git remote -v

# 9. Ensure on master branch
git branch -M master

# 10. Push to GitHub
git push -u origin master

# 11. Verify upload on GitHub web interface
```

---

## Security Checklist

Before and after uploading, verify the following security considerations:

### Pre-Upload Security

- [x] `.gitignore` properly configured
- [x] `.env.example` contains only placeholders
- [x] No actual API keys or credentials in tracked files
- [x] Database files excluded (`.db`, `.sqlite`)
- [x] Log files excluded (`logs/`, `*.log`)
- [x] Build artifacts excluded (`target/`, `node_modules/`, `dist/`)

### Post-Upload Security

- [ ] Repository set to **Private** (healthcare data is sensitive)
- [ ] No secrets exposed in commit history
- [ ] GitHub secret scanning enabled
- [ ] Dependabot alerts enabled
- [ ] Branch protection rules configured
- [ ] Access limited to authorized team members only

### Sensitive Data Categories

**Never commit these to GitHub:**

1. **Credentials & Keys**:
   - Azure Cosmos DB keys
   - Database passwords
   - SFTP passwords
   - Encryption keys
   - API tokens

2. **Personal Information**:
   - Real patient/payer data
   - PHI (Protected Health Information)
   - PII (Personally Identifiable Information)

3. **Configuration**:
   - Production `.env` files
   - Real endpoint URLs (production)
   - Actual database connection strings

4. **Generated Data**:
   - EDI 835 files with real data
   - Database dumps
   - Log files with sensitive data

---

## Troubleshooting

### Issue: "remote origin already exists"

**Solution:**
```bash
# Remove existing remote
git remote remove origin

# Add new remote
git remote add origin https://github.com/YOUR_USERNAME/REPO_NAME.git
```

### Issue: Large files rejected

**Solution:**
```bash
# Check file sizes
find . -type f -size +50M

# If needed, add large files to .gitignore
echo "path/to/large/file" >> .gitignore
git rm --cached path/to/large/file
git commit -m "Remove large file from tracking"
```

### Issue: Accidentally committed .env file

**Solution:**
```bash
# Remove from Git (keep local file)
git rm --cached .env

# Add to .gitignore
echo ".env" >> .gitignore

# Commit the removal
git commit -m "Remove .env from tracking"

# Force push to remove from GitHub history (if already pushed)
git push -f origin master

# IMPORTANT: Rotate all secrets that were exposed!
```

### Issue: Push rejected (non-fast-forward)

**Solution:**
```bash
# If you're sure you want to overwrite remote
git push -f origin master

# Better approach: pull and merge first
git pull origin master --rebase
git push origin master
```

---

## Best Practices

### Commit Messages

Use clear, descriptive commit messages following this format:

```
Type: Short description (50 chars max)

Longer explanation if needed (wrap at 72 chars)
- Bullet points for multiple changes
- Use imperative mood ("Add feature" not "Added feature")
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

### Branch Strategy

Consider implementing a branch strategy:

- `master` or `main` - Production-ready code
- `develop` - Integration branch for features
- `feature/*` - Feature branches
- `hotfix/*` - Critical bug fixes
- `release/*` - Release preparation

### Regular Commits

Commit frequently with focused changes:
- Commit related changes together
- Keep commits atomic and focused
- Write meaningful commit messages
- Don't commit commented-out code
- Don't commit temporary files

---

## Next Steps After Upload

1. **Set up CI/CD pipeline** (GitHub Actions, Azure DevOps)
2. **Configure automated testing** (run tests on every PR)
3. **Set up code quality checks** (SonarQube, CodeQL)
4. **Document API endpoints** (Swagger/OpenAPI)
5. **Create issue templates** for bugs and features
6. **Set up project boards** for task tracking
7. **Add contributing guidelines** (`CONTRIBUTING.md`)
8. **Add license** if open source (`LICENSE`)

---

## Additional Resources

- [GitHub Documentation](https://docs.github.com)
- [Git Best Practices](https://git-scm.com/book/en/v2)
- [GitHub Security Best Practices](https://docs.github.com/en/code-security)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)

---

## Summary

**Total Project Size:** ~520MB (mostly `node_modules` which is gitignored)

**Files to Upload:**
- Source code (Java + React)
- Documentation
- Configuration templates
- Database schemas
- Build scripts

**Files Excluded:**
- Secrets and credentials
- Build artifacts
- Dependencies (installable via Maven/NPM)
- Local databases and logs

**Repository Visibility:** **Private** (recommended for healthcare applications)

**Estimated Upload Time:** 5-10 minutes (depending on connection speed)

---

**Last Updated:** 2025-10-30
