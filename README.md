<p align="right">
  <a href="README.md"><b>English</b></a> | <a href="README.vi.md"><b>Tiếng Việt</b></a>
</p>

# PhongTrọXanh.vn — Backend Service

> **Modular Monolith** backend for rental housing and roommate matching, with trust scoring, rental records and dynamic QR check-in. Rental records currently have no PDF contract generation or electronic signature provider.

Current MVP verification: [core flows and limitations](docs/backend-verification.md). Payment setup: [payOS](docs/payos-setup.md). Historical VNPay integration instructions are superseded by this payOS guide.

---

## Tech Stack

<p align="center">
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java_21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot_3.3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.3.4"></a>
  <a href="https://spring.io/projects/spring-security"><img src="https://img.shields.io/badge/Spring_Security_6-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security 6"></a>
  <a href="https://jwt.io/"><img src="https://img.shields.io/badge/JJWT_0.12.6-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JJWT"></a>
  <a href="https://maven.apache.org/"><img src="https://img.shields.io/badge/Maven_3.9+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven"></a>
</p>

<p align="center">
  <a href="https://www.postgresql.org/"><img src="https://img.shields.io/badge/PostgreSQL_18-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 18"></a>
  <a href="https://postgis.net/"><img src="https://img.shields.io/badge/PostGIS_3.6-2D8C4E?style=for-the-badge&logo=postgis&logoColor=white" alt="PostGIS"></a>
  <a href="https://redis.io/"><img src="https://img.shields.io/badge/Redis_7-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis 7"></a>
  <a href="https://www.docker.com/"><img src="https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Compose"></a>
  <a href="https://springdoc.org/"><img src="https://img.shields.io/badge/OpenAPI_3.0-85EA2D?style=for-the-badge&logo=openapiinitiative&logoColor=black" alt="OpenAPI"></a>
</p>

<p align="center">
  <a href="https://cloudinary.com/"><img src="https://img.shields.io/badge/Cloudinary-3448C5?style=for-the-badge&logo=cloudinary&logoColor=white" alt="Cloudinary"></a>
  <a href="https://payos.vn/"><img src="https://img.shields.io/badge/payOS-005BAA?style=for-the-badge&logo=payos&logoColor=white" alt="payOS"></a>
  <a href="https://www.brevo.com/"><img src="https://img.shields.io/badge/Brevo_REST_API-0B996E?style=for-the-badge&logo=brevo&logoColor=white" alt="Brevo"></a>
  <a href="https://goong.io/"><img src="https://img.shields.io/badge/Goong_Maps_API-FF5722?style=for-the-badge" alt="Goong Maps"></a>
  <a href="https://resilience4j.readme.io/"><img src="https://img.shields.io/badge/Resilience4j_2.2-FF9900?style=for-the-badge" alt="Resilience4j"></a>
</p>

---

## 1. Technical Overview & Architectural Decisions

