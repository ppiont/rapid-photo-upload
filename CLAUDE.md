# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RapidPhotoUpload is a production-grade, high-volume photo upload system demonstrating architectural excellence through DDD, CQRS, and Vertical Slice Architecture. The system supports 100 concurrent uploads per user with direct S3 uploads via pre-signed URLs, achieving sub-90-second completion times.

**Architecture:** Monorepo with Spring Boot backend, React web client, React Native mobile client, and Terraform-managed AWS infrastructure (ALB → ECS Fargate → RDS PostgreSQL + S3).

**Key Performance Target:** 100 photos (200MB total) must upload in <90 seconds on 25 Mbps connection.

## Repository Structure

```
rapid-photo-upload/
├── backend/          # Spring Boot Java application (DDD/CQRS/VSA)
├── web/              # React TypeScript web client
├── mobile/           # React Native mobile application
└── terraform/        # AWS infrastructure as code
```

## Development Commands

### Backend (Spring Boot)

```bash
cd backend

# Build project
./mvnw clean package

# Run tests (includes integration tests with Testcontainers + LocalStack)
./mvnw test

# Run application locally
./mvnw spring-boot:run

# Run integration tests only
./mvnw test -Dtest="*IntegrationTest"

# Build Docker image
docker build -t rapid-photo-upload:latest .

# Check code coverage
./mvnw test jacoco:report
# View: target/site/jacoco/index.html
```

### Web Client (React + TypeScript + Vite)

```bash
cd web

# Install dependencies
npm install

# Run development server with hot reload
npm run dev

# Build for production
npm run build

# Run linter
npm run lint

# Type check
npm run type-check

# Preview production build
npm run preview
```

### Mobile Client (React Native)

```bash
cd mobile

# Install dependencies
npm install

# Start Metro bundler
npm start

# Run on iOS simulator
npm run ios

# Run on Android emulator
npm run android

# Run tests
npm test

# Build for iOS
npm run build:ios

# Build for Android
npm run build:android
```

### Infrastructure (Terraform)

```bash
cd terraform

# Initialize Terraform
terraform init

# Plan infrastructure changes
terraform plan

# Apply infrastructure
terraform apply

# View outputs (ALB URL, bucket name, etc.)
terraform output

# Destroy infrastructure (for cleanup)
terraform destroy

# Format Terraform files
terraform fmt -recursive
```

### AWS Deployment Workflow

```bash
# 1. Build backend JAR
cd backend && ./mvnw clean package

# 2. Build and push Docker image to ECR
docker build -t rapid-photo-upload:latest .
ECR_URL=$(cd ../terraform && terraform output -raw ecr_repository_url)
aws ecr get-login-password --region us-west-1 | docker login --username AWS --password-stdin $ECR_URL
docker tag rapid-photo-upload:latest $ECR_URL:latest
docker push $ECR_URL:latest

# 3. Update ECS service with new image
cd ../terraform
terraform apply -var="container_image=$ECR_URL:latest"

# 4. Monitor deployment
aws ecs describe-services --cluster rapid-photo-upload-demo-cluster --services rapid-photo-upload-demo-service

# 5. View logs
aws logs tail /ecs/rapid-photo-upload-demo --follow
```

## High-Level Architecture

### Core Upload Flow (Critical to Understand)

The architecture uses **direct S3 uploads** to achieve high concurrency and avoid backend bottlenecks:

1. **Client → Backend:** POST /api/upload/initialize with 100 file metadata objects
2. **Backend:** Creates UploadJob aggregate + 100 Photo entities, generates pre-signed S3 URLs (1-hour expiry)
3. **Backend → Client:** Returns job ID + array of {photoId, filename, uploadUrl}
4. **Client:** For each photo (using Promise.all for parallelism):
   - PUT /api/upload/photos/{id}/start (mark as UPLOADING)
   - PUT to S3 pre-signed URL (direct upload, bypasses backend)
   - PUT /api/upload/photos/{id}/complete (mark as COMPLETED)
