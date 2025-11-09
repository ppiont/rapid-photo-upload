package com.demo.photoupload;

import com.demo.photoupload.infrastructure.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Abstract base class for controller tests using @WebMvcTest.
 * <p>
 * This class provides:
 * - MockMvc for testing controllers without starting full application
 * - ObjectMapper for JSON serialization/deserialization
 * - Mocked JwtService for authentication
 * <p>
 * Tests extending this class should mock the specific handlers/services
 * their controller depends on using @MockBean.
 */
@WebMvcTest
@ActiveProfiles("test")
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Mock JwtService to avoid requiring real JWT validation in controller tests.
     * Subclasses can configure this mock as needed.
     */
    @MockBean
    protected JwtService jwtService;

    /**
     * Helper method to create an Authorization header with a test JWT token.
     *
     * @param userId User ID to include in the token
     * @return Authorization header value (e.g., "Bearer test-token-123")
     */
    protected String authHeader(String userId) {
        return "Bearer test-token-" + userId;
    }

    /**
     * Helper method to convert an object to JSON string.
     *
     * @param obj Object to serialize
     * @return JSON string
     */
    protected String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
