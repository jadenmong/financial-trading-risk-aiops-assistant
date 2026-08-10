package com.jadenmong.riskaiops.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mock.env.MockEnvironment;

class ProductionReadinessHealthIndicatorTest {
    @Test
    void referenceModeDoesNotApplyProductionGuards() {
        var environment = new MockEnvironment().withProperty("app.runtime-mode", "reference");
        assertThat(new ProductionReadinessHealthIndicator(environment).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void productionFailsReadinessWhenReferencePathsAreEnabled() {
        var environment = new MockEnvironment()
                .withProperty("app.runtime-mode", "production")
                .withProperty("app.reference-mode", "true")
                .withProperty("REFERENCE_AUTH", "true")
                .withProperty("RISK_CORE_MODE", "sample")
                .withProperty("MODEL_PROVIDER", "fake")
                .withProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri", "http://localhost:8081/realms/risk-aiops")
                .withProperty("app.market-source", "reference")
                .withProperty("app.require-source-mtls", "false");
        assertThat(new ProductionReadinessHealthIndicator(environment).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
