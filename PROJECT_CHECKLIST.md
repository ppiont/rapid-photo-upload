# RapidPhotoUpload Project Checklist

**Project Duration:** 5 days
**Key Performance Target:** 100 photos (200MB) upload in <90 seconds @ 25 Mbps

---

## Phase 1: Infrastructure Setup (1.5 days)

### Task 1.1: Initialize Terraform Configuration ✅
**Estimate:** 2 hours
**Dependencies:** None
**Status:** COMPLETED

- [x] Create `terraform/` directory
- [x] Create `terraform/versions.tf` with Terraform 1.13+ and AWS provider 6.19+
- [x] Create `terraform/variables.tf` with all required variables
- [x] Create `terraform/main.tf` with locals and tags
- [x] Run `terraform init` successfully
- [x] Define variables: aws_region, project_name, environment, db_password, container_image

---

### Task 1.2: Provision VPC and Network Resources ✅
**Estimate:** 3 hours
**Dependencies:** Task 1.1
**Status:** COMPLETED

- [x] Create `terraform/network.tf`
- [x] Create VPC (10.0.0.0/16) with DNS support
- [x] Create 2 public subnets in different AZs (10.0.0.0/24, 10.0.1.0/24)
- [x] Create 2 private subnets in different AZs (10.0.10.0/24, 10.0.11.0/24)
- [x] Create and attach Internet Gateway
- [x] Create Elastic IP for NAT Gateway
- [x] Create NAT Gateway in first public subnet
- [x] Create public route table (route to IGW)
- [x] Create private route table (route to NAT)
- [x] Associate subnets with route tables
- [x] Create security groups for ALB, ECS, RDS

---

### Task 1.3: Provision RDS PostgreSQL Database ✅
**Estimate:** 2 hours
**Dependencies:** Task 1.2
**Status:** COMPLETED

- [x] Create `terraform/data.tf`
- [x] Create DB subnet group with private subnets
- [x] Configure RDS instance (PostgreSQL 17.6, db.t3.micro)
- [x] Configure storage (20GB gp3 with auto-scaling to 50GB)
- [x] Set database name = "photoupload"
- [x] Configure credentials (username, password from variable)
- [x] Associate with security group
- [x] Disable public accessibility
- [x] Configure backup settings
- [x] Add output for RDS endpoint

---

### Task 1.4: Provision S3 Bucket for Photos ✅
**Estimate:** 1 hour
**Dependencies:** None
**Status:** COMPLETED

- [x] Create S3 bucket resource with unique name
- [x] Configure public access block (all blocked)
- [x] Add CORS configuration (PUT, GET, HEAD methods)
- [x] Set allowed origins to * (restrict in production)
- [x] Set allowed headers to *
- [x] Expose ETag header
- [x] Add output for bucket name

---

### Task 1.5: Provision ECS Cluster and ALB ✅
**Estimate:** 4 hours
**Dependencies:** Task 1.2, Task 1.3, Task 1.4
**Status:** COMPLETED

- [x] Create `terraform/compute.tf`
- [x] Create ECS Fargate cluster with container insights
- [x] Create CloudWatch log group
- [x] Create IAM execution role (for ECR, CloudWatch)
- [x] Create IAM task role (for S3 access)
- [x] Attach policies to roles
- [x] Create task definition (1 vCPU, 2GB RAM, port 8080)
- [x] Configure environment variables (DB URL, S3 bucket, AWS region)
- [x] Configure secret for DB password
- [x] Configure log configuration and health check
- [x] Create ALB in public subnets
- [x] Create target group (port 8080, health check /actuator/health)
- [x] Create HTTP listener (port 80 → target group)
- [x] Create ECS service (2 desired tasks)
- [x] Add outputs (ALB URL, cluster name, service name)

---

### Task 1.6: Apply Terraform and Verify Infrastructure ✅
**Estimate:** 2 hours
**Dependencies:** Tasks 1.1-1.5
**Status:** COMPLETED

- [x] Create `terraform.tfvars` with values
- [x] Run `terraform plan` and review
- [x] Run `terraform apply`
- [x] Verify resources in AWS console
- [x] Check ALB DNS resolves
- [x] Verify ECS tasks are running
- [x] Test database connection from ECS task
- [x] Generate test S3 pre-signed URL
- [x] Document outputs (ALB URL, bucket name, DB endpoint)

