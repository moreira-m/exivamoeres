package com.exivamoeres.service.impl;

import com.exivamoeres.client.TibiaCreatureCatalogEntry;
import com.exivamoeres.client.TibiaDataClient;
import com.exivamoeres.domain.Creature;
import com.exivamoeres.repository.CreatureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importa o catálogo completo de criaturas (Bestiary) da TibiaData na subida
 * da aplicação. O endpoint /v4/creatures já traz nome, race e ícone de todas
 * de uma vez — não precisamos chamar uma a uma.
 *
 * Casamento por `race`: criaturas novas são inseridas (difficulty nula, que a
 * TibiaData não expõe); as que já existem (os 12 seeds da V3) têm só o ícone
 * preenchido, preservando nome e difficulty usados por testes e sugestões.
 *
 * Best-effort: se a TibiaData estiver fora, o boot segue com o catálogo que
 * houver no banco.
 */
@Component
@Slf4j
public class CreatureCatalogService implements ApplicationRunner {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);

    private final CreatureRepository creatureRepository;
    private final TibiaDataClient tibiaDataClient;

    public CreatureCatalogService(CreatureRepository creatureRepository, TibiaDataClient tibiaDataClient) {
        this.creatureRepository = creatureRepository;
        this.tibiaDataClient = tibiaDataClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<TibiaCreatureCatalogEntry> catalog;
        try {
            catalog = tibiaDataClient.fetchAllCreatures().block(FETCH_TIMEOUT);
        } catch (Exception e) {
            log.warn("creature.catalog.fetch_failed error={}", e.toString());
            return;
        }
        if (catalog == null || catalog.isEmpty()) {
            log.warn("creature.catalog.empty");
            return;
        }
        importCatalog(catalog);
    }

    private void importCatalog(List<TibiaCreatureCatalogEntry> catalog) {
        List<Creature> existing = creatureRepository.findAll();
        Map<String, Creature> byRace = new HashMap<>();
        Set<String> takenNames = new HashSet<>();
        for (Creature creature : existing) {
            if (creature.getRace() != null) {
                byRace.put(creature.getRace().toLowerCase(), creature);
            }
            takenNames.add(creature.getName().toLowerCase());
        }

        List<Creature> toSave = new ArrayList<>();
        int inserted = 0;
        int iconsFilled = 0;
        for (TibiaCreatureCatalogEntry entry : catalog) {
            if (entry.race() == null || entry.race().isBlank() || entry.name() == null) {
                continue;
            }
            Creature current = byRace.get(entry.race().toLowerCase());
            if (current == null) {
                // Guarda contra o índice único de name (colisão improvável entre
                // o seed singular e o nome plural do catálogo, mas protege o lote).
                if (takenNames.add(entry.name().toLowerCase())) {
                    toSave.add(newCreature(entry));
                    inserted++;
                }
            } else if (current.getImageUrl() == null && entry.imageUrl() != null) {
                current.setImageUrl(entry.imageUrl());
                toSave.add(current);
                iconsFilled++;
            }
        }
        creatureRepository.saveAll(toSave);
        log.info("creature.catalog.imported total={} inserted={} iconsFilled={}",
                catalog.size(), inserted, iconsFilled);
    }

    private Creature newCreature(TibiaCreatureCatalogEntry entry) {
        Creature creature = new Creature();
        creature.setName(entry.name());
        creature.setRace(entry.race());
        creature.setImageUrl(entry.imageUrl());
        // difficulty fica nula — a TibiaData não expõe as estrelas do Bestiary.
        return creature;
    }
}