| Aspect | Engineering Choice | Rationale |
| :--- | :--- | :--- |
| **Architecture Pattern** | **Modular Monolith** (12 Bounded Modules) | Domain logic encapsulated into independent bounded contexts inside a single deployable runtime, eliminating microservice networking overhead, serialized DTO overhead, and distributed transaction complexity at current operational scale. |
| **Layering Strategy** | **Pragmatic Layered Architecture** | Strict 4-tier flow: `presentation` $\to$ `application` $\to$ `domain` $\to$ `infrastructure`. Domain entities leverage JPA annotations directly for Hibernate dirty-checking velocity while preserving zero-framework pure domain engines (`MatchingEngine`). |
| **Database & GIS** | PostgreSQL 18 + PostGIS 3.4 Extension | Relational store for 25 normalized tables. Spatial coordinates use `GEOMETRY(Point, 4326)` indexed with `GIST` to execute high-throughput radius queries (`ST_DWithin`) for MapView. |
| **Cache & In-Memory Store** | Redis 7 (Alpine) | Serves 4 specialized workloads: (1) In-memory read caching (view counters, user profiles), (2) Token revocation blacklisting on logout, (3) Atomic counter operations for quota management, (4) STOMP WebSocket pub/sub message broker. |
| **Security & Session** | Stateless JWT (JJWT 0.12.6) + HttpOnly Cookies | 15-minute `AccessToken` kept in client in-memory state to mitigate XSS; 7-day `RefreshToken` secured in `HttpOnly, Secure, SameSite=Lax` cookies; multi-device session revocation coordinated via Redis session hashes. |
| **PII Cryptography** | AES-256-GCM (Hardware-Accelerated) | Citizen Identification Cards (CCCD/CMND) encrypted at rest in the database using authenticated AES-256-GCM with dynamic IVs. Decryption occurs strictly in-memory during KYC verification audits. |
| **Asset Storage** | Cloudinary Cloud Storage | Zero-disk-spooling direct `InputStream` byte streaming from client to Cloudinary under structured paths (`phongtroxanh/{rooms,avatars,kyc,dispute-evidence,misc}`); 100% compliant with PaaS ephemeral filesystems (Render / Railway). |
| **Payment Processing** | payOS (official Java SDK, signed webhook) | Subscription plans (Free, Pro Tenant, Landlord VIP) and consumable transactions signed with cryptographic hashes; Signed webhooks lock the payment row and credit benefits once per order. |
| **Transactional Email** | Brevo REST API v3 (HTTPS Port 443) | Outbound transactional emails (OTP, password resets) dispatched over TLS/HTTPS (Port 443), preventing connection timeouts caused by cloud platforms blocking legacy TCP SMTP ports (25, 465, 587). Local fallback to `console` logger supported. |
| **Geocoding & Spatial** | Goong Maps REST API | Fallback geocoding resolving Vietnamese natural language addresses into PostGIS coordinates when landlords post rooms without GPS coordinates; Redis-cached place autocomplete. |
| **Real-time Messaging** | STOMP over WebSocket (`/ws/chat`) | Full-duplex real-time chat with channel-level JWT interceptors, backed by Redis Pub/Sub for cross-connection event dispatching and PostgreSQL persistence for message history. |

---

## 2. System Architecture

```mermaid
flowchart TB
    subgraph Clients["Client Layer"]
        WebClient["SPA Web Client (React + Vite)"]
    end

    subgraph SecurityGateway["Security & Gateway Interceptors"]
        CORS["CORS Configuration"]
        JWTFilter["JwtAuthFilter (Bearer Token Validator)"]
        WSInterceptor["STOMP ChannelInterceptor (WebSocket Auth)"]
    end

    subgraph ApplicationCore["Spring Boot 3.3.4 (Modular Monolith)"]
        subgraph BusinessModules["12 Independent Business Modules"]
            AuthMod["auth"]
            UserMod["user"]
            RoomMod["room"]
            MatchingMod["matching"]
            SwapMod["swap"]
            RentalMod["rental"]
            ReviewMod["review"]
            ChatMod["chat"]
            MonetizationMod["monetization"]
            AdminMod["admin"]
            LocationMod["location"]
            MiscMod["misc"]
        end

        subgraph CommonPorts["Common Ports & Adapters (common.*)"]
            StoragePort["FileStoragePort<br/>(CloudinaryStorageAdapter)"]
            MailPort["EmailNotificationPort<br/>(BrevoEmailAdapter)"]
            PaymentPort["PayOsPaymentAdapter<br/>(official payOS SDK)"]
            GeoPort["GeocodingPort<br/>(GoongMapsAdapter)"]
            CryptoUtil["CryptoUtils (AES-256-GCM)"]
        end
    end

    subgraph DataInfrastructure["Data & Persistence"]
        PostgresDB[("PostgreSQL 18 + PostGIS<br/>(25 Tables, GIST Spatial Index)")]
        RedisCache[("Redis 7<br/>(Sessions, Token Blacklist, Pub/Sub)")]
    end

    subgraph ExternalAPIs["Third-Party Integrations"]
        CloudinaryAPI["Cloudinary Storage API"]
        BrevoAPI["Brevo Email REST API"]
        PayOSAPI["payOS Payment API"]
        GoongAPI["Goong Maps Geocoding API"]
    end

    WebClient -->|HTTP / REST| CORS --> JWTFilter --> BusinessModules
    WebClient -->|WebSocket| WSInterceptor --> ChatMod

    BusinessModules --> CommonPorts
    BusinessModules --> PostgresDB
    BusinessModules --> RedisCache

    StoragePort --> CloudinaryAPI
    MailPort --> BrevoAPI
    PaymentPort --> PayOSAPI
    GeoPort --> GoongAPI
```

