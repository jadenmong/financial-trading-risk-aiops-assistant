package com.jadenmong.riskaiops.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityConfigTest {
    private final SecurityConfig.AccountAccessGuard productionGuard = new SecurityConfig.AccountAccessGuard(false);

    @Test
    void productionGuardFailsClosedWithoutJwtAuthentication() {
        assertFalse(productionGuard.allowed(null, "ACC_ALPHA_01", "risk:read"));
    }

    @Test
    void productionGuardRequiresScopeAndAccountClaim() {
        assertTrue(productionGuard.allowed(authentication("ACC_ALPHA_01", "risk:read"), "ACC_ALPHA_01", "risk:read"));
        assertFalse(productionGuard.allowed(authentication("ACC_ALPHA_01", "risk:read"), "ACC_BETA_01", "risk:read"));
        assertFalse(productionGuard.allowed(authentication("ACC_ALPHA_01", "market:read"), "ACC_ALPHA_01", "risk:read"));
    }

    @Test
    void referenceGuardAllowsDeterministicLocalRequests() {
        assertTrue(new SecurityConfig.AccountAccessGuard(true).allowed(null, "ACC_ALPHA_01", "risk:read"));
    }

    private static JwtAuthenticationToken authentication(String accountId, String scope) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("accounts", List.of(accountId))
                .claim("scope", scope)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
