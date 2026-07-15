package com.exivamoeres.service.impl;

import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.service.TeamLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class TeamLifecycleServiceImpl implements TeamLifecycleService {

    private final HuntingListRepository listRepository;

    public TeamLifecycleServiceImpl(HuntingListRepository listRepository) {
        this.listRepository = listRepository;
    }

    @Override
    @Transactional
    public void completeIfTargetUnlocked(HuntingList list, Long unlockedCreatureId) {
        if (list.getStatus() != TeamStatus.ACTIVE) {
            return;
        }
        if (!list.getTargetCreature().getId().equals(unlockedCreatureId)) {
            return;
        }
        list.setStatus(TeamStatus.COMPLETED);
        log.info("team.completed listId={} targetCreatureId={}", list.getId(), unlockedCreatureId);
    }

    @Override
    @Transactional
    public int archiveExpiredTeams() {
        List<HuntingList> expired =
                listRepository.findAllByStatusAndExpiresAtBefore(TeamStatus.ACTIVE, Instant.now());
        expired.forEach(list -> list.setStatus(TeamStatus.ARCHIVED));
        if (!expired.isEmpty()) {
            log.info("team.archived.batch count={}", expired.size());
        }
        return expired.size();
    }
}