---

## 3. Engineering Highlights

### 3.1. 8-Pillar Roommate Compatibility Engine
Rather than relying on arbitrary matching, roommate affinity is evaluated through a deterministic, weighted scoring algorithm (`MatchingEngine`) implemented as a pure domain service:

$$\text{CompatibilityScore} = \sum_{i=1}^{8} (W_i \times S_i) \quad \in [0, 100]$$

| Pillar | Weight ($W_i$) | Evaluation Logic |
| :--- | :---: | :--- |
| **Budget Alignment** | $20\%$ | Overlap percentage between reciprocal target rental budgets. |
| **Geographic Proximity** | $20\%$ | Distance decay function based on PostGIS coordinates of preferred commute zones. |
| **Circadian Rhythm** | $15\%$ | Sleep/wake schedule compatibility (Morning Lark vs Night Owl). |
| **Cleanliness Expectations** | $10\%$ | Alignment on chore distribution and cleanliness thresholds. |
| **Guest Policy** | $10\%$ | Agreement on visitors, parties, and overnight guests. |
| **Smoking / Vaping** | $10\%$ | **Hard Dealbreaker:** If either party is tobacco-intolerant and the other smokes $\to$ **Score is forced to $0$ immediately**. |
| **Noise Tolerance** | $10\%$ | Study habits, music volume, and nighttime noise expectations. |
| **Pet & Lifestyle Habits** | $5\%$ | Affinity regarding domestic pets and shared cooking preferences. |

### 3.2. Concurrency Controls & Race Condition Mitigation
* **Atomic Consumable Deduction (Swipes & Boosts):** To eliminate negative balance bugs caused by concurrent user requests, inventory deductions bypass application-level lock overhead and execute directly via atomic SQL:
  ```sql
  UPDATE user_consumables 
  SET swipes_left = swipes_left - 1 
  WHERE user_id = :userId AND swipes_left > 0;
  ```
  If affected rows equal `0`, the service instantly raises `AppException(CONSUMABLE_EXHAUSTED)`.
* **Idempotent Payment Webhook Handling:** payOS may replay signed notifications. The handler locks each order and only grants benefits while it is PENDING; a replay of SUCCESS is acknowledged without granting benefits again. An optional Idempotency-Key prevents duplicate checkout orders.
* **Room State Isolation:** Entity `Room` implements optimistic locking via `@Version` to resolve simultaneous booking or updating attempts.

### 3.3. Fraud-Resistant Dynamic QR Check-In
The check-in protocol eliminates ghost listings and fraudulent deposit claims:
1. Upon physical arrival at the rental property, the landlord triggers check-in generation.
2. The backend generates a **Single-Use Cryptographic Check-in Token** with a 5-minute TTL, stored in Redis.
3. The tenant scans the QR code via camera $\to$ submits to `POST /api/v1/rentals/{id}/check-in`.
4. The system validates the tenant's real-time GPS coordinates against the property's PostGIS spatial point (must be within 500 meters), verifies token validity, transitions the contract to `CHECKED_IN`, credits **+10 TrustScore** to both parties, and immediately evicts the token to prevent replay attacks.

