package com.exivamoeres.service.impl;

import com.exivamoeres.dto.creature.CreatureResponse;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.service.CreatureService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CreatureServiceImpl implements CreatureService {

    private final CreatureRepository creatureRepository;

    public CreatureServiceImpl(CreatureRepository creatureRepository) {
        this.creatureRepository = creatureRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreatureResponse> listAll() {
        return creatureRepository.findAllByOrderByNameAsc().stream()
                .map(CreatureResponse::from)
                .toList();
    }
}
