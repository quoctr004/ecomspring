package hieu.com.review_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the review-service scaffold.
 *
 * <p><b>SCAFFOLD STATUS</b>: this service has no business logic yet — just
 * the Spring Boot main class. Tests below cover what exists: context startup
 * + class wiring. When business logic lands (controller, service, repository),
 * add proper unit and integration tests alongside.
 */
@SpringBootTest
@DisplayName("ReviewServiceApplication — scaffold smoke tests")
class ReviewServiceApplicationTests {

    @Test
    @DisplayName("Spring context loads")
    void contextLoads() {
        // Passing this test = the app at least boots with current config.
    }

    @Test
    @DisplayName("Main class is annotated with @SpringBootApplication")
    void mainAppClassMetadataIsPresent() {
        assertThat(ReviewServiceApplication.class.getSimpleName())
                .isEqualTo("ReviewServiceApplication");
        assertThat(ReviewServiceApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .isTrue();
    }
}