### 3.4. PII Protection (AES-256-GCM)
* Citizen identification numbers (`id_card_number`) in `user_verifications` are stored encrypted with **AES-256-GCM** using authenticated additional data (AAD) and non-repeating initialization vectors (IV).
* Encryption keys are injected exclusively via environment variables. Database administrators and raw SQL backups cannot access plaintext identification data.

---

## 4. Bounded Contexts & Module Catalog

```
d:\EXE\backend\src\main\java\vn\phongtroxanh\backend
├── common/                             # Cross-Cutting Infrastructure & Ports
│   ├── config/                         # Redis, OpenAPI, WebMvc Configurations
│   ├── dto/                            # Uniform ApiResponse envelope & ProblemDetail
│   ├── entity/                         # BaseEntity (UUID, Timestamps)
│   ├── exception/                      # AppException, ErrorCode, GlobalExceptionHandler
│   ├── location/                       # GeocodingPort, GoongMapsAdapter
│   ├── mail/                           # EmailNotificationPort, BrevoEmailAdapter
│   ├── payment/                        # PayOsPaymentAdapter (official SDK)
│   ├── repository/                     # Base persistence interfaces
│   ├── security/                       # JwtTokenProvider, JwtAuthFilter, UserPrincipal
│   ├── storage/                        # FileStoragePort, CloudinaryStorageAdapter
│   └── util/                           # AES-256 CryptoUtils, Constants
└── modules/                            # 12 Independent Domain Modules
    ├── admin/                          # System KPIs, KYC Audit, Moderation, Dispute Arbitration
    ├── auth/                           # Registration, Login, Token Rotation, OTP, Google OAuth2
    ├── chat/                           # REST Chat History + Real-time STOMP WebSocket (/ws/chat)
    ├── location/                       # Vietnamese Address Autocomplete, Geocoding & Reverse Geocoding
    ├── matching/                       # Discovery Feed, Swipe Interactions, Mutual Match Detection
    ├── misc/                           # Public Landing Statistics
    ├── monetization/                   # Subscription Plans, payOS Checkout, Signed Webhook
    ├── notification/                   # In-App Notifications, FCM Token Registry
    ├── rental/                         # Contracts, Dynamic Check-in QR Token, Replay Protection
    ├── review/                         # Two-Way Reviews, Evidence Uploads, Dispute Management
    ├── room/                           # Room CRUD, PostGIS Radius Search (ST_DWithin), Boost Management
    ├── swap/                           # Room Swapping & Subleasing Marketplace, Landlord Approvals
    └── user/                           # User Profiles, Avatar Uploads, TrustScore, KYC Submission
```

### API Endpoint Distribution Across Modules

