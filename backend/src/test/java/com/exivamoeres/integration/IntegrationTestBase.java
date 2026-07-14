package com.exivamoeres.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: Postgres real via Testcontainers (as
 * migrations Flyway rodam de verdade — o schema testado é o de produção).
 *
 * Container singleton compartilhado entre classes de teste para não pagar o
 * custo de subir um Postgres por classe.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Segredo de teste (>= 64 bytes p/ HMAC-SHA512) — nunca usado fora daqui.
        registry.add("app.jwt.secret",
                () -> "chave-de-teste-apenas-para-testes-0123456789-0123456789-0123456789");
    }
}