5. **Status Tracking:** Client polls GET /api/upload/jobs/{jobId}/status every 2-5 seconds
6. **Job Completion:** Backend auto-updates UploadJob status when all photos reach terminal state

**Why This Design:**
- Backend never touches binary data (no memory/bandwidth bottleneck)
- S3 handles concurrent uploads natively
- Client-side progress tracking is accurate and real-time
- Backend only manages state transitions (PENDING → UPLOADING → COMPLETED/FAILED)

### Domain-Driven Design (DDD) Implementation

The backend strictly follows DDD with proper aggregate boundaries:

**Aggregates (in `backend/src/main/java/com/demo/photoupload/domain/`):**
- `UploadJob`: Aggregate root managing batch upload lifecycle
  - Invariants: Total photos = completed + failed + pending + uploading
  - Behaviors: `create()`, `checkAndUpdateStatus()`
  - Status: IN_PROGRESS → COMPLETED | PARTIAL_FAILURE

- `Photo`: Aggregate root for individual photo state
  - State Machine: PENDING → UPLOADING → COMPLETED | FAILED
  - Behaviors: `markAsStarted()`, `markAsCompleted()`, `markAsFailed()`
  - Location stored as S3Location value object

**Value Objects:**
- `PhotoMetadata` (filename, size, mimeType)
- `S3Location` (bucket, key)
- `UploadJobId`, `PhotoId`, `UserId` (type-safe IDs)

**Critical Rule:** Domain objects have ZERO framework dependencies (no JPA, no Spring annotations).

### CQRS Pattern

Commands and Queries are strictly separated:

**Commands** (`backend/src/main/java/com/demo/photoupload/application/commands/`):
- `InitializeUploadHandler`: Creates job + photos + pre-signed URLs
- `StartPhotoUploadHandler`: Transitions PENDING → UPLOADING
- `CompletePhotoUploadHandler`: Transitions UPLOADING → COMPLETED, updates job status
- `FailPhotoUploadHandler`: Marks photo as FAILED

**Queries** (`backend/src/main/java/com/demo/photoupload/application/queries/`):
- `GetUploadJobStatusHandler`: Returns job status with photo list (for polling)
- `GetPhotosHandler`: Returns paginated photos with download URLs
- `GetPhotoByIdHandler`: Returns single photo details

All handlers are `@Transactional` and use domain repository interfaces.

### Vertical Slice Architecture (VSA)

Controllers are organized by feature (not by CRUD):

- `web/upload/UploadController`: All upload-related endpoints
- `web/photos/PhotosController`: Photo viewing/querying endpoints
- `web/auth/AuthController`: Authentication endpoints

Each slice contains its own request/response DTOs and directly uses command/query handlers.

### Infrastructure Layer Patterns

**Persistence Adapters** (`backend/src/main/java/com/demo/photoupload/infrastructure/persistence/`):
- JPA entities (UploadJobEntity, PhotoEntity) are separate from domain objects
- Adapters implement domain repository interfaces
- Mappers convert between domain ↔ JPA entities
- Uses Spring Data JPA for queries, but domain logic stays pure

**S3 Service** (`backend/src/main/java/com/demo/photoupload/infrastructure/s3/`):
- `generatePresignedUploadUrl(key, duration)`: Creates PUT URL for client uploads
- `generatePresignedDownloadUrl(key, duration)`: Creates GET URL for downloads
- Uses AWS SDK S3Presigner with 1-hour expiry

**Key Abstraction:** Domain layer defines repository interfaces as "ports"; infrastructure provides "adapters."

## Database Schema Design Principles

**What We Store (PostgreSQL):**
- User credentials and metadata
- Photo metadata (filename, size, S3 location)
- Upload job state (total, completed, failed counts)
- Photo tags

**What We DON'T Store:**
- Binary file data (stored in S3)
- Upload progress percentages (client-side only)
- Intermediate error messages (logged server-side, not persisted)

