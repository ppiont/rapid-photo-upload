# RapidPhotoUpload Testing Implementation Plan

**Project:** Backend Testing Suite
**Target:** >80% code coverage for application and web layers
**Status:** Phase 2 in progress (70% complete)
**Last Updated:** 2025-11-09

---

## Executive Summary

### Current Status
- **47 unit tests** implemented and passing
- **Phase 1 (Infrastructure):** ✅ COMPLETE
- **Phase 2 (Unit Tests):** 🟡 70% COMPLETE (7/10 handlers)
- **Phase 3-6:** 🔴 NOT STARTED

### Test Infrastructure Created (Phase 1)
1. `application-test.yml` - Testcontainers configuration (PostgreSQL + LocalStack)
2. `AbstractIntegrationTest.java` - Base class for @SpringBootTest with containers
3. `AbstractControllerTest.java` - Base class for @WebMvcTest with MockMvc
4. `TestDataFactory.java` - 250+ lines of test data factory methods
5. `TestAuthHelper.java` - JWT token generation utilities

---

## Phase 2: Unit Tests Progress (70% Complete)

### ✅ Completed Handler Tests (47 tests total)

#### Command Handlers (5/7 complete)

1. **InitializeUploadHandlerTest** - 6 tests
   - Single photo upload
   - Multiple photos (3 photos)
   - Large batch (100 photos)
   - Correct data verification
   - Pre-signed URL generation
   - S3 bucket usage verification

2. **StartPhotoUploadHandlerTest** - 6 tests
   - Mark photo as UPLOADING
   - Save photo after marking as started
   - Photo not found exception
   - Parse photo ID from command
   - PENDING → UPLOADING transition
   - Repository method call ordering

3. **CompletePhotoUploadHandlerTest** - 7 tests
   - Complete photo and update job
   - Update job status after completion
   - Photo not found exception
   - Upload job not found exception
   - UPLOADING → COMPLETED transition
   - Repository call ordering
   - Job stays IN_PROGRESS when some photos pending

4. **FailPhotoUploadHandlerTest** - 5 tests
   - Mark photo as FAILED and update job
   - Update job to PARTIAL_FAILURE
   - Photo not found exception
   - UPLOADING → FAILED transition
   - Repository call ordering

5. **AddPhotoTagsHandlerTest** - 9 tests
   - Add new tags to photo
   - Skip duplicate tags
   - Trim and validate tag names
   - Skip empty/blank tags
   - Reject tags exceeding max length (100 chars)
   - Photo not found exception
   - Photo ownership verification
   - Invalid photo ID format
   - No save when all tags are duplicates

6. **RemovePhotoTagHandlerTest** - 7 tests
   - Remove tag from photo
   - Idempotent when tag doesn't exist
   - Trim tag name before removal
   - Photo not found exception
   - Photo ownership verification
   - Empty tag name rejection
   - Invalid photo ID format

#### Query Handlers (1/3 complete)

7. **GetUploadJobStatusHandlerTest** - 7 tests
   - Retrieve upload job status
   - Correct photo statuses in response
   - COMPLETED status when all photos done
   - PARTIAL_FAILURE status with mixed results
   - Job not found exception
   - Include all photo details in response
   - Handle job with no photos started

### 📋 Remaining Handler Tests (3 handlers, ~20-25 tests needed)

#### Command Handler (1 remaining)

**DeletePhotoHandler** - Estimated 6-8 tests needed
- File: `src/main/java/.../commands/DeletePhotoHandler.java`
- Dependencies: `PhotoRepository`, `S3Service`
- Test scenarios:
  - Successfully delete photo and S3 object
  - Photo not found exception
  - Photo ownership verification
  - S3 deletion error handling
  - Repository call ordering
  - Invalid photo ID format
  - Verify both DB and S3 cleanup

#### Query Handlers (2 remaining)

**GetPhotosHandler** - Estimated 8-10 tests needed
- File: `src/main/java/.../queries/GetPhotosHandler.java`
- Dependencies: `PhotoRepository`, `PhotoTagJpaRepository`, `S3Service`
- Test scenarios:
  - Get paginated photos for user
  - Generate download URLs for all photos
  - Batch-fetch tags for photos
  - Empty result set handling
  - Pagination edge cases (first page, last page)
  - Photo filtering by status
  - Sort order verification (created_at DESC)
  - Tag inclusion in response

**GetPhotoByIdHandler** - Estimated 6-7 tests needed
- File: `src/main/java/.../queries/GetPhotoByIdHandler.java`
- Dependencies: `PhotoRepository`, `PhotoTagJpaRepository`, `S3Service`
- Test scenarios:
  - Get photo by ID with tags
  - Generate download URL
  - Photo not found exception
  - Photo ownership verification
  - Include all photo details
  - Invalid photo ID format

---

## Phase 3: Controller Integration Tests (NOT STARTED)

**Estimated:** 3 controller test classes, ~40-50 tests total

### Controllers to Test

#### 1. AuthControllerTest (@WebMvcTest)
**File:** `src/main/java/.../web/auth/AuthController.java`

