package com.jadenmong.riskaiops.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, @Value("${app.reference-mode:false}") boolean referenceMode) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/internal/**"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        if (referenceMode) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())));
        }
        return http.build();
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");
        scopes.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        return converter;
    }

    @Bean
    AccountAccessGuard accountAccessGuard(@Value("${app.reference-mode:false}") boolean referenceMode) {
        return new AccountAccessGuard(referenceMode);
    }

    @Bean
    @ConditionalOnProperty(name = "app.reference-mode", havingValue = "false", matchIfMissing = true)
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                          @Value("${app.jwt-audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();
        JwtClaimValidator<Object> audienceValidator = new JwtClaimValidator<>("aud", claim ->
                claim instanceof String value && value.equals(audience)
                        || claim instanceof java.util.Collection<?> values && values.contains(audience));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    public static final class AccountAccessGuard {
        private final boolean referenceMode;

        public AccountAccessGuard(boolean referenceMode) {
            this.referenceMode = referenceMode;
        }

        public boolean allowed(org.springframework.security.core.Authentication authentication, String accountId, String scope) {
            if (referenceMode) return true;
            if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return false;
            List<String> accounts = jwt.getClaimAsStringList("accounts");
            return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("SCOPE_" + scope))
                    && (accountId == null || accounts != null && accounts.contains(accountId));
        }
    }
}
