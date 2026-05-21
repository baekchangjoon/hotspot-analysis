package io.github.baekchangjoon.hotspotanalysis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test verifying the Spring Boot application context bootstraps successfully.
 *
 * <p>This is the minimal verification for T1 (project scaffolding): if the context
 * fails to load, no further functional tests can run.</p>
 */
@SpringBootTest(args = {"--help"})
class HotspotApplicationTest {

    @Test
    void contextLoads() {
        // Empty body is intentional: the Spring annotation drives the verification.
    }
}