---

## Phase 2: Backend Development (1.5 days)

### Task 2.1: Set Up Spring Boot Project Structure ✅
**Estimate:** 1 hour
**Dependencies:** Task 1.6
**Status:** COMPLETED

- [x] Initialize Spring Boot project with Spring Initializr
- [x] Add dependencies to pom.xml (Spring Web, Data JPA, PostgreSQL, Flyway, AWS SDK S3, Security, JJWT)
- [x] Create package structure (domain/, application/, infrastructure/, web/)
- [x] Configure application.yml (database, S3, Flyway, JWT)
- [x] Create application main class

---

### Task 2.2: Implement Database Migrations with Flyway ✅
**Estimate:** 2 hours
**Dependencies:** Task 2.1
**Status:** COMPLETED

- [x] Create `src/main/resources/db/migration/` directory
- [x] Write V1__create_users_table.sql
- [x] Write V2__create_upload_jobs_table.sql
- [x] Write V3__create_photos_table.sql
- [x] Write V4__create_photo_tags_table.sql
- [x] Test migrations locally with PostgreSQL container
- [x] Verify constraints and indexes

---

### Task 2.3: Implement Domain Layer (DDD) ✅
**Estimate:** 4 hours
**Dependencies:** Task 2.1
**Status:** COMPLETED

- [x] Create `domain/` package
- [x] Implement `UploadJob` aggregate with factory method and behaviors
- [x] Implement `Photo` aggregate with state machine transitions
- [x] Implement value objects: PhotoMetadata, S3Location
- [x] Implement identity objects: UploadJobId, PhotoId, UserId
- [x] Define enums: UploadJobStatus, PhotoStatus
- [x] Create repository interfaces: UploadJobRepository, PhotoRepository
- [x] Write unit tests for domain logic
- [x] Verify NO JPA annotations in domain layer

---

### Task 2.4: Implement Infrastructure Layer (JPA + S3) ✅
**Estimate:** 4 hours
**Dependencies:** Task 2.3
**Status:** COMPLETED

- [x] Create `infrastructure/persistence/` package
- [x] Implement JPA entities (UploadJobEntity, PhotoEntity, PhotoTagEntity, UserEntity)
- [x] Create Spring Data JPA repository interfaces
- [x] Implement repository adapters (domain → JPA)
- [x] Implement mapper classes for domain ↔ entity conversion
- [x] Create `infrastructure/s3/` package
- [x] Configure AWS S3 SDK client and presigner
- [x] Implement S3Service (generatePresignedUploadUrl, generatePresignedDownloadUrl)
- [x] Create S3Config with bucket name from properties
- [ ] Write integration tests with LocalStack

---

### Task 2.5: Implement Application Layer (CQRS Handlers) ✅
**Estimate:** 5 hours
**Dependencies:** Tasks 2.3, 2.4
**Status:** COMPLETED

- [x] Create `application/commands/` package
- [x] Define command record classes
- [x] Implement InitializeUploadHandler (creates job, photos, generates URLs)
- [x] Implement StartPhotoUploadHandler (transitions photo to UPLOADING)
- [x] Implement CompletePhotoUploadHandler (transitions to COMPLETED, updates job)
- [x] Implement FailPhotoUploadHandler (marks photo as FAILED)
- [ ] Implement AddPhotoTagHandler (adds tags to photo) - deferred to later
- [x] Create `application/queries/` package
- [x] Define query record classes
- [x] Implement GetUploadJobStatusHandler (returns job status with photos)
- [x] Implement GetPhotosHandler (returns paginated photo list with download URLs)
- [x] Implement GetPhotoByIdHandler (returns single photo details)
- [x] Use batch operations for 100 photos (saveAll)
- [ ] Write unit tests with mocked repositories - will be done in Task 2.8

---

### Task 2.6: Implement Web Layer (REST Controllers) ✅
**Estimate:** 4 hours
**Dependencies:** Task 2.5
**Status:** COMPLETED

