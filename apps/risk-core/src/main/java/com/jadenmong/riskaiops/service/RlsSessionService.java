package com.jadenmong.riskaiops.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Binds the already-authorized account to the current database transaction for FORCE RLS. */
@Service
public class RlsSessionService {
    private final JdbcTemplate jdbc;
    public RlsSessionService(ObjectProvider<JdbcTemplate> provider) { this.jdbc = provider.getIfAvailable(); }
    public void authorize(String accountId) {
        if (jdbc != null) jdbc.queryForObject("select set_config('app.allowed_accounts', ?, true)", String.class, accountId);
    }
}
