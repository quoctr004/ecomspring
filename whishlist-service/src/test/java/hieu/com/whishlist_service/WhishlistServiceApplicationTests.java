package hieu.com.whishlist_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the wishlist-service scaffold.
 *
 * <p><b>SCAFFOLD STATUS</b>: this service has no business logic yet —
 * just the Spring Boot main class. Tests below cover what exists:
 * context startup + class wiring. When business logic lands (controller,
 * service, repository), add proper unit and integration tests alongside.
 */
@SpringBootTest
@DisplayName("WhishlistService — scaffold smoke tests")
class WhishlistServiceApplicationTests {

    @Test
    @DisplayName("Spring context loads — catches misconfigured beans / missing properties")
    void contextLoads() {
        // Failure here means Spring cannot wire the context (bad yaml, missing bean,
        // version mismatch). Passing this test = the app at least boots.
    }

    @Test
    @DisplayName("Main application class is discoverable as @SpringBootApplication")
    void mainAppClassMetadataIsPresent() {
        assertThat(WhishlistServiceApplication.class.getSimpleName())
                .isEqualTo("WhishlistServiceApplication");
        assertThat(WhishlistServiceApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .isTrue();
    }
}
