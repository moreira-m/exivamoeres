package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCharacterSnapshot;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.config.CharacterProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.service.CharacterLevelRefreshService;
import com.exivamoeres.service.CharacterSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Refresh de level de baixo custo:
 * <ul>
 *   <li><b>Escopo</b>: só personagens em time ativo (query do repositório) —
 *       quem não está em time gera zero chamadas.</li>
 *   <li><b>Staleness</b>: só rebusca quem não sincroniza há mais de
 *       {@code level-staleness} (reusa o {@code updated_at}, tocado apenas no
 *       sync — sem coluna nova).</li>
 *   <li><b>Lote + pausa</b>: no máximo {@code level-refresh-batch-size} por
 *       ciclo, com {@code level-refresh-spacing} entre chamadas, pra não dar
 *       rajada na TibiaData.</li>
 * </ul>
 * Busca dados frescos direto no client (sem o cache de elegibilidade), igual ao
 * polling de claims. Cada personagem é isolado: uma falha só é logada.
 */
@Service
@Slf4j
public class CharacterLevelRefreshServiceImpl implements CharacterLevelRefreshService {

    /** Teto de espera por personagem — protege o ciclo de travar. */
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

    private final CharacterRepository characterRepository;
    private final TibiaDataClient tibiaDataClient;
    private final CharacterSyncService characterSyncService;
    private final CharacterProperties characterProperties;

    public CharacterLevelRefreshServiceImpl(CharacterRepository characterRepository,
                                            TibiaDataClient tibiaDataClient,
                                            CharacterSyncService characterSyncService,
                                            CharacterProperties characterProperties) {
        this.characterRepository = characterRepository;
        this.tibiaDataClient = tibiaDataClient;
        this.characterSyncService = characterSyncService;
        this.characterProperties = characterProperties;
    }

    @Override
    public void refreshStaleTeamCharacters() {
        Instant threshold = Instant.now().minus(characterProperties.levelStaleness());
        List<Character> stale = characterRepository.findStaleCharactersInActiveTeams(
                threshold, PageRequest.of(0, characterProperties.levelRefreshBatchSize()));
        if (stale.isEmpty()) {
            return;
        }
        log.info("character.level_refresh.start batchSize={}", stale.size());
        long spacingMillis = characterProperties.levelRefreshSpacing().toMillis();
        int updated = 0;
        for (int i = 0; i < stale.size(); i++) {
            Character character = stale.get(i);
            try {
                if (refreshOne(character)) {
                    updated++;
                }
            } catch (Exception e) {
                log.error("character.level_refresh.char_error characterId={} error={}",
                        character.getId(), e.toString());
            }
            // Espaça as chamadas (menos na última) pra suavizar o pico na TibiaData.
            if (spacingMillis > 0 && i < stale.size() - 1) {
                try {
                    Thread.sleep(spacingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("character.level_refresh.end batchSize={} updated={}", stale.size(), updated);
    }

    private boolean refreshOne(Character character) {
        TibiaCharacterSnapshot snapshot = tibiaDataClient.fetchCharacter(character.getName()).block(FETCH_TIMEOUT);
        // Personagem sumiu (deletado/renomeado) ou TibiaData indisponível: não
        // toca no retrato local; será tentado de novo no próximo ciclo.
        if (snapshot == null || !snapshot.found()) {
            return false;
        }
        characterSyncService.findOrCreateFromSnapshot(snapshot);
        return true;
    }
}
