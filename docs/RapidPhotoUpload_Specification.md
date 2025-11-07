# RapidPhotoUpload: Complete Project Specification

## Project Overview

**Project Name:** RapidPhotoUpload  
**Duration:** 5 days  
**Team Size:** 1 full-stack developer  
**Architecture:** Monorepo with Spring Boot backend, React web, React Native mobile, AWS infrastructure  
**Deployment Target:** AWS (ALB, ECS Fargate, RDS PostgreSQL, S3)  

**Core Requirements:**
- Support 100 concurrent photo uploads per user session
- Complete uploads within 90 seconds on standard broadband (25 Mbps)
- Maintain responsive UI during uploads on both web and mobile
- Implement DDD/CQRS/VSA architecture patterns
- Direct S3 uploads via pre-signed URLs
- Real-time status updates via polling
- JWT authentication

---

## Technology Stack

### Backend
- **Language:** Java 25+
- **Framework:** Spring Boot 3.5.7+
- **Database:** PostgreSQL 17.6
- **ORM:** Spring Data JPA
- **Migration:** Flyway
- **Build:** Maven
- **Testing:** JUnit 5, Testcontainers, LocalStack

### Web Frontend
- **Language:** TypeScript 5.9.3
- **Framework:** React 19.2
- **Build Tool:** Vite 7.2.2
- **HTTP Client:** Axios
- **State Management:** React Hooks

### Mobile Frontend
- **Language:** TypeScript 5.9.3
- **Framework:** React Native 0.82+
- **File Handling:** Expo File System
- **Image Picker:** Expo Image Picker

### Infrastructure
- **IaC:** Terraform 1.13+
- **Provider:** AWS Provider 6.19+
- **Services:** VPC, ALB, ECS Fargate, RDS, S3, IAM, CloudWatch

---

## Repository Structure

```
rapid-photo-upload/
├── backend/
│   ├── src/main/
│   │   ├── java/com/demo/photoupload/
│   │   │   ├── domain/              # Pure domain objects
│   │   │   ├── application/         # CQRS handlers
│   │   │   ├── infrastructure/      # JPA, S3, adapters
│   │   │   └── web/                 # REST controllers
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/        # Flyway SQL
│   ├── src/test/
│   ├── pom.xml
│   └── Dockerfile
│
├── web/
│   ├── src/
│   │   ├── components/
│   │   ├── services/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── App.tsx
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
│
├── mobile/
│   ├── src/
│   │   ├── screens/
│   │   ├── components/
│   │   ├── services/
│   │   ├── hooks/
│   │   └── App.tsx
│   ├── package.json
│   ├── tsconfig.json
│   └── app.json
│
└── terraform/
    ├── main.tf
    ├── versions.tf
    ├── variables.tf
    ├── network.tf
    ├── data.tf
    └── compute.tf
```

---

## Database Schema

### Table: users
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

### Table: upload_jobs
```sql
CREATE TABLE upload_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'PARTIAL_FAILURE')),
    total_photos INT NOT NULL,
    completed_photos INT DEFAULT 0,
    failed_photos INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_upload_jobs_user_status ON upload_jobs(user_id, status, created_at DESC);
```

### Table: photos
```sql
CREATE TABLE photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES upload_jobs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100),
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1000) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'UPLOADING', 'COMPLETED', 'FAILED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_photos_job_status ON photos(job_id, status);
CREATE INDEX idx_photos_user_status ON photos(user_id, status, created_at DESC);
CREATE INDEX idx_photos_s3_key ON photos(s3_key);
```

### Table: photo_tags
```sql
CREATE TABLE photo_tags (
    id BIGSERIAL PRIMARY KEY,
    photo_id UUID NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(photo_id, tag)
);

CREATE INDEX idx_photo_tags_photo ON photo_tags(photo_id);
CREATE INDEX idx_photo_tags_tag ON photo_tags(tag);
```

---

## API Specification

### Base URL
```
Production: http://<alb-dns-name>
Development: http://localhost:8080
```

### Authentication
All endpoints except `/auth/register` and `/auth/login` require JWT authentication.

**Header:**
```
Authorization: Bearer <jwt_token>
```

---

### Authentication Endpoints

#### POST /api/auth/register
Register a new user.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Response:** `201 Created`
```json
{
  "userId": 1,
  "email": "user@example.com"
}
```

**Errors:**
- `400 Bad Request`: Invalid email or weak password
- `409 Conflict`: Email already exists

---

#### POST /api/auth/login
Authenticate and receive JWT token.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "userId": 1,
  "email": "user@example.com",
  "expiresIn": 3600
}
```

**Errors:**
- `401 Unauthorized`: Invalid credentials

---

### Upload Endpoints

#### POST /api/upload/initialize
Initialize a batch upload and receive pre-signed S3 URLs.

**Request:**
```json
{
  "files": [
    {
      "filename": "photo1.jpg",
      "size": 2048000,
      "mimeType": "image/jpeg"
    },
    {
      "filename": "photo2.jpg",
      "size": 1500000,
      "mimeType": "image/jpeg"
    }
  ]
}
```

**Response:** `200 OK`
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "photos": [
    {
      "photoId": "123e4567-e89b-12d3-a456-426614174000",
      "filename": "photo1.jpg",
      "uploadUrl": "https://bucket.s3.amazonaws.com/photos/123e4567.../photo1.jpg?X-Amz-Signature=..."
    },
    {
      "photoId": "223e4567-e89b-12d3-a456-426614174001",
      "filename": "photo2.jpg",
      "uploadUrl": "https://bucket.s3.amazonaws.com/photos/223e4567.../photo2.jpg?X-Amz-Signature=..."
    }
  ]
}
```

**Business Logic:**
1. Create `UploadJob` aggregate with status `IN_PROGRESS`
2. Create `Photo` entities for each file with status `PENDING`
3. Batch insert into database (efficient for 100 items)
4. Generate S3 pre-signed PUT URLs (1 hour expiry)
5. Return job ID and upload URLs

**Errors:**
- `400 Bad Request`: Invalid file metadata or exceeds limits
- `401 Unauthorized`: Missing/invalid JWT

---

#### PUT /api/upload/photos/{photoId}/start
Mark photo as upload started (transition to UPLOADING).

**Request:** Empty body

**Response:** `200 OK`

**Business Logic:**
1. Fetch photo by ID
2. Verify user ownership
3. Validate state transition (PENDING → UPLOADING only)
4. Update photo status to `UPLOADING`
5. Set `started_at` timestamp

**Errors:**
- `404 Not Found`: Photo doesn't exist
- `403 Forbidden`: User doesn't own this photo
- `400 Bad Request`: Invalid state transition

---

#### PUT /api/upload/photos/{photoId}/complete
Mark photo as upload completed (transition to COMPLETED).

**Request:** Empty body

**Response:** `200 OK`

**Business Logic:**
1. Fetch photo by ID
2. Verify user ownership
3. Validate state transition (UPLOADING → COMPLETED only)
4. Update photo status to `COMPLETED`
5. Set `completed_at` timestamp
6. Fetch parent `UploadJob`
7. Check if all photos in job are complete
8. If complete: update job status to `COMPLETED` or `PARTIAL_FAILURE`
9. Set job `completed_at` timestamp if done