- [x] Create `web/upload/` package
- [x] Implement UploadController (initialize, start, complete, fail, status endpoints)
- [x] Define request/response DTOs
- [x] Create `web/photos/` package
- [x] Implement PhotosController (list, get photo endpoints)
- [ ] Create `web/auth/` package - deferred to Task 2.7
- [ ] Implement AuthController (register, login) - deferred to Task 2.7
- [x] Create `web/common/` package
- [x] Implement GlobalExceptionHandler with @RestControllerAdvice
- [x] Add validation annotations (@Valid, @NotNull, @Pattern, @Size, etc.)
- [ ] Write integration tests with @WebMvcTest - deferred to Task 2.8

---

### Task 2.7: Implement JWT Authentication ✅
**Estimate:** 3 hours
**Dependencies:** Task 2.6
**Status:** COMPLETED

- [x] Add JJWT dependency (already in pom.xml)
- [x] Create `infrastructure/security/` package
- [x] Implement JwtService (generateToken, validateToken, extractUserId)
- [x] Implement JwtAuthenticationFilter extends OncePerRequestFilter
- [x] Implement SecurityConfig
- [x] Configure HTTP security (disable CSRF, stateless session, permit auth endpoints)
- [x] Implement UserService for authentication (instead of UserDetailsService)
- [x] Configure password encoder (BCrypt)
- [x] JWT secret already in application.yml
- [x] Created AuthController with register/login endpoints
- [ ] Test with Postman - deferred to manual testing

---

### Task 2.8: Write Integration Tests
**Estimate:** 4 hours
**Dependencies:** Task 2.7

- [ ] Set up Testcontainers for PostgreSQL
- [ ] Set up LocalStack for S3
- [ ] Create @SpringBootTest test class
- [ ] Implement test: Upload 100 photos concurrently
- [ ] Implement test: Initialize upload
- [ ] Implement test: Use CompletableFuture for concurrency
- [ ] Implement test: Simulate S3 uploads
- [ ] Implement test: Verify job status
- [ ] Implement test: Pagination
- [ ] Implement test: Tagging
- [ ] Implement test: Authentication
- [ ] Run with `mvn test`
- [ ] Verify code coverage >80% for application and web layers

---

## Phase 3: Web Client Development (1 day)

### Task 3.1: Set Up React Project with TypeScript
**Estimate:** 1 hour
**Dependencies:** None

- [ ] Run `npm create vite@latest web -- --template react-ts`
- [ ] Install dependencies: axios, react-router-dom
- [ ] Create directory structure (components/, services/, hooks/, types/)
- [ ] Configure `tsconfig.json` for strict mode
- [ ] Set up `.env.example` for API base URL
- [ ] Configure Vite proxy for local development

---

### Task 3.2: Implement API Service Layer
**Estimate:** 2 hours
**Dependencies:** Tasks 3.1, 2.6

- [ ] Create `services/api.ts` (Axios instance with base URL and JWT interceptor)
- [ ] Implement `authService.ts` (login, register)
- [ ] Implement `uploadService.ts` (initialize, startUpload, completeUpload, getJobStatus)
- [ ] Implement `s3Service.ts` (uploadToS3 with progress callback)
- [ ] Implement `photoService.ts` (getPhotos, getPhotoById, addTags, removeTag)
- [ ] Create `types/` with TypeScript interfaces
- [ ] Test with mock data

---

### Task 3.3: Implement Authentication Components
**Estimate:** 2 hours
**Dependencies:** Task 3.2

- [ ] Create `components/Auth/Login.tsx` with form validation
- [ ] Create `components/Auth/Register.tsx` with form validation
- [ ] Create `hooks/useAuth.ts` (user state, login, register, logout)
- [ ] Store JWT in localStorage
- [ ] Create AuthContext provider
- [ ] Implement protected route wrapper
- [ ] Add React Router setup
- [ ] Style with CSS/Tailwind

---

### Task 3.4: Implement Photo Upload Components
**Estimate:** 4 hours
**Dependencies:** Task 3.2

- [ ] Create `components/PhotoUploader/PhotoUploader.tsx` (main component)
- [ ] Implement file selection handling
- [ ] Call uploadService.initialize()
- [ ] Implement concurrent upload logic with Promise.all
- [ ] Create `FileSelector.tsx` (input type="file" multiple)
- [ ] Create `UploadProgress.tsx` (progress bars, status indicators)
- [ ] Create `UploadQueue.tsx` (list of files with status)
- [ ] Implement `usePhotoUpload` hook
- [ ] Use Axios onUploadProgress callback
- [ ] Handle errors and retry logic
- [ ] Style components
- [ ] Test with 100 test images

