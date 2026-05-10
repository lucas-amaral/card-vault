# Card Vault API

Secure REST API for registering and looking up credit/debit card numbers (PANs).  
Built with **Java 21**, **Spring Boot**, **MySQL 8**, **Testcontainers** and **Gradle**.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Security Model](#security-model)
- [Prerequisites](#prerequisites)
- [Running Locally — Docker Compose (Recommended)](#running-locally--docker-compose-recommended)
- [Running Locally — Without Docker](#running-locally--without-docker)
- [Running Tests](#running-tests)
- [API Reference](#api-reference)
- [Environment Variables](#environment-variables)
- [Project Structure](#project-structure)
- [Scalability Considerations](#scalability-considerations)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   Client (HTTPS)                    │
└──────────────────────┬──────────────────────────────┘
                       │ Bearer JWT
┌──────────────────────▼──────────────────────────────┐
│  Spring Security Filter Chain                       │
│  ├─ AuditLoggingFilter  (audits selected req/resp)  │
│  └─ JwtAuthenticationFilter  (validates token)      │
├─────────────────────────────────────────────────────┤
│  Controllers                                        │
│  ├─ POST /api/v1/auth/login                         │
│  ├─ POST /api/v1/cards          (single card)       │
│  ├─ POST /api/v1/cards/batch    (TXT file upload)   │
│  ├─ GET  /api/v1/cards/batch/{jobId}/status         │
│  └─ GET  /api/v1/cards/{pan}    (lookup)            │
├─────────────────────────────────────────────────────┤
│  Services                                           │
│  ├─ CardService  (register / lookup)                │
│  ├─ BatchProcessingService  (async batch import)    │
│  ├─ AuthService  (JWT issuance)                     │
│  └─ AuditLogService  (async persistence)            │
├─────────────────────────────────────────────────────┤
│  Security Utilities                                 │
│  ├─ CardEncryptionUtil  (AES-256-GCM + HMAC-SHA256) │
│  └─ PayloadEncryptionUtil  (audit payload AES-GCM)  │
├─────────────────────────────────────────────────────┤
│  MySQL 8  (Flyway migrations)                       │
│  ├─ users        (bcrypt passwords)                 │
│  ├─ cards        (AES-encrypted PAN, HMAC hash)     │
│  ├─ batch_jobs                                      │
│  └─ audit_logs   (encrypted bodies)                 │
└─────────────────────────────────────────────────────┘
```

---

## Security Model

| Layer | Mechanism |
|---|---|
| Authentication | JWT signed with HMAC, 24 h expiry |
| Card number at rest | AES-256-GCM (random IV per record) |
| Card lookup | HMAC-SHA256 hash with indexed lookup |
| Passwords | BCrypt (strength 12) |
| Audit | Sensitive endpoints are redacted, body columns are AES-GCM encrypted, Swagger/OpenAPI/internal calls are skipped |

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Java | 21+ | Run / build the application |
| Docker | Latest | Run MySQL + the app itself |
| Docker Compose | v2+ | Orchestrate local environment |
| Git | Any | Clone the repository |

> **Docker is the only hard requirement to run the full stack locally.**  
> Java is only needed if you want to build or run without Docker.

---

## Running Locally — Docker Compose (Recommended)

This is the fastest way to get the API running — zero configuration needed.

### 1. Clone the repository

```bash
git clone https://github.com/lucas-amaral/card-vault.git
cd card-vault
```

### 2. (Optional but recommended) Set your own secrets

Create a local `.env` file and replace the placeholder values:

```env
DB_USERNAME=cardvault
DB_PASSWORD=cardvault123
JWT_SECRET=replace_with_at_least_32_random_chars_here
CARD_ENCRYPTION_KEY=replace_with_exactly_32_chars!!!
AUDIT_ENCRYPTION_KEY=replace_with_32_or_more_random_chars_here
```

Docker Compose always connects the app container to MySQL through `db:3306`. Use a `localhost` JDBC URL only when running the app directly from your IDE or Gradle.

> If you skip this step, the app will start with **development defaults**, which are fine for local testing but must be changed for any real environment.

### 3. Start everything

```bash
docker compose up --build
```

Docker Compose will:
1. Pull and start a MySQL 8 container
2. Wait until MySQL is healthy
3. Build the Spring Boot application image
4. Start the API (Flyway runs DB migrations automatically on startup)

### 4. Verify it's running

```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Swagger UI:** http://localhost:8080/swagger-ui.html

### Default credentials (seeded by Flyway)

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | `ROLE_ADMIN` |

### Stop the application

```bash
docker compose down          # Stop containers (data preserved)
docker compose down -v       # Stop and delete the database volume
```

---

## Running Locally — Without Docker

Use this approach if you want to run the application directly with Gradle (e.g., for active development).

### 1. Start only the database

```bash
docker compose up db -d
```

### 2. Set environment variables

```bash
export DB_URL=jdbc:mysql://localhost:3306/cardvault?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
export DB_USERNAME=cardvault
export DB_PASSWORD=cardvault123
export JWT_SECRET=a_local_dev_secret_at_least_32_chars_long
export CARD_ENCRYPTION_KEY=localdevkey1234567890123456789012
export AUDIT_ENCRYPTION_KEY=localdevkey1234567890123456789012
```

### 3. Run the application

```bash
./gradlew bootRun
```

The API will be available at http://localhost:8080.

---

## Running Tests

The project has three test categories:

### Unit tests (no Docker required)

```bash
./gradlew test
```

### Integration tests (requires Docker for Testcontainers)

Testcontainers spins up a real MySQL instance automatically during the test run.

```bash
./gradlew integrationTest
```

### All tests

```bash
./gradlew test integrationTest
```

### View reports

```bash
# Test results
open build/reports/tests/test/index.html
```

---

## API Reference

### Authentication

#### `POST /api/v1/auth/login`

```json
// Request
{
  "username": "admin",
  "password": "Admin@123"
}

// Response 200
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "<JWT>",
    "tokenType": "Bearer",
    "expiresIn": 86400000
  },
  "timestamp": "2024-05-08T10:00:00"
}
```

Use the `accessToken` in all subsequent requests:

```
Authorization: Bearer <accessToken>
```

---

### Register a Single Card

#### `POST /api/v1/cards`

```json
// Request
{
  "cardNumber": "4456897999999999"
}

// Response 201 — new card registered
{
  "success": true,
  "message": "Card registered successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "batchName": null,
    "createdAt": "2024-05-08T10:01:00"
  }
}

// Response 200 — card already exists
{
  "success": true,
  "message": "Card already registered",
  "data": { "id": "550e8400-e29b-41d4-a716-446655440000", ... }
}
```

---

### Batch Upload (TXT File)

#### `POST /api/v1/cards/batch` — `multipart/form-data`

```bash
curl -X POST https://<your-host>/api/v1/cards/batch \
  -H "Authorization: Bearer <token>" \
  -F "file=@DESAFIO-HYPERATIVA.txt"
```

```json
// Response 202
{
  "success": true,
  "message": "Batch job accepted. Poll /api/v1/cards/batch/550e8400-e29b-41d4-a716-446655440000/status for progress.",
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "filename": "DESAFIO-HYPERATIVA.txt",
    "totalParsed": null,
    "inserted": null,
    "skipped": null,
    "error": null,
    "createdAt": "2024-05-08T10:01:00",
    "updatedAt": "2024-05-08T10:01:00"
  }
}
```

#### `GET /api/v1/cards/batch/{jobId}/status`

```json
// Response 200
{
  "success": true,
  "message": "Job status retrieved",
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "DONE",
    "filename": "DESAFIO-HYPERATIVA.txt",
    "totalParsed": 9,
    "inserted": 7,
    "skipped": 2,
    "error": null,
    "createdAt": "2024-05-08T10:01:00",
    "updatedAt": "2024-05-08T10:01:03"
  }
}
```

#### TXT File Format

```
DESAFIO-HYPERATIVA 20180524LOTE0001000010   ← header
C1 4456897999999999                          ← card line (C + seq + space + PAN)
C2 4456897922969999
...
LOTE0001000010                               ← footer
```

- Column 01 = `C` (card line identifier)
- Columns 02–07 = sequence number in batch (left-padded)
- Columns 08–26 = card number (left-aligned, space-padded)
- Invalid card numbers (non-numeric, < 13 or > 19 digits) are skipped

---

### Look Up a Card

#### `GET /api/v1/cards/{cardNumber}`

```bash
curl https://<your-host>/api/v1/cards/4456897999999999 \
  -H "Authorization: Bearer <token>"
```

```json
// Response 200 — found
{
  "success": true,
  "message": "Card found",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "batchName": "LOTE0001",
    "createdAt": "2024-05-08T10:01:00"
  }
}

// Response 404 — not found
{
  "success": false,
  "message": "Card not found"
}
```

---

## Environment Variables

| Variable | Default (dev only) | Description |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/cardvault...` | JDBC connection URL when running outside Docker; Docker Compose sets this to `db:3306` |
| `DB_USERNAME` | `root` | Database username; Docker Compose uses `cardvault` |
| `DB_PASSWORD` | `root` | Database password; Docker Compose uses `cardvault123` |
| `JWT_SECRET` | *(insecure dev default)* | JWT HMAC signing key — **must be changed in production** |
| `JWT_EXPIRATION` | `86400000` | Token validity in milliseconds (24 h) |
| `CARD_ENCRYPTION_KEY` | *(insecure dev default)* | AES-256 key — must be **exactly 32 characters** — **must be changed in production** |
| `AUDIT_ENCRYPTION_KEY` | Falls back to `CARD_ENCRYPTION_KEY` | Key used to encrypt `audit_logs.request_body` and `audit_logs.response_body`; use a separate production secret |

---

## Project Structure

```
src/
├── main/java/br/com/amaral/cardvault/
│   ├── config/          # Security, Async, OpenAPI configuration
│   ├── controllers/     # REST endpoints
│   ├── entities/dto/    # Request / Response records
│   ├── entities/        # JPA entities
│   ├── exceptions/      # Global error handling
│   ├── filters/         # JWT + Audit logging filters
│   ├── repositories/    # Spring Data JPA interfaces
│   ├── services/        # Business logic (interfaces + implementations)
│   └── utils/           # Card + audit payload encryption
├── main/resources/
│   ├── application.yml
│   └── db/migration/    # Flyway SQL migration scripts (V1–V4)
└── test/java/...        # Unit tests + Testcontainers integration tests
```

---

## Scalability Considerations

- **Lookup by hash** — `cards.card_hash` is indexed; existence checks are O(1) regardless of table size.
- **Batch inserts** — each card in a batch is checked for duplicates then inserted individually. For very large files (millions of rows), future consideration can be a bulk-upsert strategy or a message queue.
- **Stateless JWT** — the application is horizontally scalable with no shared session state.
- **Async audit logging** — audit log persistence is non-blocking and never delays the main request.
- **Connection pooling** — HikariCP (Spring Boot default) is pre-configured for efficient DB connection reuse.