**Critical Indexes:**
- `idx_upload_jobs_user_status` on (user_id, status, created_at DESC)
- `idx_photos_job_status` on (job_id, status)
- `idx_photos_user_status` on (user_id, status, created_at DESC)
- `idx_photos_s3_key` on (s3_key) for uniqueness checks

**Migrations:** Flyway scripts in `backend/src/main/resources/db/migration/` (V1__create_users, V2__create_upload_jobs, V3__create_photos, V4__create_photo_tags).

## Frontend State Management

### Web Client (React)

**Progress Tracking Strategy:**
- Each file gets local state: `{file: File, status: 'pending'|'uploading'|'completed'|'failed', progress: 0-100, photoId?: string}`
- Axios `onUploadProgress` callback updates local percentage (smooth, real-time)
- Backend is only notified on state transitions (start, complete, fail)

**Key Hooks:**
- `useAuth`: Manages JWT token, login/logout, stores in localStorage
- `usePhotoUpload`: Orchestrates 100 concurrent uploads via Promise.all
- `usePhotos`: Fetches and caches photo list with pagination

**Critical Implementation Detail:**
```typescript
// uploads happen in parallel, not sequentially
const uploadPromises = photos.map(async (photoData, index) => {
  await uploadService.startUpload(photoData.photoId);
  await s3Service.uploadToS3(photoData.uploadUrl, file, onProgress);
  await uploadService.completeUpload(photoData.photoId);
});
await Promise.all(uploadPromises); // <-- 100 concurrent uploads
```

### Mobile Client (React Native)

**Key Differences from Web:**
- Uses `expo-image-picker` for multi-select (up to 100 photos)
- Uses `expo-file-system` for S3 uploads (FileSystem.uploadAsync)
- Concurrent upload limit: ~10-20 at a time (mobile bandwidth constraints)
- JWT stored in AsyncStorage (not localStorage)
- Pull-to-refresh in gallery

## Testing Strategy

### Integration Tests (Backend)

Located in `backend/src/test/`, uses:
- **Testcontainers** for real PostgreSQL instance
- **LocalStack** for S3 emulation
- **SpringBootTest** with random port

**Critical Test Scenario:**
```java
@Test
void shouldUpload100PhotosConcurrently() {
    // 1. Initialize with 100 files
    var initResponse = restTemplate.post("/api/upload/initialize", files);

    // 2. Upload concurrently with CompletableFuture
    List<CompletableFuture<Void>> uploads = initResponse.photos().stream()
        .map(p -> CompletableFuture.runAsync(() -> {
            restTemplate.put("/api/upload/photos/" + p.photoId() + "/start");
            // simulate S3 upload
            restTemplate.put("/api/upload/photos/" + p.photoId() + "/complete");
        }))
        .toList();

    CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0])).get();

    // 3. Verify job status
    var status = restTemplate.get("/api/upload/jobs/" + jobId + "/status");
    assertEquals("COMPLETED", status.status());
}
```

**Coverage Target:** >80% for application and web layers.

### End-to-End Testing

Manual testing checklist:
1. Upload 100 photos (2MB each) and measure time (<90s required)
2. Verify UI remains responsive during upload
3. Check status polling updates correctly
4. Test error scenarios (network failure, large files)
5. Verify gallery pagination
6. Test tagging add/remove

## Common Development Patterns

### Adding a New Upload State Transition

1. Update domain enum: `PhotoStatus` or `UploadJobStatus`
2. Add behavior method to aggregate: e.g., `photo.markAsRetrying()`
3. Create command handler: `RetryPhotoUploadHandler`
4. Create controller endpoint: `PUT /api/upload/photos/{id}/retry`
5. Update JPA entity mapping
6. Add Flyway migration if schema changes
7. Update frontend service and UI

### Adding a New Query

1. Create query record: `GetPhotosByTagQuery`
2. Create query handler: `GetPhotosByTagHandler` with `@Transactional(readOnly=true)`
3. Add controller endpoint: `GET /api/photos?tag={tag}`
4. Add repository method if needed: `findByUserIdAndTag()`
5. Update frontend service
6. Add integration test

### Debugging S3 Upload Issues

