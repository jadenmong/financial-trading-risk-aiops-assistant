package com.jadenmong.riskaiops.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Binds the already-authorized account to the current database transaction for FORCE RLS. */
@Service
public class RlsSessionService {
    private final JdbcTemplate jdbc;
    private final boolean referenceMode;
    public RlsSessionService(ObjectProvider<JdbcTemplate> provider, @Value("${app.reference-mode:false}") boolean referenceMode) {
        this.jdbc = provider.getIfAvailable();
        this.referenceMode = referenceMode;
    }
    public void authorize(String accountId) {
        if (jdbc != null) jdbc.queryForObject("select set_config('app.allowed_accounts', ?, true)", String.class, accountId);
    }

    public void authorizeCurrentAccounts() {
        if (jdbc == null) return;
        if (referenceMode) {
            authorize("ACC_ALPHA_01,ACC_ALPHA_02,ACC_BETA_01,ACC_BETA_02");
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return;
        List<String> accounts = jwt.getClaimAsStringList("accounts");
        if (accounts == null || accounts.isEmpty()) return;
        authorize(String.join(",", accounts));
    }
}
