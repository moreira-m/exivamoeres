package com.exivamoeres.integration;

import com.exivamoeres.domain.AuthProvider;
import com.exivamoeres.domain.Notification;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.RefreshToken;
import com.exivamoeres.domain.User;
import com.exivamoeres.repository.NotificationRepository;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.scheduler.RetentionScheduler;
import com.exivamoeres.service.RetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * A limpeza periódica das tabelas que cresciam para sempre (item S8).
 *
 * <p>Este job é <b>invisível</b>: ninguém abre um chamado porque uma tabela está
 * grande, e ninguém percebe se ele apagar demais — até a hora em que todo mundo é
 * deslogado de uma vez. Por isso metade destes testes afirma o que <b>não</b> pode
 * sumir, e não o que some.</p>
 */
class RetentionIntegrationTest extends IntegrationTestBase {

    @Autowired RetentionService retentionService;
    @Autowired RetentionScheduler retentionScheduler;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    /**
     * Espião, não mock: as notificações continuam sendo apagadas de verdade em todos os
     * testes, e só um deles troca o comportamento de um método. Existe por causa do
     * teste das transações separadas, lá embaixo.
     */
    @SpyBean NotificationRepository notificationRepository;

    private User usuario;

    @BeforeEach
    void limparEstado() {
        refreshTokenRepository.deleteAll();
        notificationRepository.deleteAll();
        usuario = novoUsuario();
    }

    // ---------------------------------------------------------------- tokens

    @Test
    void tokenVencidoHaMaisDeUmMesSomeDoBanco() {
        RefreshToken alvo = token(Instant.now().minus(Duration.ofDays(40)), true);

        int apagados = retentionService.purgeExpiredRefreshTokens();

        assertThat(apagados).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(alvo.getId())).isEmpty();
    }

    @Test
    void tokenRevogadoDentroDaCarenciaFicaParaOSinalDeReuso() {
        // Vencido ontem e revogado: é exatamente o registro que alguém "otimizaria"
        // apagando por `revoked = true`. Ele precisa ficar — enquanto existe, uma
        // tentativa de reuso é recusada com `reason=revoked userId=N` (S7); sem ele, a
        // mesma tentativa vira `unknown_token`, indistinguível de lixo e sem dono.
        RefreshToken alvo = token(Instant.now().minus(Duration.ofDays(1)), true);

        int apagados = retentionService.purgeExpiredRefreshTokens();

        assertThat(apagados).isZero();
        assertThat(refreshTokenRepository.findById(alvo.getId())).isPresent();
    }

    @Test
    void tokenQueAindaValeNaoEApagado() {
        // O pior defeito possível neste job: uma comparação invertida desloga todo
        // mundo de uma vez, e o único sintoma é "o site pediu login de novo".
        RefreshToken vivo = token(Instant.now().plus(Duration.ofDays(14)), false);

        retentionService.purgeExpiredRefreshTokens();

        assertThat(refreshTokenRepository.findById(vivo.getId())).isPresent();
    }

    // ---------------------------------------------------------- notificações

    @Test
    void notificacaoLidaAntigaSomeDoBanco() {
        Notification alvo = notificacao(true, Duration.ofDays(120));

        int apagadas = retentionService.purgeOldReadNotifications();

        assertThat(apagadas).isEqualTo(1);
        assertThat(notificationRepository.findById(alvo.getId())).isEmpty();
    }

    @Test
    void notificacaoNaoLidaNuncaEApagada() {
        // Não-lida com 200 dias ainda é algo que aquela pessoa nunca viu. Apagá-la é o
        // site decidir sozinho que o aviso não valia.
        Notification alvo = notificacao(false, Duration.ofDays(200));

        int apagadas = retentionService.purgeOldReadNotifications();

        assertThat(apagadas).isZero();
        assertThat(notificationRepository.findById(alvo.getId())).isPresent();
    }

    @Test
    void notificacaoLidaRecenteFica() {
        Notification alvo = notificacao(true, Duration.ofDays(10));

        retentionService.purgeOldReadNotifications();

        assertThat(notificationRepository.findById(alvo.getId())).isPresent();
    }

    // ------------------------------------------------------------ o ciclo

    @Test
    void umCicloDoJobFazAsDuasLimpezas() {
        RefreshToken token = token(Instant.now().minus(Duration.ofDays(40)), true);
        Notification notificacao = notificacao(true, Duration.ofDays(120));

        retentionScheduler.purgeExpiredData();

        assertThat(refreshTokenRepository.findById(token.getId())).isEmpty();
        assertThat(notificationRepository.findById(notificacao.getId())).isEmpty();
    }

    @Test
    void cicloSemLixoNenhumNaoQuebra() {
        // O caso normal em produção por muito tempo: nada a apagar. O job roda todo dia
        // desde o primeiro deploy, e "não achou nada" não pode ser um erro.
        token(Instant.now().plus(Duration.ofDays(14)), false);
        notificacao(false, Duration.ofDays(1));

        retentionScheduler.purgeExpiredData();

        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void falhaNaSegundaLimpezaNaoDesfazAPrimeira() {
        // A razão de as duas purgas terem transação própria: se estivessem na mesma, o
        // ciclo seria tudo-ou-nada e um erro nas notificações faria a tabela de tokens
        // continuar crescendo para sempre — sem ninguém notar, porque o alerta que
        // dispara é o mesmo.
        RefreshToken token = token(Instant.now().minus(Duration.ofDays(40)), true);
        doThrow(new IllegalStateException("banco fora"))
                .when(notificationRepository).deleteReadBefore(any());

        assertThatThrownBy(() -> retentionScheduler.purgeExpiredData())
                .isInstanceOf(IllegalStateException.class);

        assertThat(refreshTokenRepository.findById(token.getId()))
                .as("o que a primeira purga já apagou continua apagado")
                .isEmpty();
    }

    // ------------------------------------------------------------ fixtures

    private User novoUsuario() {
        User user = new User();
        user.setEmail("retencao-" + UUID.randomUUID() + "@exemplo.com");
        user.setDisplayName("Jogador da retenção");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setPasswordHash("$2a$10$hash-irrelevante");
        return userRepository.save(user);
    }

    private RefreshToken token(Instant expiraEm, boolean revogado) {
        RefreshToken token = new RefreshToken();
        token.setUser(usuario);
        token.setTokenHash(UUID.randomUUID().toString().repeat(2).substring(0, 64));
        token.setExpiresAt(expiraEm);
        token.setRevoked(revogado);
        return refreshTokenRepository.save(token);
    }

    /**
     * ⚠️ O `created_at` da notificação é preenchido por `@PrePersist` e é
     * `updatable = false` — não há como nascer velha pelo JPA. Envelhecer no SQL é o
     * que permite testar a janela de 90 dias sem esperar 90 dias nem parametrizar o
     * relógio da aplicação só para o teste.
     */
    private Notification notificacao(boolean lida, Duration idade) {
        Notification notificacao = new Notification();
        notificacao.setRecipient(usuario);
        notificacao.setType(NotificationType.JOIN_REQUEST_RECEIVED);
        notificacao.setListName("Time de teste");
        notificacao.setRead(lida);
        Notification salva = notificationRepository.saveAndFlush(notificacao);
        jdbcTemplate.update("update notifications set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(idade)), salva.getId());
        return salva;
    }
}