**Errors:**
- `404 Not Found`: Photo doesn't exist
- `403 Forbidden`: User doesn't own this photo
- `400 Bad Request`: Invalid state transition

---

#### PUT /api/upload/photos/{photoId}/fail
Mark photo as failed.

**Request:**
```json
{
  "errorMessage": "Network timeout"
}
```

**Response:** `200 OK`

**Business Logic:**
1. Fetch photo by ID
2. Verify user ownership
3. Update photo status to `FAILED`
4. Update parent job's `failed_photos` counter
5. Check job completion status

**Errors:**
- `404 Not Found`: Photo doesn't exist
- `403 Forbidden`: User doesn't own this photo

---

#### GET /api/upload/jobs/{jobId}/status
Get real-time status of an upload job (for polling).

**Response:** `200 OK`
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "totalPhotos": 100,
  "completedPhotos": 45,
  "failedPhotos": 2,
  "photos": [
    {
      "photoId": "123e4567-e89b-12d3-a456-426614174000",
      "filename": "photo1.jpg",
      "status": "COMPLETED"
    },
    {
      "photoId": "223e4567-e89b-12d3-a456-426614174001",
      "filename": "photo2.jpg",
      "status": "UPLOADING"
    },
    {
      "photoId": "323e4567-e89b-12d3-a456-426614174002",
      "filename": "photo3.jpg",
      "status": "FAILED"
    }
  ]
}
```

**Query Parameters:**
- `includePhotos` (optional): `true`/`false` - Include photo list (default: `true`)

**Business Logic:**
1. Fetch `UploadJob` by ID
2. Verify user ownership
3. Calculate summary stats from job table
4. Optionally fetch photo details
5. Return aggregated status

**Errors:**
- `404 Not Found`: Job doesn't exist
- `403 Forbidden`: User doesn't own this job

---

### Photo Query Endpoints

#### GET /api/photos
List user's completed photos with pagination.

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 50, max: 100)
- `status` (optional): Filter by status (default: COMPLETED)

**Response:** `200 OK`
```json
{
  "photos": [
    {
      "photoId": "123e4567-e89b-12d3-a456-426614174000",
      "filename": "sunset.jpg",
      "fileSizeBytes": 2048000,
      "mimeType": "image/jpeg",
      "downloadUrl": "https://bucket.s3.amazonaws.com/photos/123e4567.../sunset.jpg?X-Amz-Signature=...",
      "createdAt": "2025-11-07T14:30:00Z",
      "tags": ["vacation", "beach"]
    }
  ],
  "pagination": {
    "page": 0,
    "size": 50,
    "totalPages": 3,
    "totalElements": 142
  }
}
```

**Business Logic:**
1. Query photos by user ID and status
2. Apply pagination
3. Generate S3 pre-signed GET URLs (1 hour expiry)
4. Join with tags table
5. Return photo DTOs with download URLs

**Errors:**
- `401 Unauthorized`: Missing/invalid JWT

---

#### GET /api/photos/{photoId}
Get details for a single photo.

**Response:** `200 OK`
```json
{
  "photoId": "123e4567-e89b-12d3-a456-426614174000",
  "filename": "sunset.jpg",
  "fileSizeBytes": 2048000,
  "mimeType": "image/jpeg",
  "downloadUrl": "https://bucket.s3.amazonaws.com/photos/123e4567.../sunset.jpg?X-Amz-Signature=...",
  "status": "COMPLETED",
  "createdAt": "2025-11-07T14:30:00Z",
  "completedAt": "2025-11-07T14:31:30Z",
  "tags": ["vacation", "beach"]
}
```

**Errors:**
- `404 Not Found`: Photo doesn't exist
- `403 Forbidden`: User doesn't own this photo

---

### Tagging Endpoints

#### POST /api/photos/{photoId}/tags
Add tags to a photo.

**Request:**
```json
{
  "tags": ["vacation", "beach", "sunset"]
}
```

**Response:** `200 OK`
```json
{
  "photoId": "123e4567-e89b-12d3-a456-426614174000",
  "tags": ["vacation", "beach", "sunset"]
}
```

**Business Logic:**
1. Verify photo exists and user owns it
2. Insert tags (ignore duplicates via UNIQUE constraint)
3. Return updated tag list

**Errors:**
- `404 Not Found`: Photo doesn't exist
- `403 Forbidden`: User doesn't own this photo
- `400 Bad Request`: Invalid tag format

---

#### DELETE /api/photos/{photoId}/tags/{tag}
Remove a tag from a photo.

**Response:** `204 No Content`

**Errors:**
- `404 Not Found`: Photo or tag doesn't exist
- `403 Forbidden`: User doesn't own this photo

---

## Domain Model

### Aggregates

#### UploadJob (Aggregate Root)
```java
public class UploadJob {
    private final UploadJobId id;
    private final UserId userId;
    private final List<Photo> photos;
    private UploadJobStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    
    // Factory method
    public static UploadJob create(UserId userId, List<PhotoMetadata> metadata, String s3Bucket);
    
    // Domain behaviors
    public void checkAndUpdateStatus();
    public int getTotalPhotos();
    public long getCompletedCount();
    public long getFailedCount();
}
```

**Invariants:**
- Total photos = completed + failed + pending + uploading
- Status transitions: IN_PROGRESS → COMPLETED | PARTIAL_FAILURE
- Cannot add photos after creation

#### Photo (Aggregate Root)
```java
public class Photo {
    private final PhotoId id;
    private final UploadJobId jobId;
    private final UserId userId;
    private final PhotoMetadata metadata;
    private final S3Location s3Location;
    private PhotoStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Factory method
    public static Photo initialize(UploadJobId jobId, UserId userId, PhotoMetadata metadata, String s3Bucket);
    
    // State transitions
    public void markAsStarted();
    public void markAsCompleted();
    public void markAsFailed();
}
```

**State Machine:**
```
PENDING → UPLOADING → COMPLETED
             ↓
          FAILED
```

### Value Objects

#### PhotoMetadata
```java
public class PhotoMetadata {
    private final String filename;
    private final long fileSizeBytes;
    private final String mimeType;
}
```

#### S3Location
```java
public class S3Location {
    private final String bucket;
    private final String key;