```bash
# Check S3 bucket CORS configuration
aws s3api get-bucket-cors --bucket rapid-photo-upload-demo-photos-xyz

# Test pre-signed URL manually
curl -X PUT --upload-file test.jpg "<presigned-url>"

# View S3 objects
aws s3 ls s3://rapid-photo-upload-demo-photos-xyz/photos/

# Check ECS task logs for S3 errors
aws logs tail /ecs/rapid-photo-upload-demo --follow --filter-pattern "S3"
```

### Monitoring Production Performance

```bash
# View ECS metrics (CPU/Memory)
aws cloudwatch get-metric-statistics \
  --namespace AWS/ECS \
  --metric-name CPUUtilization \
  --dimensions Name=ServiceName,Value=rapid-photo-upload-demo-service \
  --start-time 2025-01-01T00:00:00Z \
  --end-time 2025-01-02T00:00:00Z \
  --period 300 \
  --statistics Average

# View RDS query performance
aws rds describe-db-log-files --db-instance-identifier rapid-photo-upload-demo-db
```

## Important Implementation Details

### JWT Authentication Flow

1. Client: POST /api/auth/login with {email, password}
2. Backend: Validates credentials, generates JWT with userId claim
3. Backend: Returns {token, userId, email, expiresIn: 3600}
4. Client: Stores token in localStorage (web) or AsyncStorage (mobile)
5. Client: Adds `Authorization: Bearer <token>` header to all requests
6. Backend: `JwtAuthenticationFilter` validates token on every request
7. Backend: Extracts userId from token, available in controllers via `@AuthenticationPrincipal`

**Security Notes:**
- Passwords hashed with BCrypt
- JWT secret must be configured in application.yml
- Token expiry: 24 hours (configurable)

### Batch Insert Optimization

When creating 100 photos:
```java
// ✅ CORRECT: Single batch insert
List<Photo> photos = uploadJob.getPhotos();
photoRepository.saveAll(photos); // Uses JPA batch insert

// ❌ WRONG: 100 individual inserts
for (Photo photo : photos) {
    photoRepository.save(photo); // Slow!
}
```

Configure in application.yml:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100
        order_inserts: true
```

### Pre-Signed URL Expiry Handling

- Upload URLs: 1 hour expiry (sufficient for user to select and upload)
- Download URLs: 1 hour expiry (generated on-demand per gallery request)
- If URL expires, client must call initialize/getPhotos again

## Environment Configuration

### Local Development

Backend requires:
- Java 17+
- PostgreSQL 15+ (use Docker: `docker run -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15`)
- AWS credentials with S3 access (or LocalStack for testing)

Create `backend/src/main/resources/application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/photoupload
    username: postgres
    password: postgres

aws:
  region: us-west-1
  s3:
    bucket-name: local-test-bucket

jwt:
  secret: your-development-secret-key-change-in-production
