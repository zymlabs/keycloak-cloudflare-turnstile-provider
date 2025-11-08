# Contributing to Keycloak Cloudflare Turnstile Provider

Thank you for your interest in contributing! This document provides guidelines and instructions for contributing to this project.

## Development Workflow

We use GitFlow branching strategy with semantic versioning via GitVersion.

### Branch Structure

- **master** - Production-ready code, tagged with version numbers (e.g., `1.0.0`)
- **develop** - Integration branch for features, tagged with pre-release versions (e.g., `1.1.0-alpha.5`)
- **feature/** - Feature branches (e.g., `feature/add-widget-customization`)
- **hotfix/** - Critical bug fixes for production (e.g., `hotfix/fix-timeout-issue`)
- **release/** - Release preparation branches (e.g., `release/1.1.0`)

### Creating Feature Branches

```bash
# Start from develop
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/your-feature-name

# Make changes, commit with conventional commits (see below)
git add .
git commit -m "feat: add widget size configuration"

# Push to origin
git push origin feature/your-feature-name

# Create pull request to develop
```

### Conventional Commits

We use [Conventional Commits](https://www.conventionalcommits.org/) for clear and consistent commit messages.

**Format:**
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, no logic change)
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `chore`: Maintenance tasks, build changes

**Examples:**
```bash
feat(authenticator): add support for custom widget sizes
fix(service): handle network timeout errors gracefully
docs(readme): update installation instructions
test(entity): add tests for JPA entity validation
chore(deps): update Keycloak to 24.0.5
```

## Setting Up Development Environment

### Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Docker and Docker Compose (for local testing)
- Git

### Initial Setup

```bash
# Clone the repository
git clone https://github.com/zymlabs/keycloak-cloudflare-turnstile.git
cd keycloak-cloudflare-turnstile

# Build the project
mvn clean package

# Run tests
mvn test

# Start local Keycloak with Docker Compose
docker-compose up -d

# Access Keycloak at http://localhost:8080
# Admin credentials: admin / admin
```

### Project Structure

```
keycloak-cloudflare-turnstile-provider/
├── src/
│   ├── main/
│   │   ├── java/com/zymlabs/keycloak/cloudflare/turnstileprovider/
│   │   │   ├── CloudflareTurnstileAuthenticator.java
│   │   │   ├── CloudflareTurnstileAuthenticatorFactory.java
│   │   │   ├── CloudflareTurnstileService.java
│   │   │   ├── CloudflareTurnstileCheckEntity.java
│   │   │   ├── CloudflareTurnstileJpaEntityProvider.java
│   │   │   ├── CloudflareTurnstileJpaEntityProviderFactory.java
│   │   │   └── IpAddressUtils.java
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── services/ (SPI registration)
│   │       │   └── cloudflare-turnstile-changelog.xml
│   │       └── theme-resources/
│   │           ├── messages/
│   │           └── templates/
│   └── test/java/com/zymlabs/keycloak/cloudflare/turnstileprovider/
├── docs/
├── examples/
├── pom.xml
├── docker-compose.yml
└── GitVersion.yml
```

## Code Standards

### Java Code Style

- Use 4 spaces for indentation (no tabs)
- Follow standard Java naming conventions
- Maximum line length: 120 characters
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs

### Example:

```java
/**
 * Verifies a Turnstile token with Cloudflare.
 *
 * @param token The cf-turnstile-response token from the client
 * @param remoteIp The user's IP address (optional but recommended)
 * @return Verification result containing success status and details
 */
public TurnstileVerificationResult verify(String token, String remoteIp) {
    // Implementation
}
```

### Testing Standards

- Write unit tests for all new functionality
- Aim for high test coverage (>80%)
- Use JUnit 5 and AssertJ for assertions
- Use `@DisplayName` for readable test descriptions
- Group related tests with nested classes or comments

### Example:

```java
@Test
@DisplayName("Should block authentication when IP is in blocklist")
void testIpBlocklist() {
    assertThat(IpAddressUtils.isIpInList("192.168.1.100", "192.168.1.0/24"))
            .isTrue();
}
```

## Testing

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=CloudflareTurnstileServiceTest

# Run tests with coverage
mvn clean test jacoco:report
```

### Integration Testing

For integration testing with real Cloudflare Turnstile:

1. Create a test site in Cloudflare Dashboard
2. Set environment variables:
   ```bash
   export TURNSTILE_SITE_KEY=your_site_key
   export TURNSTILE_SECRET_KEY=your_secret_key
   ```
3. Run integration tests (if available):
   ```bash
   mvn verify -P integration-tests
   ```

## Building and Packaging

### Build JAR

```bash
# Clean build
mvn clean package

# Skip tests (not recommended)
mvn clean package -DskipTests

# The JAR will be in target/zymlabs-cloudflare-turnstile-provider.jar
```

### Versioning

Versions are automatically calculated by GitVersion based on branch and commits:

- **master branch**: `1.0.0`, `1.0.1`, `1.1.0`
- **develop branch**: `1.1.0-alpha.1`, `1.1.0-alpha.2`
- **feature branches**: `1.1.0-feature-name.1`
- **hotfix branches**: `1.0.1-beta.1`

To see the calculated version:

```bash
# Install GitVersion
# See: https://gitversion.net/docs/

# Show version info
gitversion
```

## Pull Request Process

1. **Create a feature branch** from `develop`
2. **Make your changes** following code standards
3. **Write tests** for new functionality
4. **Update documentation** if needed
5. **Commit** with conventional commit messages
6. **Push** to your fork
7. **Create a Pull Request** to `develop`

### PR Checklist

- [ ] Code follows project style guidelines
- [ ] All tests pass locally
- [ ] New tests added for new functionality
- [ ] Documentation updated (if applicable)
- [ ] Commit messages follow conventional commits
- [ ] No merge conflicts with develop
- [ ] PR description clearly explains the changes

### PR Template

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update

## Testing
How has this been tested?

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have performed a self-review
- [ ] I have commented my code where necessary
- [ ] I have updated the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix/feature works
- [ ] New and existing tests pass locally
```

## Release Process

Releases are managed through GitHub Actions and GitVersion.

### Creating a Release

1. **Merge features** to `develop`
2. **Create release branch** from `develop`:
   ```bash
   git checkout develop
   git pull
   git checkout -b release/1.1.0
   ```
3. **Update version** in documentation if needed
4. **Create PR** from `release/1.1.0` to `master`
5. **After merge**, GitHub Actions will:
   - Calculate version with GitVersion
   - Build the JAR
   - Create GitHub release
   - Tag the commit

### Hotfix Process

For critical production fixes:

1. **Create hotfix branch** from `master`:
   ```bash
   git checkout master
   git pull
   git checkout -b hotfix/fix-critical-bug
   ```
2. **Fix the issue** and commit
3. **Create PR** to `master`
4. **After merge**, merge `master` back to `develop`

## CI/CD Pipeline

GitHub Actions automatically:

1. **On every push**:
   - Runs tests
   - Checks code compilation

2. **On PR to develop/master**:
   - Runs full test suite
   - Generates coverage report
   - Validates commit messages

3. **On merge to master**:
   - Calculates version
   - Builds production JAR
   - Creates GitHub release with artifacts
   - Tags the commit

## Getting Help

- **Questions**: Open an issue with the `question` label
- **Bugs**: Open an issue with the `bug` label and provide:
  - Keycloak version
  - Provider version
  - Steps to reproduce
  - Expected vs actual behavior
  - Relevant logs

## Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Focus on constructive feedback
- Assume good intentions

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