    public static S3Location forPhoto(String bucket, PhotoId photoId, String filename);
}
```

---

## Application Layer (CQRS)

### Commands

1. **InitializeUploadCommand**: Create job + photos, generate pre-signed URLs
2. **StartPhotoUploadCommand**: Transition photo to UPLOADING
3. **CompletePhotoUploadCommand**: Transition photo to COMPLETED, update job
4. **FailPhotoUploadCommand**: Transition photo to FAILED
5. **AddPhotoTagCommand**: Add tags to photo
6. **RegisterUserCommand**: Create new user
7. **LoginUserCommand**: Authenticate user

### Queries

1. **GetUploadJobStatusQuery**: Fetch job status with photo list
2. **GetPhotosQuery**: List user's photos with pagination
3. **GetPhotoByIdQuery**: Fetch single photo details

---

## Infrastructure Components

### AWS Resources (Terraform)

**Network:**
- VPC (10.0.0.0/16)
- 2 Public Subnets (for ALB)
- 2 Private Subnets (for ECS + RDS)
- Internet Gateway
- NAT Gateway (1 for cost optimization)
- Route Tables

**Compute:**
- ECS Fargate Cluster
- ECS Task Definition (1 vCPU, 2GB RAM)
- ECS Service (2 tasks for redundancy)
- Application Load Balancer
- Target Group (health check: /actuator/health)

**Data:**
- RDS PostgreSQL 17.6 (db.t3.micro, single-AZ)
- S3 Bucket (with CORS for direct uploads)

**Security:**
- Security Groups (ALB, ECS, RDS)
- IAM Roles (ECS execution, ECS task with S3 permissions)
- Secrets Manager (database password)

---

## Development Phases

### Phase 1: Infrastructure Setup
**Duration:** 1.5 days  
**Priority:** Critical (blocks all other work)

#### Task 1.1: Initialize Terraform Configuration
**Estimate:** 2 hours  
**Dependencies:** None  
**Deliverables:**
- `terraform/versions.tf` with Terraform 1.13+ and AWS provider 6.19+
- `terraform/variables.tf` with all required variables
- `terraform/main.tf` with locals and tags

**Acceptance Criteria:**
- `terraform init` runs successfully
- Variables defined for: aws_region, project_name, environment, db_password, container_image
- Local variables set up for naming and tagging

**Implementation Steps:**
1. Create `terraform/` directory
2. Define required_version >= 1.13.0
3. Configure AWS provider ~> 6.19.0
4. Define input variables with defaults
5. Set up locals for resource naming
6. Initialize Terraform backend (local state)

---

#### Task 1.2: Provision VPC and Network Resources
**Estimate:** 3 hours  
**Dependencies:** Task 1.1  
**Deliverables:**
- `terraform/network.tf` with VPC, subnets, gateways, route tables
- Security groups for ALB, ECS, RDS

**Acceptance Criteria:**
- VPC created with DNS support enabled
- 2 public subnets in different AZs
- 2 private subnets in different AZs
- Internet Gateway attached
- NAT Gateway in public subnet
- Route tables configured correctly
- Security groups allow: ALB (80/443), ECS (8080), RDS (5432)

**Implementation Steps:**
1. Create VPC (10.0.0.0/16)
2. Create 2 public subnets (10.0.0.0/24, 10.0.1.0/24)
3. Create 2 private subnets (10.0.10.0/24, 10.0.11.0/24)
4. Create and attach Internet Gateway
5. Create Elastic IP for NAT Gateway
6. Create NAT Gateway in first public subnet
7. Create public route table (route to IGW)
8. Create private route table (route to NAT)
9. Associate subnets with route tables
10. Create security groups with proper ingress/egress rules

---

#### Task 1.3: Provision RDS PostgreSQL Database
**Estimate:** 2 hours  
**Dependencies:** Task 1.2  
**Deliverables:**
- `terraform/data.tf` with RDS instance configuration
- DB subnet group

**Acceptance Criteria:**
- PostgreSQL 17.6 instance created
- db.t3.micro instance class
- 20GB storage with auto-scaling to 50GB
- Single-AZ deployment
- In private subnets
- Security group allows connections from ECS only
- Outputs include endpoint address

**Implementation Steps:**
1. Create DB subnet group with private subnets
2. Configure RDS instance resource
3. Set engine = "postgres", version = "17.6"
4. Configure storage (20GB, gp3, auto-scaling)
5. Set database name = "photoupload"
6. Configure credentials (username, password from variable)
7. Associate with security group
8. Disable public accessibility
9. Configure backup settings
10. Add output for endpoint

---

#### Task 1.4: Provision S3 Bucket for Photos
**Estimate:** 1 hour  
**Dependencies:** None  
**Deliverables:**
- S3 bucket with CORS configuration
- Public access block settings

**Acceptance Criteria:**
- S3 bucket created with unique name
- CORS allows PUT and GET from any origin
- Public access fully blocked
- Versioning disabled (for cost)
- Outputs include bucket name

**Implementation Steps:**
1. Create S3 bucket resource
2. Configure public access block (all true)
3. Add CORS configuration:
   - Allowed methods: PUT, GET, HEAD
   - Allowed origins: * (restrict in production)
   - Allowed headers: *
   - Expose headers: ETag
4. Add output for bucket name

---

#### Task 1.5: Provision ECS Cluster and ALB
**Estimate:** 4 hours  
**Dependencies:** Task 1.2, Task 1.3, Task 1.4  
**Deliverables:**
- `terraform/compute.tf` with ECS cluster, task definition, service, ALB
- IAM roles for ECS execution and task
- CloudWatch log group

**Acceptance Criteria:**
- ECS Fargate cluster created
- Task definition with 1 vCPU, 2GB RAM
- Container config includes environment variables for DB and S3
- ECS service runs 2 tasks
- ALB created in public subnets
- Target group with health checks
- HTTP listener forwards to target group
- IAM roles grant: ECR pull, CloudWatch logs, S3 read/write
- Outputs include ALB DNS name

**Implementation Steps:**
1. Create ECS cluster with container insights
2. Create CloudWatch log group
3. Create IAM execution role (for ECR, CloudWatch)
4. Create IAM task role (for S3 access)
5. Attach policies to roles
6. Create task definition:
   - Container image from variable
   - Port mapping 8080
   - Environment variables (DB URL, username, S3 bucket, region)
   - Secret for DB password
   - Log configuration
   - Health check command
7. Create ALB in public subnets
8. Create target group (port 8080, health check /actuator/health)
9. Create HTTP listener (port 80 → target group)
10. Create ECS service:
    - 2 desired tasks
    - Network config (private subnets, security group)
    - Load balancer config
11. Add outputs (ALB URL, cluster name, service name)

---

#### Task 1.6: Apply Terraform and Verify Infrastructure
**Estimate:** 2 hours  
**Dependencies:** Task 1.1-1.5  
**Deliverables:**
- Infrastructure deployed to AWS
- Verification of connectivity

**Acceptance Criteria:**
- `terraform plan` shows all resources
- `terraform apply` completes without errors
- ALB health checks pass
- RDS endpoint accessible from ECS
- S3 bucket accessible with pre-signed URLs
- All outputs available

**Implementation Steps:**
1. Create `terraform.tfvars` with values
2. Run `terraform plan` and review
3. Run `terraform apply`
4. Verify resources in AWS console
5. Test basic connectivity:
   - Check ALB DNS resolves
   - Verify ECS tasks are running
   - Test database connection from ECS task
   - Generate test S3 pre-signed URL
6. Document outputs (ALB URL, bucket name, DB endpoint)

---

### Phase 2: Backend Development
**Duration:** 1.5 days  
**Priority:** Critical

#### Task 2.1: Set Up Spring Boot Project Structure
**Estimate:** 1 hour  
**Dependencies:** Task 1.6 (infrastructure ready)  
**Deliverables:**
- Maven project with dependencies
- Package structure for DDD layers

**Acceptance Criteria:**
- `pom.xml` includes: Spring Boot 3.2+, Spring Web, Spring Data JPA, PostgreSQL driver, Flyway, AWS SDK for S3, Spring Security, JJWT
- Directory structure created: domain/, application/, infrastructure/, web/
- `application.yml` configured with placeholders

**Implementation Steps:**
1. Initialize Spring Boot project with Spring Initializr
2. Add dependencies to pom.xml
3. Create package structure
4. Configure application.yml:
   - Database connection (use Terraform outputs)
   - S3 bucket name
   - Flyway enabled
   - JWT secret
5. Create application main class

---

#### Task 2.2: Implement Database Migrations with Flyway
**Estimate:** 2 hours  
**Dependencies:** Task 2.1  
**Deliverables:**
- 4 Flyway migration SQL files

**Acceptance Criteria:**
- `V1__create_users_table.sql` creates users table with index
- `V2__create_upload_jobs_table.sql` creates upload_jobs with index
- `V3__create_photos_table.sql` creates photos with 3 indexes
- `V4__create_photo_tags_table.sql` creates photo_tags with 2 indexes
- Migrations run successfully on application startup

**Implementation Steps:**
1. Create `src/main/resources/db/migration/` directory
2. Write V1 migration (users table)
3. Write V2 migration (upload_jobs table)
4. Write V3 migration (photos table)
5. Write V4 migration (photo_tags table)
6. Test migrations locally with PostgreSQL container
7. Verify constraints and indexes

---

#### Task 2.3: Implement Domain Layer (DDD)
**Estimate:** 4 hours  
**Dependencies:** Task 2.1  
**Deliverables:**
- Pure domain classes with no framework dependencies

**Acceptance Criteria:**
- `UploadJob` aggregate with factory method and behaviors
- `Photo` aggregate with state machine transitions
- Value objects: `PhotoMetadata`, `S3Location`, identity objects
- Enums: `UploadJobStatus`, `PhotoStatus`
- Repository interfaces (ports)
- Domain behaviors enforce invariants
- No JPA annotations in domain layer

**Implementation Steps:**
1. Create `domain/` package
2. Implement `UploadJob` aggregate:
   - Factory method `create()`
   - Behavior `checkAndUpdateStatus()`
   - Query methods for counts
3. Implement `Photo` aggregate:
   - Factory method `initialize()`
   - State transitions: `markAsStarted()`, `markAsCompleted()`, `markAsFailed()`
4. Implement value objects:
   - `PhotoMetadata` (filename, size, mimeType)
   - `S3Location` (bucket, key)
   - `UploadJobId`, `PhotoId`, `UserId`
5. Define enums for statuses
6. Create repository interfaces:
   - `UploadJobRepository`
   - `PhotoRepository`
7. Write unit tests for domain logic

---

#### Task 2.4: Implement Infrastructure Layer (JPA + S3)
**Estimate:** 4 hours  
**Dependencies:** Task 2.3  
**Deliverables:**
- JPA entities and repositories
- S3 service for pre-signed URLs
- Mappers between domain and entities

**Acceptance Criteria:**
- JPA entities: `UploadJobEntity`, `PhotoEntity`, `PhotoTagEntity`, `UserEntity`
- Spring Data JPA repositories
- Adapter classes implementing domain repository interfaces
- Mapper classes for domain ↔ entity conversion
- `S3Service` with methods for generating pre-signed PUT and GET URLs
- AWS SDK configuration

**Implementation Steps:**
1. Create `infrastructure/persistence/` package
2. Implement JPA entities with proper relationships
3. Create Spring Data JPA repository interfaces
4. Implement repository adapters (domain → JPA)
5. Implement mapper classes
6. Create `infrastructure/s3/` package
7. Configure AWS S3 SDK client and presigner
8. Implement `S3Service`:
   - `generatePresignedUploadUrl(key, duration)`
   - `generatePresignedDownloadUrl(key, duration)`
9. Create `S3Config` with bucket name from properties
10. Write integration tests with LocalStack

---

#### Task 2.5: Implement Application Layer (CQRS Handlers)
**Estimate:** 5 hours  
**Dependencies:** Task 2.3, Task 2.4  
**Deliverables:**
- Command handlers for all write operations
- Query handlers for all read operations

**Acceptance Criteria:**
- Command handlers:
  - `InitializeUploadHandler`: Creates job, photos, generates URLs
  - `StartPhotoUploadHandler`: Transitions photo to UPLOADING
  - `CompletePhotoUploadHandler`: Transitions photo to COMPLETED, updates job
  - `FailPhotoUploadHandler`: Marks photo as FAILED
  - `AddPhotoTagHandler`: Adds tags to photo
- Query handlers:
  - `GetUploadJobStatusHandler`: Returns job status with photos
  - `GetPhotosHandler`: Returns paginated photo list with download URLs
  - `GetPhotoByIdHandler`: Returns single photo details
- All handlers use domain repositories
- Transactional boundaries defined
- Proper error handling

**Implementation Steps:**
1. Create `application/commands/` package
2. Define command record classes
3. Implement command handlers with @Service and @Transactional
4. Create `application/queries/` package
5. Define query record classes
6. Implement query handlers with @Transactional(readOnly=true)
7. Use batch operations where appropriate (e.g., saveAll for 100 photos)
8. Write unit tests with mocked repositories

---

#### Task 2.6: Implement Web Layer (REST Controllers)
**Estimate:** 4 hours  
**Dependencies:** Task 2.5  
**Deliverables:**
- REST controllers organized by vertical slices

**Acceptance Criteria:**
- `UploadController` with endpoints:
  - POST /api/upload/initialize
  - PUT /api/upload/photos/{id}/start
  - PUT /api/upload/photos/{id}/complete
  - PUT /api/upload/photos/{id}/fail
  - GET /api/upload/jobs/{id}/status
- `PhotosController` with endpoints:
  - GET /api/photos
  - GET /api/photos/{id}
  - POST /api/photos/{id}/tags
  - DELETE /api/photos/{id}/tags/{tag}
- `AuthController` with endpoints:
  - POST /api/auth/register
  - POST /api/auth/login
- Request/response DTOs defined
- Validation on request bodies
- Proper HTTP status codes
- Exception handling with @ControllerAdvice

**Implementation Steps:**
1. Create `web/upload/` package
2. Implement `UploadController` with all endpoints
3. Define request/response DTOs
4. Create `web/photos/` package
5. Implement `PhotosController`
6. Create `web/auth/` package
7. Implement `AuthController`
8. Create `web/common/` package
9. Implement `GlobalExceptionHandler` with @RestControllerAdvice
10. Add validation annotations (@Valid, @NotNull, etc.)
11. Write integration tests with @WebMvcTest

---

#### Task 2.7: Implement JWT Authentication
**Estimate:** 3 hours  
**Dependencies:** Task 2.6  
**Deliverables:**
- JWT token generation and validation
- Spring Security configuration

**Acceptance Criteria:**
- `JwtService` generates tokens with user ID and expiry
- `JwtAuthenticationFilter` validates tokens on each request
- `SecurityConfig` configures security rules:
  - Permit /api/auth/* without authentication
  - Require authentication for all other endpoints
- User details loaded from database
- Password hashing with BCrypt
- JWT secret configured in application.yml

**Implementation Steps:**
1. Add JJWT dependency
2. Create `infrastructure/security/` package
3. Implement `JwtService`:
   - `generateToken(userId, email)`
   - `validateToken(token)`
   - `extractUserId(token)`
4. Implement `JwtAuthenticationFilter` extends OncePerRequestFilter
5. Implement `SecurityConfig` extends WebSecurityConfigurerAdapter
6. Configure HTTP security:
   - Disable CSRF (stateless API)
   - Session management: STATELESS
   - Permit auth endpoints
   - Authenticate all others
7. Implement `UserDetailsService` loading from database
8. Configure password encoder (BCrypt)
9. Add JWT secret to application.yml
10. Test with Postman

---

#### Task 2.8: Write Integration Tests
**Estimate:** 4 hours  
**Dependencies:** Task 2.7  
**Deliverables:**
- End-to-end integration tests

**Acceptance Criteria:**
- Test: Initialize upload with 100 files
- Test: Start, upload to S3 (LocalStack), complete 100 photos concurrently
- Test: Verify job status transitions correctly
- Test: Query photos with pagination
- Test: Add and remove tags
- Test: Authentication flow
- All tests pass
- Code coverage >80% for application and web layers

**Implementation Steps:**
1. Set up Testcontainers for PostgreSQL
2. Set up LocalStack for S3
3. Create `@SpringBootTest` test class
4. Implement test: Upload 100 photos concurrently
   - Initialize upload
   - Use CompletableFuture for concurrency
   - Simulate S3 uploads
   - Mark all complete
   - Verify job status
5. Implement test: Pagination
6. Implement test: Tagging
7. Implement test: Authentication
8. Run with `mvn test`

---

### Phase 3: Web Client Development
**Duration:** 1 day  
**Priority:** High

#### Task 3.1: Set Up React Project with TypeScript
**Estimate:** 1 hour  
**Dependencies:** None (can start in parallel with backend)  
**Deliverables:**
- Vite + React + TypeScript project
- Directory structure

**Acceptance Criteria:**
- Project created with Vite
- TypeScript configured (strict mode)
- Directory structure: components/, services/, hooks/, types/
- ESLint and Prettier configured
- Dependencies installed: React Router, Axios, React Query (optional)

**Implementation Steps:**
1. Run `npm create vite@latest web -- --template react-ts`
2. Install dependencies: `npm install axios react-router-dom`
3. Create directory structure
4. Configure `tsconfig.json` for strict mode
5. Set up `.env.example` for API base URL
6. Configure Vite proxy for local development

---

#### Task 3.2: Implement API Service Layer
**Estimate:** 2 hours  
**Dependencies:** Task 3.1, Task 2.6 (API endpoints available)  
**Deliverables:**
- Service modules for API calls

**Acceptance Criteria:**
- `api.ts`: Axios instance with base URL and JWT interceptor
- `authService.ts`: login(), register()
- `uploadService.ts`: initialize(), startUpload(), completeUpload(), getJobStatus()
- `photoService.ts`: getPhotos(), getPhotoById(), addTags(), removeTag()
- `s3Service.ts`: uploadToS3() with progress callback
- TypeScript types for all requests/responses
- Error handling

**Implementation Steps:**
1. Create `services/api.ts`:
   - Configure Axios instance
   - Add request interceptor for JWT
   - Add response interceptor for error handling
2. Implement `authService.ts`
3. Implement `uploadService.ts`:
   - initialize() calls POST /api/upload/initialize
   - startUpload() calls PUT /api/upload/photos/{id}/start
   - completeUpload() calls PUT /api/upload/photos/{id}/complete
   - getJobStatus() calls GET /api/upload/jobs/{id}/status
4. Implement `s3Service.ts`:
   - uploadToS3(url, file, onProgress)
   - Uses axios.put with onUploadProgress
5. Implement `photoService.ts`
6. Create `types/` with TypeScript interfaces
7. Test with mock data

---

#### Task 3.3: Implement Authentication Components
**Estimate:** 2 hours  
**Dependencies:** Task 3.2  
**Deliverables:**
- Login and registration components
- Auth context for state management

**Acceptance Criteria:**
- `Login.tsx` component with form validation
- `Register.tsx` component with form validation
- `useAuth` hook for auth state and actions
- JWT token stored in localStorage
- Protected routes require authentication
- Redirect to login if unauthenticated

**Implementation Steps:**
1. Create `components/Auth/Login.tsx`
2. Create `components/Auth/Register.tsx`
3. Create `hooks/useAuth.ts`:
   - State: user, token, loading
   - Actions: login, register, logout
   - Store JWT in localStorage
   - Provide user context
4. Create `AuthContext` provider
5. Implement protected route wrapper
6. Add React Router setup
7. Style with CSS/Tailwind

---

#### Task 3.4: Implement Photo Upload Components
**Estimate:** 4 hours  
**Dependencies:** Task 3.2  
**Deliverables:**
- Photo uploader with progress tracking

**Acceptance Criteria:**
- `PhotoUploader.tsx` main component
- `FileSelector.tsx` for batch file selection
- `UploadProgress.tsx` showing progress bars
- `UploadQueue.tsx` managing 100 concurrent uploads
- Client-side progress tracking (0-100%)
- Support for 100 concurrent uploads via Promise.all
- Upload flow: select → initialize → upload to S3 → mark complete
- Real-time status polling during upload
- Error handling and retry logic

**Implementation Steps:**
1. Create `components/PhotoUploader/PhotoUploader.tsx`:
   - State: files[], jobId, uploading
   - Handle file selection
   - Call uploadService.initialize()
   - For each photo:
     - Call startUpload()
     - Call s3Service.uploadToS3() with progress callback
     - Call completeUpload()
   - Use Promise.all for concurrency
2. Create `FileSelector.tsx`:
   - Input type="file" multiple
   - Display selected files
3. Create `UploadProgress.tsx`:
   - Progress bar per file
   - Status indicator (pending/uploading/completed/failed)
   - Percentage display
4. Create `UploadQueue.tsx`:
   - List of files with status
   - Cancel button (optional)
5. Implement `usePhotoUpload` hook:
   - Manage upload state
   - Handle concurrent uploads
6. Style components
7. Test with 100 test images

---

#### Task 3.5: Implement Photo Gallery Components
**Estimate:** 3 hours  
**Dependencies:** Task 3.2  
**Deliverables:**
- Photo gallery with viewing and downloading

**Acceptance Criteria:**
- `PhotoGallery.tsx` component with grid layout
- `PhotoCard.tsx` for individual photo display
- `PhotoModal.tsx` for full-size viewing
- Pagination support
- Download functionality via pre-signed URLs
- Tag display
- Lazy loading for performance

**Implementation Steps:**
1. Create `components/PhotoGallery/PhotoGallery.tsx`:
   - Fetch photos with photoService.getPhotos()
   - Display in responsive grid
   - Implement pagination
2. Create `PhotoCard.tsx`:
   - Thumbnail image
   - Filename, size, date
   - Tags
   - Click to open modal
3. Create `PhotoModal.tsx`:
   - Full-size image display
   - Download button
   - Close button
   - Tag management
4. Implement `usePhotos` hook:
   - Fetch and cache photo list
   - Pagination state
5. Style with CSS Grid/Flexbox
6. Implement lazy loading

---

#### Task 3.6: Implement Tagging Functionality
**Estimate:** 2 hours  
**Dependencies:** Task 3.5  
**Deliverables:**
- Tag management UI

**Acceptance Criteria:**
- `PhotoTags.tsx` component displays tags
- `TagInput.tsx` for adding new tags
- Add tag functionality
- Remove tag functionality
- Tag suggestions (optional)

**Implementation Steps:**
1. Create `components/PhotoTags.tsx`:
   - Display list of tags
   - Remove button per tag
2. Create `components/TagInput.tsx`:
   - Input field for new tag
   - Add button
   - Enter key support
3. Integrate with PhotoModal
4. Call photoService.addTags() and removeTag()
5. Update local state after mutations
6. Style tag badges

---

#### Task 3.7: Polish UI and Add Responsive Design
**Estimate:** 2 hours  
**Dependencies:** Task 3.4, 3.5, 3.6  
**Deliverables:**
- Responsive design for mobile/tablet/desktop
- Loading states and error messages

**Acceptance Criteria:**
- Responsive breakpoints implemented
- Mobile-friendly navigation
- Loading spinners during async operations
- Toast notifications for success/error
- Empty states for no photos
- Accessible (ARIA labels, keyboard navigation)

**Implementation Steps:**
1. Add CSS media queries for breakpoints
2. Implement mobile navigation (hamburger menu)
3. Add loading spinners
4. Create Toast notification component
5. Add empty state components
6. Test on different screen sizes
7. Add ARIA labels
8. Test keyboard navigation

---

### Phase 4: Mobile Client Development
**Duration:** 1 day  
**Priority:** High

#### Task 4.1: Set Up React Native Project
**Estimate:** 1 hour  
**Dependencies:** None  
**Deliverables:**
- React Native project with Expo

**Acceptance Criteria:**
- Project created with Expo
- TypeScript configured
- Navigation set up (React Navigation)
- Dependencies installed: Expo File System, Expo Image Picker, Axios

**Implementation Steps:**
1. Run `npx create-expo-app mobile --template expo-template-blank-typescript`
2. Install dependencies:
   - `expo-file-system`
   - `expo-image-picker`
   - `@react-navigation/native`
   - `@react-navigation/stack`
   - `axios`
3. Configure TypeScript
4. Set up navigation structure
5. Create `.env.example` for API base URL

---

#### Task 4.2: Implement Shared Services (API Layer)
**Estimate:** 1.5 hours  
**Dependencies:** Task 4.1, Task 2.6 (API endpoints available)  
**Deliverables:**
- API service modules (reuse web logic where possible)

**Acceptance Criteria:**
- `api.ts`: Axios instance with JWT interceptor
- `authService.ts`: login(), register()
- `photoService.ts`: initialize(), start, complete, getStatus()
- `uploadService.ts`: uploadToS3() using expo-file-system
- Error handling

**Implementation Steps:**
1. Copy relevant service files from web project
2. Adapt for React Native:
   - Use AsyncStorage instead of localStorage
   - Use expo-file-system for uploads
3. Implement `uploadService.ts`:
   - uploadToS3() using FileSystem.uploadAsync()
4. Test with Metro bundler

---

#### Task 4.3: Implement Authentication Screens
**Estimate:** 2 hours  
**Dependencies:** Task 4.2  
**Deliverables:**
- Login and registration screens

**Acceptance Criteria:**
- `LoginScreen.tsx` with form validation
- `RegisterScreen.tsx` with form validation
- `useAuth` hook for state management
- JWT stored in AsyncStorage
- Navigation to main app after login

**Implementation Steps:**
1. Create `screens/LoginScreen.tsx`
2. Create `screens/RegisterScreen.tsx`
3. Create `hooks/useAuth.ts`:
   - Use AsyncStorage for token
   - Provide auth context
4. Set up navigation guards
5. Style with React Native components

---

#### Task 4.4: Implement Upload Screen
**Estimate:** 4 hours  
**Dependencies:** Task 4.2  
**Deliverables:**
- Photo upload functionality

**Acceptance Criteria:**
- `UploadScreen.tsx` with photo picker
- `PhotoPicker.tsx` component using expo-image-picker
- `UploadProgress.tsx` showing progress
- Support for selecting and uploading up to 100 photos
- Concurrent uploads (limited to ~10 at a time on mobile)
- Background upload support (optional)
- Local progress tracking

**Implementation Steps:**
1. Create `screens/UploadScreen.tsx`:
   - Button to launch photo picker
   - Display selected photos
   - Initialize upload
   - Upload to S3 concurrently (batch of 10)
   - Poll for status
2. Create `components/PhotoPicker.tsx`:
   - Use expo-image-picker.launchImageLibraryAsync()
   - Allow multiple selection
3. Create `components/UploadProgress.tsx`:
   - Progress bar per photo
   - Status indicators
4. Implement `usePhotoUpload` hook
5. Handle permissions for photo library access
6. Test with real device

---

#### Task 4.5: Implement Gallery Screen
**Estimate:** 3 hours  
**Dependencies:** Task 4.2  
**Deliverables:**
- Photo gallery with viewing

**Acceptance Criteria:**
- `GalleryScreen.tsx` with photo grid
- `PhotoGrid.tsx` component with FlatList
- `PhotoDetailScreen.tsx` for full-size viewing
- Download functionality
- Tag display
- Pull-to-refresh

**Implementation Steps:**
1. Create `screens/GalleryScreen.tsx`:
   - Fetch photos
   - Display in grid (FlatList with numColumns)
   - Pull-to-refresh
   - Navigate to detail on tap
2. Create `components/PhotoGrid.tsx`:
   - Render photo cards
   - Lazy loading
3. Create `screens/PhotoDetailScreen.tsx`:
   - Full-size image
   - Download button (save to device)
   - Tags
4. Implement download using FileSystem.downloadAsync()
5. Style appropriately

---

#### Task 4.6: Implement Tagging Functionality
**Estimate:** 1.5 hours  
**Dependencies:** Task 4.5  
**Deliverables:**
- Tag management in mobile app

**Acceptance Criteria:**
- `TagInput.tsx` component for adding tags
- Display tags on photo detail screen
- Add/remove tags

**Implementation Steps:**
1. Create `components/TagInput.tsx`:
   - TextInput for new tag
   - Add button
2. Display tags in PhotoDetailScreen
3. Implement remove tag with onPress
4. Call API to add/remove tags
5. Update local state

---

#### Task 4.7: Test on Real Devices
**Estimate:** 2 hours  
**Dependencies:** Task 4.4, 4.5, 4.6  
**Deliverables:**
- Tested app on iOS and Android

**Acceptance Criteria:**
- App runs on iOS simulator/device
- App runs on Android emulator/device
- Upload works with real photos
- Gallery displays correctly
- Navigation smooth
- No crashes

**Implementation Steps:**
1. Build app for iOS: `expo run:ios`
2. Build app for Android: `expo run:android`
3. Test upload flow with 20+ photos
4. Test gallery and detail screens
5. Test offline behavior (graceful errors)
6. Fix any platform-specific issues

---

### Phase 5: Integration Testing and Demo
**Duration:** 1 day  
**Priority:** Critical

#### Task 5.1: Run Full Integration Test Suite
**Estimate:** 2 hours  
**Dependencies:** Task 2.8, Task 3.7, Task 4.7  
**Deliverables:**
- All integration tests passing

**Acceptance Criteria:**
- Backend integration tests pass (100 concurrent uploads)
- API endpoints respond correctly
- Database constraints enforced
- S3 uploads work end-to-end

**Implementation Steps:**
1. Run backend tests: `mvn test`
2. Verify all tests pass
3. Review code coverage reports
4. Fix any failing tests
5. Document test results

---

#### Task 5.2: End-to-End Testing with Clients
**Estimate:** 3 hours  
**Dependencies:** Task 5.1  
**Deliverables:**
- Manual E2E test scenarios completed

**Acceptance Criteria:**
- Web client: Upload 100 photos successfully
- Mobile client: Upload 20+ photos successfully
- Status polling shows correct progress
- Gallery displays all photos
- Tags can be added/removed
- Download works
- Authentication flows work
- Error cases handled gracefully

**Implementation Steps:**
1. Prepare 100 test images (~2MB each)
2. Test web client:
   - Register new user
   - Login
   - Upload 100 photos concurrently
   - Monitor progress in real-time
   - Verify all complete
   - View in gallery
   - Add tags
   - Download photo
3. Test mobile client:
   - Login
   - Upload batch of photos
   - View gallery
   - Test detail screen
4. Test error cases:
   - Invalid credentials
   - Network interruption during upload
   - Large files
5. Document results

---

#### Task 5.3: Performance Testing
**Estimate:** 2 hours  
**Dependencies:** Task 5.2  
**Deliverables:**
- Performance metrics documented

**Acceptance Criteria:**
- 100 photos (200MB) upload in <90 seconds @ 25 Mbps
- API endpoints respond in <100ms (p95)
- Database queries complete in <50ms (p95)
- UI remains responsive during uploads

**Implementation Steps:**
1. Set up network throttling (25 Mbps)
2. Upload 100 photos and measure time
3. Verify <90 seconds
4. Use browser DevTools to measure API response times
5. Check database query performance (pg_stat_statements)
6. Monitor ECS task CPU/memory usage
7. Document results

---

#### Task 5.4: Build and Deploy Docker Image
**Estimate:** 2 hours  
**Dependencies:** Task 5.3  
**Deliverables:**
- Docker image built and pushed to ECR
- ECS service updated

**Acceptance Criteria:**
- Dockerfile builds successfully
- Image pushed to ECR
- ECS service updated with new image
- Health checks pass
- Application accessible via ALB

**Implementation Steps:**
1. Create `Dockerfile` in backend/:
   ```dockerfile
   FROM eclipse-temurin:17-jdk-alpine AS build
   WORKDIR /app
   COPY pom.xml .
   COPY src ./src
   RUN ./mvnw clean package -DskipTests
   
   FROM eclipse-temurin:17-jre-alpine
   WORKDIR /app
   COPY --from=build /app/target/*.jar app.jar
   EXPOSE 8080
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```
2. Build image: `docker build -t rapid-photo-upload:latest .`
3. Tag for ECR: `docker tag rapid-photo-upload:latest <account>.dkr.ecr.us-west-1.amazonaws.com/rapid-photo-upload:latest`
4. Push to ECR: `docker push <account>.dkr.ecr.us-west-1.amazonaws.com/rapid-photo-upload:latest`
5. Update Terraform variable with image URL
6. Run `terraform apply` to update ECS service
7. Wait for deployment to complete
8. Verify via ALB health check

---

#### Task 5.5: Create Demo Video
**Estimate:** 2 hours  
**Dependencies:** Task 5.4  
**Deliverables:**
- 3-5 minute demo video

**Acceptance Criteria:**
- Video shows:
  - Web client uploading 100 photos
  - Real-time progress indicators
  - Status transitions (pending → uploading → completed)
  - Gallery view with pagination
  - Tagging functionality
  - Mobile client upload flow
  - Download functionality
- Audio narration explaining features
- Meets 90-second upload benchmark

**Implementation Steps:**
1. Prepare script and talking points
2. Set up screen recording software (OBS, QuickTime)
3. Record web client demo:
   - Login
   - Select 100 photos
   - Upload with progress
   - Show status polling
   - Navigate gallery
   - Add tags
4. Record mobile client demo:
   - Login
   - Upload photos
   - View gallery
5. Edit video with annotations
6. Add audio narration
7. Export and upload

---

#### Task 5.6: Write Technical Documentation
**Estimate:** 2 hours  
**Dependencies:** Task 5.5  
**Deliverables:**
- Technical writeup (1-2 pages)

**Acceptance Criteria:**
- Document includes:
  - Architecture overview
  - Concurrency strategy (direct S3 uploads)
  - Asynchronous design (client-side progress, polling)
  - Cloud storage interaction (pre-signed URLs)
  - DDD/CQRS/VSA implementation details
  - Division of logic across components
  - AI tool usage (if any) with examples
- Clear diagrams
- Code snippets where relevant

**Implementation Steps:**
1. Create `docs/technical-writeup.md`
2. Section 1: Architecture
   - Diagram: ALB → ECS → RDS + S3
   - Component responsibilities
3. Section 2: Concurrency Strategy
   - Direct S3 uploads
   - Pre-signed URLs
   - Promise.all for parallel uploads
4. Section 3: Asynchronous Design
   - Client-side progress tracking
   - Polling for status updates
   - Non-blocking UI
5. Section 4: Domain-Driven Design
   - Aggregates: UploadJob, Photo
   - Domain behaviors
   - Repository pattern
6. Section 5: AI Tools (if used)
   - Tool name and purpose
   - Example prompts
   - Impact on development
7. Export to PDF

---

#### Task 5.7: Prepare Submission Package
**Estimate:** 1 hour  
**Dependencies:** Task 5.6  
**Deliverables:**
- Complete submission package

**Acceptance Criteria:**
- GitHub repository includes:
  - All source code (backend, web, mobile, terraform)
  - README.md with setup instructions
  - Technical writeup
  - Demo video (linked or uploaded)
  - Test results
  - AI tool documentation
- Repository is public or access granted

**Implementation Steps:**
1. Create comprehensive README.md:
   - Project overview
   - Prerequisites
   - Setup instructions
   - Deployment guide
   - API documentation link
2. Organize repository structure
3. Add .gitignore files
4. Commit all code
5. Push to GitHub
6. Upload demo video to YouTube (unlisted) or include in repo
7. Add LICENSE file
8. Verify repository is accessible
9. Create release tag (v1.0.0)

---

## Testing Strategy

### Unit Tests
**Target Coverage:** 80%+ for domain and application layers

**Focus Areas:**
- Domain aggregate behaviors and state transitions
- Command handler logic
- Query handler logic
- Value object validation

**Tools:** JUnit 5, Mockito

---

### Integration Tests
**Focus Areas:**
- Full upload flow (initialize → upload → complete)
- Database operations with real PostgreSQL
- S3 interactions with LocalStack
- Authentication flow

**Tools:** Spring Boot Test, Testcontainers, LocalStack

---

### End-to-End Tests
**Scenarios:**
1. User registration and login
2. Upload 100 photos concurrently
3. Real-time status updates via polling
4. Gallery view with pagination
5. Tagging operations
6. Download photos

**Tools:** Manual testing, Selenium (optional)

---

### Performance Tests
**Metrics:**
- 100 photos (200MB) upload time: <90 seconds @ 25 Mbps
- API response time (p95): <100ms
- Database query time (p95): <50ms
- Concurrent user load: 10 users uploading simultaneously

**Tools:** Apache JMeter (optional), browser DevTools

---

## Deployment Guide

### Prerequisites
- AWS Account with appropriate permissions
- Terraform 1.13+ installed
- Docker installed
- AWS CLI configured

### Steps

1. **Clone Repository**
   ```bash
   git clone https://github.com/your-org/rapid-photo-upload.git
   cd rapid-photo-upload
   ```

2. **Deploy Infrastructure**
   ```bash
   cd terraform
   terraform init
   
   # Create terraform.tfvars
   cat > terraform.tfvars <<EOF
   aws_region      = "us-west-1"
   project_name    = "rapid-photo-upload"
   environment     = "prod"
   db_password     = "your-secure-password"
   container_image = "placeholder"
   EOF
   
   terraform apply
   ```

3. **Build and Push Backend**
   ```bash
   cd backend
   ./mvnw clean package
   docker build -t rapid-photo-upload:latest .
   
   # Get ECR URL from Terraform output
   ECR_URL=$(cd ../terraform && terraform output -raw ecr_repository_url)
   
   # Login to ECR
   aws ecr get-login-password --region us-west-1 | docker login --username AWS --password-stdin $ECR_URL
   
   # Tag and push
   docker tag rapid-photo-upload:latest $ECR_URL:latest
   docker push $ECR_URL:latest
   ```

4. **Update Terraform with Image**
   ```bash
   cd terraform
   terraform apply -var="container_image=$ECR_URL:latest"
   ```

5. **Deploy Web Client**
   ```bash
   cd web
   npm install
   npm run build
   
   # Deploy to S3 + CloudFront (or serve via ALB)
   aws s3 sync dist/ s3://your-web-bucket/
   ```

6. **Test Deployment**
   ```bash
   # Get ALB URL
   ALB_URL=$(cd terraform && terraform output -raw alb_url)
   
   # Test health
   curl $ALB_URL/actuator/health
   ```

---

## Cost Estimates

### AWS Monthly Costs (Production)

| Service | Configuration | Monthly Cost |
|---------|--------------|--------------|
| ECS Fargate | 2 tasks × 1 vCPU × 2GB × 730h | $65 |
| ALB | 1 load balancer + LCU | $18 |
| RDS PostgreSQL | db.t3.micro, 20GB, single-AZ | $15 |
| S3 | 100GB storage + 10K requests | $3 |
| NAT Gateway | 1 gateway + data transfer | $32 |
| **Total** | | **$133/month** |

### Demo/Dev Costs (8 hours/day)
- Turn off resources when not in use: **~$50/month**

---

## Success Criteria

### Functional Requirements
- [ ] Support 100 concurrent uploads per user
- [ ] Complete uploads within 90 seconds on 25 Mbps connection
- [ ] UI remains responsive during uploads
- [ ] Real-time status updates via polling (2-5 seconds)
- [ ] Web client (React/TypeScript) fully functional
- [ ] Mobile client (React Native) fully functional
- [ ] JWT authentication implemented
- [ ] Photo gallery with pagination
- [ ] Tagging functionality

### Technical Requirements
- [ ] DDD: UploadJob and Photo aggregates implemented
- [ ] CQRS: Separate command and query handlers
- [ ] VSA: Controllers organized by feature
- [ ] PostgreSQL with Flyway migrations
- [ ] AWS S3 with pre-signed URLs
- [ ] Integration tests with >80% coverage
- [ ] Clean, documented code
- [ ] Infrastructure as code (Terraform)

### Performance Requirements
- [ ] 100 photos upload in <90 seconds
- [ ] API response time p95 <100ms
- [ ] Database query time p95 <50ms
- [ ] UI responsive (no freezing during uploads)

### Deliverables
- [ ] Complete source code in GitHub repository
- [ ] Technical writeup (1-2 pages)
- [ ] Demo video (3-5 minutes)
- [ ] Integration test results
- [ ] AI tool documentation (if used)

---

## Risk Management

### Technical Risks

**Risk:** S3 upload failures due to network issues  
**Mitigation:** Implement retry logic with exponential backoff

**Risk:** Database bottleneck with 100 concurrent inserts  
**Mitigation:** Use batch inserts, optimize indexes

**Risk:** ECS task memory issues  
**Mitigation:** Monitor CloudWatch metrics, increase task memory if needed

**Risk:** JWT token expiry during long uploads  
**Mitigation:** Set token expiry to 24 hours, implement refresh token

**Risk:** Mobile photo picker limitations  
**Mitigation:** Batch uploads in groups of 10-20

### Schedule Risks

**Risk:** Infrastructure setup takes longer than expected  
**Mitigation:** Start with minimal Terraform config, iterate

**Risk:** Integration issues between frontend and backend  
**Mitigation:** Define API contracts early, use mocks for parallel development

**Risk:** Testing reveals performance issues  
**Mitigation:** Allocate buffer time in Phase 5 for optimization

---

## Appendix

### Environment Variables

**Backend (application.yml):**
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  flyway:
    enabled: true

aws:
  region: ${AWS_REGION}
  s3:
    bucket-name: ${S3_BUCKET_NAME}

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000  # 24 hours
```

**Web (.env):**
```
VITE_API_BASE_URL=http://localhost:8080
```

**Mobile (.env):**
```
API_BASE_URL=http://localhost:8080
```

### Useful Commands

**Terraform:**
```bash
terraform init
terraform plan
terraform apply
terraform destroy
terraform output
```

**Maven:**
```bash
./mvnw clean package
./mvnw test
./mvnw spring-boot:run
```

**Docker:**
```bash
docker build -t app .
docker run -p 8080:8080 app
docker ps
docker logs <container-id>
```

**AWS CLI:**
```bash
aws ecr get-login-password | docker login ...
aws s3 ls
aws ecs list-tasks --cluster <cluster-name>
aws logs tail <log-group> --follow
```

---

**This document serves as the single source of truth for the RapidPhotoUpload project. All development tasks should reference this specification.**