```

Web client `.env`:
```
VITE_API_BASE_URL=http://localhost:8080
```

### Production (AWS)

Environment variables injected by Terraform into ECS task definition:
- `SPRING_DATASOURCE_URL`: RDS endpoint
- `SPRING_DATASOURCE_USERNAME`: photoadmin
- `SPRING_DATASOURCE_PASSWORD`: (from Secrets Manager)
- `AWS_S3_BUCKET_NAME`: (from Terraform output)
- `AWS_REGION`: us-west-1
- `JWT_SECRET`: (from environment variable)

## Troubleshooting

### "Health check failed" on ECS deployment

1. Check logs: `aws logs tail /ecs/rapid-photo-upload-demo --follow`
2. Verify database connectivity from ECS task
3. Ensure Flyway migrations completed successfully
4. Check security group rules (ECS → RDS on port 5432)

### "100 uploads timing out"

1. Check S3 bucket CORS configuration allows PUT from your domain
2. Verify pre-signed URLs not expired (1 hour limit)
3. Monitor ECS task metrics (CPU/memory not maxed)
4. Check database connection pool size (default: 10, may need to increase)

### "Integration tests failing with Testcontainers"

1. Ensure Docker daemon is running
2. Increase Docker memory allocation (4GB minimum)
3. Check Testcontainers logs for image pull errors
4. Verify LocalStack container started successfully

## Cost Optimization Tips

For demo/development (to minimize AWS costs):

1. Use Terraform `terraform destroy` when not actively testing
2. Consider RDS Serverless v2 (only charged when active)
3. Use S3 Lifecycle policies to auto-delete old test photos
4. Turn off NAT Gateway (use VPC endpoints for S3 access instead)
5. Reduce ECS task count to 1 for non-production

## References

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [AWS S3 Pre-Signed URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html)
- [React Query for State Management](https://tanstack.com/query/latest)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)

---

**Key Principle:** The system achieves high performance through architectural decisions (direct S3 uploads, client-side progress, proper indexing) rather than complex optimizations. Keep the design simple and focus on proper domain modeling.

## Development Workflow

### Progress Tracking

This project uses a dual-tracking system for task management:

1. **TodoWrite Tool** (active session tracking)
   - Used for real-time task tracking during Claude Code sessions
   - Provides immediate visibility into current progress
   - Only shows high-level Phase 1-5 tasks

2. **PROJECT_CHECKLIST.md** (persistent documentation)
   - Markdown file with all 35 tasks and 191 sub-tasks
   - Must be manually updated when tasks are completed
   - Mark completed tasks with:
     - Add ✅ to task heading
     - Add `**Status:** COMPLETED` line
     - Change `[ ]` to `[x]` for all sub-tasks

**IMPORTANT:** When completing a task, you MUST update both:
1. Mark task as `completed` in TodoWrite tool
2. Update corresponding section in PROJECT_CHECKLIST.md with checkmarks

### Example Workflow

```markdown
# Before:
### Task 1.1: Initialize Terraform Configuration
**Estimate:** 2 hours
**Dependencies:** None

- [ ] Create `terraform/` directory
- [ ] Run `terraform init` successfully

# After:
### Task 1.1: Initialize Terraform Configuration ✅
**Estimate:** 2 hours
**Dependencies:** None
**Status:** COMPLETED

- [x] Create `terraform/` directory
- [x] Run `terraform init` successfully
```

### AWS Tagging Strategy

All AWS resources are automatically tagged via provider `default_tags` configuration in `terraform/main.tf`:

```hcl
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
```

**Common Tags Applied:**
- `Project`: Project name (rapid-photo-upload-peterp)
- `Environment`: Environment name (demo/dev/prod)
- `ManagedBy`: "Terraform"
- `Owner`: "DevOps"
- `CostCenter`: "Engineering"
- `Application`: "RapidPhotoUpload"

**Why Tags Matter in AWS:**
- AWS Resource Groups are **tag-based queries** (unlike Azure's container-based Resource Groups)
- Enables cost allocation tracking
- Facilitates resource filtering and organization
- Supports automated lifecycle management
- Enables tag-based access control policies

### Terraform Execution

When running Terraform commands, use the full path to the mise-installed binary:

```bash
cd terraform
/Users/ppiont/.local/share/mise/installs/terraform/1.13.5/terraform init
/Users/ppiont/.local/share/mise/installs/terraform/1.13.5/terraform plan
/Users/ppiont/.local/share/mise/installs/terraform/1.13.5/terraform apply
```

Or add an alias to your shell:
```bash
alias tf='/Users/ppiont/.local/share/mise/installs/terraform/1.13.5/terraform'
```

### AWS Region Selection

**Current Configuration:** us-west-1 (N. Virginia)

**Why us-west-1 for Austin, Texas:**
- Officially recommended by UT Austin ITS for Texas users
- Provides "quickest access speeds" based on their testing
- ~35-45ms latency from Austin
- Most mature AWS region (new services deployed here first)
- Best service availability

**Alternative:** us-east-2 (Ohio) saves ~5-10ms latency but has fewer cutting-edge services. The performance difference is negligible for this application's requirements.

