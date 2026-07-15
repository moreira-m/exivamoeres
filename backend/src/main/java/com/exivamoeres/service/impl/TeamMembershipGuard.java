package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import org.springframework.stereotype.Component;

/**
 * Autorização de ações dentro de um time. Centraliza a regra "só membro ativo
 * e aprovado age no time" para não repeti-la em cada service (soulcore, chat,
 * sugestões).
 */
@Component
public class TeamMembershipGuard {

    private final ListMembershipRepository membershipRepository;
    private final CharacterRepository characterRepository;

    public TeamMembershipGuard(ListMembershipRepository membershipRepository,
                               CharacterRepository characterRepository) {
        this.membershipRepository = membershipRepository;
        this.characterRepository = characterRepository;
    }

    /**
     * Garante que o personagem é membro ativo e aprovado do time e pertence ao
     * usuário. Devolve o personagem carregado.
     */
    public Character requireActiveMember(Long userId, Long listId, Long characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResourceNotFoundException("Personagem não encontrado"));
        if (character.getOwner() == null || !character.getOwner().getId().equals(userId)) {
            throw new BusinessRuleException("Este personagem não é seu");
        }
        ListMembership membership = membershipRepository
                .findByListIdAndCharacterIdAndActiveTrueAndStatus(listId, characterId, MembershipStatus.APPROVED)
                .orElseThrow(() -> new BusinessRuleException(
                        "Este personagem não é membro ativo deste time"));
        return membership.getCharacter();
    }

    /** Garante que o usuário participa ativamente do time (por qualquer personagem). */
    public void requireActiveMember(Long userId, Long listId) {
        boolean member = membershipRepository
                .existsByListIdAndUserIdAndActiveTrueAndStatus(listId, userId, MembershipStatus.APPROVED);
        if (!member) {
            throw new BusinessRuleException("Você não participa deste time");
        }
    }
}
