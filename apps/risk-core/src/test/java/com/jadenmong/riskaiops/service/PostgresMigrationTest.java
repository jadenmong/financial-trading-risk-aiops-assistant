package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {
    private static final DockerImageName POSTGRES = DockerImageName
            .parse("postgres:18.4@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
            .asCompatibleSubstituteFor("postgres");
    @Container
    static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>(POSTGRES);

    @Test
    void migratesAllSchemasSeedsAndAppendOnlyTrigger() throws Exception {
        Flyway flyway = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("reference", "trading", "risk", "ops", "ai", "audit").load();
        assertThat(flyway.migrate().success).isTrue();
        try (var connection = database.createConnection(""); var statement = connection.createStatement()) {
            assertThat(statement.executeQuery("select count(*) from reference.account").next()).isTrue();
            var accounts = statement.executeQuery("select count(*) from reference.account"); accounts.next(); assertThat(accounts.getInt(1)).isEqualTo(4);
            var instruments = statement.executeQuery("select count(*) from reference.instrument"); instruments.next(); assertThat(instruments.getInt(1)).isEqualTo(8);
        }
    }
}
