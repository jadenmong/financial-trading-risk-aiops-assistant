package com.jadenmong.riskaiops.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("productionReadiness")
public class ProductionReadinessHealthIndicator implements HealthIndicator {
    private final Environment environment;

    public ProductionReadinessHealthIndicator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Health health() {
        String mode = property("app.runtime-mode", "reference");
        if (!"production".equalsIgnoreCase(mode)) return Health.up().withDetail("mode", mode).build();
        List<String> violations = violations();
        if (!violations.isEmpty()) return Health.down().withDetail("mode", mode).withDetail("violations", violations).build();
        return Health.up().withDetail("mode", mode).build();
    }

    private List<String> violations() {
        List<String> values = new ArrayList<>();
        if (bool("app.reference-mode")) values.add("REFERENCE_MODE must be false");
        if (bool("REFERENCE_AUTH")) values.add("REFERENCE_AUTH must be false");
        if ("sample".equalsIgnoreCase(property("RISK_CORE_MODE", ""))) values.add("RISK_CORE_MODE=sample is forbidden");
        if ("fake".equalsIgnoreCase(property("MODEL_PROVIDER", ""))) values.add("MODEL_PROVIDER=fake is forbidden");
        if (property("spring.security.oauth2.resourceserver.jwt.issuer-uri", "").contains("localhost")) values.add("OIDC issuer must not be localhost");
        if (blank(property("app.minio.access-key", "")) || blank(property("app.minio.secret-key", ""))) values.add("immutable object store credentials are required");
        if (!"licensed".equalsIgnoreCase(property("app.market-source", "reference"))) values.add("market source must be licensed");
        if (!bool("app.require-source-mtls")) values.add("source mTLS guard must be required");
        String clientAuth = property("server.ssl.client-auth", "");
        if (!"need".equalsIgnoreCase(clientAuth)) values.add("server.ssl.client-auth must be need");
        return values;
    }

    private boolean bool(String name) {
        return Boolean.parseBoolean(property(name, "false"));
    }

    private String property(String name, String fallback) {
        String value = environment.getProperty(name);
        return value == null ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
