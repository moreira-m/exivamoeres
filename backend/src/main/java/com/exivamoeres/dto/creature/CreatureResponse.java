package com.exivamoeres.dto.creature;

import com.exivamoeres.domain.Creature;

public record CreatureResponse(
        Long id,
        String name,
        Integer difficulty,
        String imageUrl
) {
    public static CreatureResponse from(Creature creature) {
        return new CreatureResponse(
                creature.getId(), creature.getName(), creature.getDifficulty(), creature.getImageUrl());
    }
}