| Module | Base Route | Endpoints | Domain Scope |
| :--- | :--- | :---: | :--- |
| **Auth & Onboarding** | `/api/v1/auth` | **11** | Registration, Authentication, OTP Dispatch, Token Rotation, Google OAuth2, Multi-step Onboarding. |
| **User & KYC** | `/api/v1/users` | **12** | Profile management, Avatar uploads, Matching preferences matrix, TrustScore details, AES-256 KYC submission. |
| **Room & Spatial** | `/api/v1/rooms` | **15** | Room search, PostGIS MapView (`ST_DWithin`), Side-by-side comparison, Bookmarking, Room CRUD, Boost 7 days. |
| **Matching Engine** | `/api/v1/matching` | **8** | Discovery feed, Card swiping (LIKE/DISLIKE/SUPER_LIKE), Mutual match detection, Match unlinking. |
| **Room Swap** | `/api/v1/swaps` | **5** | Lease transfer listings, Search listings, Proposal dispatch, Landlord arbitration and approval. |
| **Rentals & QR** | `/api/v1/rentals` | **7** | Rental requests, Electronic contracts, Dynamic single-use check-in QR generation, Physical check-in validation. |
| **Reviews & Disputes** | `/api/v1/reviews` | **8** | Two-way rental evaluations, Dispute evidence attachment, Public responses, Dispute claims. |
| **Real-time Chat** | `/api/v1/chat` + WS | **5 + 1 WS** | Conversation registry, Historical message retrieval, REST dispatch & STOMP messaging via `/ws/chat`. |
| **Monetization** | `/api/v1/monetization` | **7** | Tiered plan catalogue, payOS checkout URL generation, signed webhook handling, Transaction logs. |
| **Admin Control** | `/api/v1/admin` | **12** | Executive KPI dashboard, KYC CCCD decryption audit, User moderation, Room verification, Dispute resolution. |
| **Location Services** | `/api/v1/locations` | **3** | Vietnamese address autocomplete (24h Redis cache), Forward & Reverse Geocoding. |
| **Public Statistics** | `/api/v1/misc` | **1** | Public landing page metrics (ac## 5. Database Architecture & Migrations

The persistence layer is managed via **Flyway Migrations** and consists of **25 relational tables**, all utilizing `UUID v4` (`gen_random_uuid()`) primary keys to prevent enumeration attacks and support distributed scalability:

* **Identity & Security:** `users`, `user_matching_profiles`, `user_trust_scores`, `user_verifications`, `user_settings`.
* **Real Estate & Spatial:** `rooms` (includes `location geometry(Point, 4326)`), `room_images`, `room_fees`, `saved_rooms`, `room_swipes`.
* **Social & Matching:** `user_swipes`, `roommate_matches`, `room_swaps`, `swap_requests`.
* **Contracts & Quality:** `rental_contracts`, `rental_reviews`, `review_evidence`, `review_disputes`.
* **Billing & Monetization:** `subscription_plans`, `user_subscriptions`, `user_consumables`, `payment_transactions`, `payos_order_code_seq`.
* **Communication:** `chat_conversations`, `chat_messages`, `notifications`.

### Consolidated Migration Scripts (`src/main/resources/db/migration/`)
1. **`V1__init_schema.sql`**: Complete database schema DDL, PostGIS spatial extension, custom PostgreSQL ENUMs, and foreign key relationships.
2. **`V2__seed_system_data.sql`**: Enterprise seed data for subscription packages (Free, Pro, VIP, Landlord packages with real-market pricing), room categories, and the initial system administrator account (`admin@phongtroxanh.vn`).
3. **`V3__postgis_spatial_and_indexes.sql`**: High-throughput spatial GiST indexes for geolocation radius queries (`ST_DWithin`), auto-updating geometry triggers, and payment transaction uniqueness constraints.

---

## 6. Local Setup & Execution Guide

### Prerequisites
* **Java:** OpenJDK 21 LTS or newer.
* **Build Tool:** Apache Maven 3.9+ (or use the bundled `mvnw.cmd` / `mvnw`).
* **Docker & Docker Compose:** For running PostgreSQL (PostGIS) and Redis locally.

### Step 1: Start PostgreSQL and Redis via Docker Compose
From the backend repository root:
```bash
docker compose up -d
```
Verify container health:
```bash
docker compose ps
```
* Container `phongtroxanh-postgres` runs on port `5433` (pre-configured with PostGIS extension). Flyway automatically migrates V1-V3 schema on application boot.
* Container `phongtroxanh-redis` runs on port `6379`.

### Step 2: Configure Environment Variables (.env)
Copy the template configuration:
```bash
cp .env.example .env
```
Ensure required parameters are populated:
```properties
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
DB_HOST=localhost
DB_PORT=5433
DB_NAME=phongtroxanh_db
DB_USER=postgres
DB_PASSWORD=postgrespassword

REDIS_HOST=localhost
REDIS_PORT=6379

# JWT HS512 Secret (Minimum 64 characters)
JWT_SECRET=4c6f6e675f616e645f73757065725f7365637265745f6a77745f6b65795f666f725f70686f6e6774726f78616e685f766e5f68733531325f73656375726974795f746f6b656e

# payOS Gateway (VietQR Banking)
PAYOS_CLIENT_ID=your_client_id
PAYOS_API_KEY=your_api_key
PAYOS_CHECKSUM_KEY=your_checksum_key

# Email (Select 'console' for local testing or 'brevo' with BREVO_API_KEY)
MAIL_PROVIDER=console

# Cloudinary Credentials
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# Goong Maps API
GOONG_API_KEY=your_goong_api_key
```

### Step 3: Build & Run Automated Test Suites
Execute full compilation and automated unit/integration test suites:
```bash
# Windows
.\mvnw.cmd clean compile
.\mvnw.cmd test

# Linux / macOS
./mvnw clean compile
./mvnw test
```

### Step 4: Launch the Backend Server
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```
The application server listens at: `http://localhost:8080`

### Step 5: Access Documentation & Observability
* **Swagger UI (OpenAPI 3):** `http://localhost:8080/swagger-ui.html`
* **OpenAPI Specification (JSON):** `http://localhost:8080/v3/api-docs`
* **Spring Actuator Health:** `http://localhost:8080/actuator/health`

---

## 7. Testing & Quality Assurance

The backend includes **22 comprehensive automated test suites** located in `src/test/java/vn/phongtroxanh/backend/`:

* **Payment & PayOS Integration:**
  - `PayOsSignatureTest`: Validates HMAC SHA-256 signature calculations and webhook payload integrity.
  - `PayOsFlowIntegrationTest`: End-to-end checkout URL generation, webhook idempotency, and status updates.
  - `PaymentServiceTest`: Unit validation for subscription plans and transaction auditing.
* **Concurrency & Race Conditions:**
  - `ConcurrentMatchingDatabaseTest`: Multi-threaded roommate swipe simulations and mutual match detection.
  - `DailySwipeQuotaDatabaseTest`: Atomic SQL counter verification preventing negative quota balances.
  - `AuthRaceIntegrationTest`: Prevents race conditions during simultaneous user registration.
* **Security & Access Control:**
  - `AuthSecurityRegressionTest`: Token rotation, invalid token rejection, and brute-force mitigation.
  - `ChatSecurityRegressionTest`: Channel-level STOMP WebSocket authorization and cross-tenant isolation.
  - `AccessSecurityRegressionTest`: Role-based authorization (`ROLE_TENANT`, `ROLE_LANDLORD`, `ROLE_ADMIN`).
* **Core Domains & KYC Auditing:**
  - `AdminIntegrityTest`: Platform analytics aggregation and paged KYC verification queue.
  - `RoomServiceTest` & `RoomRequestValidationTest`: PostGIS spatial bounds, pricing validation, and image duplicate detection.
  - `RentalServiceTest` & `RoomSwapServiceTest`: Lifecycle transitions, QR check-in token generation, and lease transfer arbitration.
  - `ReviewIntegrityTest`: Two-way rating calculation and dispute mediation workflows.

Execute all suites with fresh database fixtures:
```powershell
mvn clean test
```

---

## 8. Technical Documentation Index

Engineering specifications reside in [`docs/`](./docs/):

* [`system-specification.md`](./docs/system-specification.md): Comprehensive functional requirements and endpoint catalog.
* [`coding-rules.md`](./docs/coding-rules.md): 26 chapters covering architecture rules, naming standards, and Definition of Done.
* [`architecture-decisions.md`](./docs/architecture-decisions.md): 11 Architecture Decision Records (ADR-001 through ADR-011).
* [`payos-setup.md`](./docs/payos-setup.md): Complete integration guide for PayOS VietQR payments and webhook handling.
* [`third-party-integrations.md`](./docs/third-party-integrations.md): Configuration guide for Cloudinary, Brevo, Goong Maps, and payOS.
* [`backend-verification.md`](docs/backend-verification.md): Verified flows, test evidence, and system validation reports.
* [codebase-overview.md](./docs/codebase-overview.md): In-depth system architecture guide for incoming engineers.