Test scenarios (12-15 tests):
- POST /api/auth/register
  - Successful registration
  - Duplicate email rejection
  - Validation errors (email format, password strength)
  - Missing required fields
- POST /api/auth/login
  - Successful login with JWT token
  - Invalid credentials
  - Non-existent user
  - Account inactive handling
- JWT token format validation

#### 2. UploadControllerTest (@WebMvcTest)
**File:** `src/main/java/.../web/upload/UploadController.java`

Test scenarios (15-20 tests):
- POST /api/upload/initialize
  - Initialize with 1-100 photos
  - Authentication required (401 without token)
  - Validation errors (empty photo list, invalid metadata)
- PUT /api/upload/photos/{id}/start
  - Successful start
  - Authentication/authorization
  - Photo not found
- PUT /api/upload/photos/{id}/complete
  - Successful completion
  - Authentication/authorization
- PUT /api/upload/photos/{id}/fail
  - Mark as failed
- GET /api/upload/jobs/{id}/status
  - Get status with all photos
  - Job not found

#### 3. PhotosControllerTest (@WebMvcTest)
**File:** `src/main/java/.../web/photos/PhotosController.java`

Test scenarios (15-20 tests):
- GET /api/photos
  - Get paginated photos
  - Authentication required
  - Pagination parameters
  - Empty result handling
- GET /api/photos/{id}
  - Get single photo
  - Photo not found
  - Authorization (not owned by user)
- POST /api/photos/{id}/tags
  - Add tags
  - Validation errors
- DELETE /api/photos/{id}/tags/{tagName}
  - Remove tag
- DELETE /api/photos/{id}
  - Delete photo

### Implementation Approach
- Use `@WebMvcTest` for focused controller testing
- Mock all handler dependencies
- Use `MockMvc` for HTTP request simulation
- Use `TestAuthHelper` for JWT token generation
- Verify request/response DTOs
- Test error responses (4xx, 5xx)

---

## Phase 4: S3Service Integration Tests (NOT STARTED)

**Estimated:** 1 test class, 10-15 tests

### S3ServiceIntegrationTest
**File:** `src/main/java/.../infrastructure/s3/service/S3Service.java`

Test scenarios:
- Generate pre-signed upload URL (1 hour expiry)
- Generate pre-signed download URL (1 hour expiry, inline disposition)
- Upload file to S3 using pre-signed URL
- Download file from S3 using pre-signed URL
- Delete object from S3
- URL expiry validation
- Invalid bucket/key handling
- S3 error handling (network failures)
- Concurrent upload operations
- Large file handling

### Implementation Approach
- Use `LocalStack` Testcontainer for S3 emulation
- Extend `AbstractIntegrationTest`
- Test with actual S3 operations (not mocked)
- Verify pre-signed URLs work end-to-end
- Test error scenarios (invalid URLs, expired tokens)

---

## Phase 5: Full System Integration Tests (NOT STARTED)

**Estimated:** 2-3 test classes, 25-30 tests

### Critical Test Scenarios

#### 1. UploadWorkflowIntegrationTest
**Purpose:** Test complete upload flow with real DB + S3

Test scenarios (10-12 tests):
- **100-photo concurrent upload test** (CRITICAL)
  - Initialize upload with 100 files
  - Use CompletableFuture for parallel execution
  - Simulate S3 uploads to LocalStack
  - Verify job status transitions (IN_PROGRESS → COMPLETED)
  - Verify all photos reach terminal state
  - Performance: Complete in <10 seconds
- Upload with mixed success/failure
- Partial upload (some photos fail)
- Job status polling simulation
- Concurrent uploads from multiple users
- Upload interruption recovery

#### 2. AuthenticationIntegrationTest
**Purpose:** Test auth flow with real database

Test scenarios (8-10 tests):
- Complete registration → login → authenticated request flow
- JWT validation with real tokens
- Token expiry handling
- Invalid credentials
- Duplicate registration prevention
- Password hashing verification
- Session management
- Role-based access (if implemented)

#### 3. PhotoManagementIntegrationTest
**Purpose:** Test photo queries and operations with real data

Test scenarios (10-12 tests):
- Upload → tag → query → delete flow
- Pagination with large datasets (500+ photos)
- Tag operations (add, remove, query by tag)
- Photo ownership enforcement
- Download URL generation
- S3 cleanup verification
- Concurrent tag operations
- Photo filtering and sorting

### Implementation Approach
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Extend `AbstractIntegrationTest` (PostgreSQL + LocalStack)
- Use `TestRestTemplate` for HTTP requests
- Real database with Flyway migrations
- Real S3 operations via LocalStack
- Test transaction rollback behavior
- Verify data consistency

---

## Phase 6: Coverage Analysis & Verification (NOT STARTED)

**Estimated:** 1-2 hours

### Steps

1. **Generate JaCoCo Report**
   ```bash
   mvn test jacoco:report
   open backend/target/site/jacoco/index.html
   ```