---

### Task 3.5: Implement Photo Gallery Components
**Estimate:** 3 hours
**Dependencies:** Task 3.2

- [ ] Create `components/PhotoGallery/PhotoGallery.tsx` (responsive grid)
- [ ] Fetch photos with photoService.getPhotos()
- [ ] Implement pagination
- [ ] Create `PhotoCard.tsx` (thumbnail, filename, size, tags)
- [ ] Create `PhotoModal.tsx` (full-size display, download button)
- [ ] Implement `usePhotos` hook
- [ ] Style with CSS Grid/Flexbox
- [ ] Implement lazy loading

---

### Task 3.6: Implement Tagging Functionality
**Estimate:** 2 hours
**Dependencies:** Task 3.5

- [ ] Create `components/PhotoTags.tsx` (display tags, remove button)
- [ ] Create `components/TagInput.tsx` (input field, add button, Enter key support)
- [ ] Integrate with PhotoModal
- [ ] Call photoService.addTags() and removeTag()
- [ ] Update local state after mutations
- [ ] Style tag badges

---

### Task 3.7: Polish UI and Add Responsive Design
**Estimate:** 2 hours
**Dependencies:** Tasks 3.4, 3.5, 3.6

- [ ] Add CSS media queries for breakpoints
- [ ] Implement mobile navigation (hamburger menu)
- [ ] Add loading spinners
- [ ] Create Toast notification component
- [ ] Add empty state components
- [ ] Test on different screen sizes
- [ ] Add ARIA labels
- [ ] Test keyboard navigation

---

## Phase 4: Mobile Client Development (1 day)

### Task 4.1: Set Up React Native Project
**Estimate:** 1 hour
**Dependencies:** None

- [ ] Run `npx create-expo-app mobile --template expo-template-blank-typescript`
- [ ] Install dependencies (expo-file-system, expo-image-picker, axios, react-navigation)
- [ ] Configure TypeScript
- [ ] Set up navigation structure
- [ ] Create `.env.example` for API base URL

---

### Task 4.2: Implement Shared Services (API Layer)
**Estimate:** 1.5 hours
**Dependencies:** Tasks 4.1, 2.6

- [ ] Copy relevant service files from web project
- [ ] Adapt for React Native (AsyncStorage instead of localStorage)
- [ ] Implement `uploadService.ts` using FileSystem.uploadAsync()
- [ ] Test with Metro bundler

---

### Task 4.3: Implement Authentication Screens
**Estimate:** 2 hours
**Dependencies:** Task 4.2

- [ ] Create `screens/LoginScreen.tsx` with form validation
- [ ] Create `screens/RegisterScreen.tsx` with form validation
- [ ] Create `hooks/useAuth.ts` (use AsyncStorage for token)
- [ ] Set up navigation guards
- [ ] Style with React Native components

---

### Task 4.4: Implement Upload Screen
**Estimate:** 4 hours
**Dependencies:** Task 4.2

- [ ] Create `screens/UploadScreen.tsx` (photo picker button, display selected photos)
- [ ] Create `components/PhotoPicker.tsx` (expo-image-picker.launchImageLibraryAsync)
- [ ] Allow multiple selection (up to 100 photos)
- [ ] Initialize upload
- [ ] Upload to S3 concurrently (batch of 10)
- [ ] Create `components/UploadProgress.tsx` (progress bars, status indicators)
- [ ] Implement `usePhotoUpload` hook
- [ ] Handle permissions for photo library access
- [ ] Test with real device

---

### Task 4.5: Implement Gallery Screen
**Estimate:** 3 hours
**Dependencies:** Task 4.2

- [ ] Create `screens/GalleryScreen.tsx` (fetch photos, pull-to-refresh)
- [ ] Create `components/PhotoGrid.tsx` (FlatList with numColumns, lazy loading)
- [ ] Create `screens/PhotoDetailScreen.tsx` (full-size image, download, tags)
- [ ] Implement download using FileSystem.downloadAsync()
- [ ] Style appropriately

---

### Task 4.6: Implement Tagging Functionality
**Estimate:** 1.5 hours
**Dependencies:** Task 4.5

- [ ] Create `components/TagInput.tsx` (TextInput, add button)
- [ ] Display tags in PhotoDetailScreen
- [ ] Implement remove tag with onPress
- [ ] Call API to add/remove tags
- [ ] Update local state

