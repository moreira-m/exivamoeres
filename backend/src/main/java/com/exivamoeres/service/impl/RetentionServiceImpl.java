package com.exivamoeres.service.impl;

import com.exivamoeres.config.RetentionProperties;
import com.exivamoeres.repository.NotificationRepository;
import com.exivamoeres.repository.RefreshTokenRepository;
import com.exivamoeres.service.RetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class RetentionServiceImpl implements RetentionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationRepository notificationRepository;
    private final RetentionProperties properties;

    public RetentionServiceImpl(RefreshTokenRepository refreshTokenRepository,
                                NotificationRepository notificationRepository,
                                RetentionProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.notificationRepository = notificationRepository;
        this.properties = properties;
    }

    /**
     * O que cresce aqui não é "token expirado": é <b>entulho de rotação</b>. Todo
     * {@code /api/auth/refresh} revoga o token usado e emite outro
     * ({@code RefreshTokenServiceImpl.rotate}), então uma sessão ativa deixa uma linha
     * morta a cada 30 minutos (o TTL do access token). Medido no banco de
     * desenvolvimento: 19 das 27 linhas já eram revogadas, e <b>nenhuma</b> vencida.
     *
     * <p>⚠️ Por isso o corte é por {@code expiresAt}, e <b>não</b> por
     * {@code revoked = true}. Apagar o revogado na hora seria mais eficaz e destruiria o
     * sinal de <b>reuso de token</b> (S7): quem apresenta um token revogado hoje é
     * recusado com {@code reason=revoked userId=N} — a linha que diz "alguém está
     * tentando de novo com uma cópia, e é da conta N". Sem o registro, a mesma tentativa
     * vira {@code reason=unknown_token}, indistinguível de lixo de outro ambiente e sem
     * dono. A carência é o que mantém essa distinção durante e depois da vida do token.</p>
     */
    @Override
    @Transactional
    public int purgeExpiredRefreshTokens() {
        Instant limite = Instant.now().minus(properties.expiredRefreshTokenGrace());
        int apagados = refreshTokenRepository.deleteExpiredBefore(limite);
        if (apagados > 0) {
            log.info("retention.refresh_tokens.purged count={} olderThan={}", apagados, limite);
        }
        return apagados;
    }

    /**
     * Notificação lida é aviso <b>entregue</b>: o fato que ela anunciava continua no
     * time, na participação e no chat. Guardá-la para sempre é pagar armazenamento por
     * uma cópia do que já aconteceu.
     *
     * <p>⚠️ Só as <b>lidas</b>. Não-lida com 200 dias ainda é algo que aquela pessoa
     * nunca viu — apagá-la é o site decidir sozinho que o aviso não valia. E o volume
     * de não-lidas não é ilimitado do mesmo jeito: elas param de nascer quando a conta
     * para de participar de times.</p>
     */
    @Override
    @Transactional
    public int purgeOldReadNotifications() {
        Instant limite = Instant.now().minus(properties.readNotificationRetention());
        int apagados = notificationRepository.deleteReadBefore(limite);
        if (apagados > 0) {
            log.info("retention.notifications.purged count={} olderThan={}", apagados, limite);
        }
        return apagados;
    }
}