2. **Analyze Coverage by Layer**
   - Application layer (commands, queries): Target >90%
   - Web layer (controllers): Target >85%
   - Domain layer: Target >80%
   - Infrastructure layer: Target >70%

3. **Identify Gaps**
   - Uncovered branches in handlers
   - Error handling paths not tested
   - Edge cases missed

4. **Add Missing Tests**
   - Write tests for uncovered critical paths
   - Focus on error scenarios
   - Test concurrent operations

5. **Verify Target Met**
   - Overall coverage >80% ✅
   - Application layer >90% ✅
   - Web layer >85% ✅

### Coverage Exclusions
- JPA entities (infrastructure/persistence/entity)
- Configuration classes
- DTOs and record classes
- Main application class

---

## Test Execution Strategy

### Running Tests

```bash
# Run all tests
mvn test

# Run unit tests only (fast, ~3 seconds)
mvn test -Dtest="*Test"

# Run handler unit tests
mvn test -Dtest="*HandlerTest"

# Run integration tests only (slow, ~10-15 seconds)
mvn test -Dtest="*IntegrationTest"

# Run specific test class
mvn test -Dtest=InitializeUploadHandlerTest

# Generate coverage report
mvn test jacoco:report
```

### CI/CD Integration
- Unit tests: Run on every commit (fast feedback)
- Integration tests: Run on PR creation
- Coverage report: Publish to PR comments
- Fail build if coverage <80%

---

## Key Testing Principles

### Unit Tests
- **Fast:** <5 seconds total execution
- **Isolated:** Mock all dependencies
- **Focused:** Test one behavior per test
- **Readable:** Clear test names and assertions

### Integration Tests
- **Real dependencies:** Actual DB + S3 (via containers)
- **Comprehensive:** Test full workflows
- **Isolated:** Clean state between tests
- **Performance:** Verify <90s for 100-photo upload

### Test Data
- Use `TestDataFactory` for consistent test data
- Use `TestAuthHelper` for JWT tokens
- Avoid hardcoded UUIDs (use generated values)
- Clean up test data after each test

---

## Common Test Patterns

### Unit Test Pattern (Handler)
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("HandlerName Tests")
class HandlerNameTest {
    @Mock private Repository repository;
    @Mock private Service service;

    private HandlerName handler;

    @BeforeEach
    void setUp() {
        handler = new HandlerName(repository, service);
    }

    @Test
    @DisplayName("Should successfully do something")
    void shouldDoSomething() {
        // Arrange
        var command = new Command(...);
        when(repository.findById(any())).thenReturn(Optional.of(...));

        // Act
        handler.handle(command);

        // Assert
        verify(repository).save(any());
    }
}
```

### Integration Test Pattern
```java
class FeatureIntegrationTest extends AbstractIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should complete end-to-end workflow")
    void shouldCompleteWorkflow() {
        // Arrange - create user and authenticate
        String token = TestAuthHelper.generateToken(userId);

        // Act - make HTTP requests
        var response = restTemplate
            .postForEntity("/api/endpoint", request, ResponseDto.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

---

## Troubleshooting Common Issues

### Testcontainers Not Starting
- Ensure Docker Desktop is running
- Increase Docker memory to 4GB+
- Check for port conflicts (5432, 4566)

### Tests Failing Randomly
- Check for test data isolation issues
- Verify @Transactional rollback
- Look for shared state between tests

### Slow Test Execution
- Ensure unit tests use mocks (not real DB)
- Limit integration test scope
- Use test containers reuse feature

---

## Next Steps

1. **Complete Phase 2** (Estimated: 2 hours)
   - Create DeletePhotoHandlerTest
   - Create GetPhotosHandlerTest
   - Create GetPhotoByIdHandlerTest
   - Run all tests: expect 65-70 tests passing

2. **Start Phase 3** (Estimated: 3-4 hours)
   - Create AuthControllerTest
   - Create UploadControllerTest
   - Create PhotosControllerTest

3. **Complete Phase 4** (Estimated: 1-2 hours)
   - Create S3ServiceIntegrationTest
   - Test with LocalStack

4. **Complete Phase 5** (Estimated: 3-4 hours)
   - Create UploadWorkflowIntegrationTest
   - Create AuthenticationIntegrationTest
   - Create PhotoManagementIntegrationTest

5. **Phase 6: Verify Coverage** (Estimated: 1 hour)
   - Generate report
   - Add missing tests
   - Achieve >80% coverage ✅

**Total Estimated Time Remaining:** 10-13 hours

---

## Success Criteria

- [x] Phase 1: Test infrastructure complete
- [ ] Phase 2: 10 handler test classes (currently 7/10)
- [ ] Phase 3: 3 controller test classes
- [ ] Phase 4: S3Service integration tests
- [ ] Phase 5: 3 full integration test classes
- [ ] Phase 6: >80% code coverage verified
- [ ] All tests passing (target: 100-120 tests)
- [ ] Fast unit tests (<5 seconds)
- [ ] Integration tests complete in <30 seconds

---

**End of Testing Plan**