---

### Task 4.7: Test on Real Devices
**Estimate:** 2 hours
**Dependencies:** Tasks 4.4, 4.5, 4.6

- [ ] Build app for iOS: `expo run:ios`
- [ ] Build app for Android: `expo run:android`
- [ ] Test upload flow with 20+ photos
- [ ] Test gallery and detail screens
- [ ] Test offline behavior (graceful errors)
- [ ] Fix any platform-specific issues

---

## Phase 5: Integration Testing and Demo (1 day)

### Task 5.1: Run Full Integration Test Suite
**Estimate:** 2 hours
**Dependencies:** Tasks 2.8, 3.7, 4.7

- [ ] Run backend tests: `mvn test`
- [ ] Verify all tests pass
- [ ] Review code coverage reports
- [ ] Fix any failing tests
- [ ] Document test results

---

### Task 5.2: End-to-End Testing with Clients
**Estimate:** 3 hours
**Dependencies:** Task 5.1

- [ ] Prepare 100 test images (~2MB each)
- [ ] Test web client: Register new user
- [ ] Test web client: Login
- [ ] Test web client: Upload 100 photos concurrently
- [ ] Monitor progress in real-time
- [ ] Verify all complete
- [ ] View in gallery
- [ ] Add tags
- [ ] Download photo
- [ ] Test mobile client: Login
- [ ] Test mobile client: Upload batch of photos
- [ ] Test mobile client: View gallery
- [ ] Test mobile client: Detail screen
- [ ] Test error cases: Invalid credentials, network interruption, large files
- [ ] Document results

---

### Task 5.3: Performance Testing
**Estimate:** 2 hours
**Dependencies:** Task 5.2

- [ ] Set up network throttling (25 Mbps)
- [ ] Upload 100 photos and measure time
- [ ] Verify <90 seconds completion time
- [ ] Use browser DevTools to measure API response times (p95 <100ms)
- [ ] Check database query performance (p95 <50ms)
- [ ] Monitor ECS task CPU/memory usage
- [ ] Document results

---

### Task 5.4: Build and Deploy Docker Image
**Estimate:** 2 hours
**Dependencies:** Task 5.3

- [ ] Create `Dockerfile` in backend/
- [ ] Build image: `docker build -t rapid-photo-upload:latest .`
- [ ] Tag for ECR
- [ ] Push to ECR
- [ ] Update Terraform variable with image URL
- [ ] Run `terraform apply` to update ECS service
- [ ] Wait for deployment to complete
- [ ] Verify via ALB health check

---

### Task 5.5: Create Demo Video
**Estimate:** 2 hours
**Dependencies:** Task 5.4

- [ ] Prepare script and talking points
- [ ] Set up screen recording software (OBS, QuickTime)
- [ ] Record web client demo (login, select 100 photos, upload with progress, gallery, tags)
- [ ] Record mobile client demo (login, upload photos, view gallery)
- [ ] Edit video with annotations
- [ ] Add audio narration
- [ ] Export and upload (3-5 minutes total)

---

### Task 5.6: Write Technical Documentation
**Estimate:** 2 hours
**Dependencies:** Task 5.5

- [ ] Create `docs/technical-writeup.md`
- [ ] Section 1: Architecture (diagram, component responsibilities)
- [ ] Section 2: Concurrency Strategy (direct S3 uploads, pre-signed URLs, Promise.all)
- [ ] Section 3: Asynchronous Design (client-side progress, polling, non-blocking UI)
- [ ] Section 4: Domain-Driven Design (aggregates, domain behaviors, repository pattern)
- [ ] Section 5: AI Tools (if used - tool name, prompts, impact)
- [ ] Export to PDF

---

### Task 5.7: Prepare Submission Package
**Estimate:** 1 hour
**Dependencies:** Task 5.6

- [ ] Create comprehensive README.md (overview, prerequisites, setup, deployment, API docs)
- [ ] Organize repository structure
- [ ] Add .gitignore files
- [ ] Commit all code
- [ ] Push to GitHub
- [ ] Upload demo video to YouTube (unlisted) or include in repo
- [ ] Add LICENSE file
- [ ] Verify repository is accessible
- [ ] Create release tag (v1.0.0)

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
