package com.demo.photoupload;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * Abstract base class for integration tests using Testcontainers.
 * <p>
 * This class sets up:
 * - PostgreSQL container for database tests
 * - LocalStack container for S3 tests
 * - TestRestTemplate for HTTP requests
 * - Random port for the embedded web server
 * <p>
 * Tests extending this class will have access to a real database and S3 environment,
 * with Flyway migrations applied automatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * PostgreSQL container - shared across all integration tests.
     * Using PostgreSQL 17 to match production environment.
     */
    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:17")
    )
            .withDatabaseName("photoupload")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(true); // Reuse container across test classes for faster execution

    /**
     * LocalStack container for S3 simulation.
     * Using LocalStack 3.0 for compatibility with latest AWS SDK.
     */
    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle
    static final LocalStackContainer localStackContainer = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0")
    )
            .withServices(S3)
            .withReuse(true);

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * Configure dynamic properties for LocalStack S3 endpoint.
     * This allows tests to connect to the LocalStack S3 service instead of real AWS.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL connection is handled by Testcontainers JDBC URL in application-test.yml

        // Configure LocalStack S3 endpoint
        registry.add("aws.s3.endpoint", () -> localStackContainer.getEndpointOverride(S3).toString());
        registry.add("aws.region", () -> localStackContainer.getRegion());
        registry.add("aws.accessKeyId", () -> localStackContainer.getAccessKey());
        registry.add("aws.secretAccessKey", () -> localStackContainer.getSecretKey());
    }

    /**
     * Base URL for making HTTP requests to the application.
     */
    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Get the S3 bucket name used in tests.
     */
    protected String getTestBucketName() {
        return "test-bucket";
    }

    /**
     * Optional: Override this method in subclasses to set up test-specific data before each test.
     */
    @BeforeEach
    void setUpBase() {
        // Subclasses can override to add custom setup
    }
}
