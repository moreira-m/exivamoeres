package com.exivamoeres.service.impl;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import org.springframework.stereotype.Component;

/**
 * Autorização de ações dentro de um time. Centraliza duas regras para não
 * repeti-las em cada service (soulcore, chat, sugestões):
 * - só membro ativo e aprovado age no time;
 * - escrita (marcar soulcore, enviar mensagem) só em time ATIVO — times
 *   COMPLETED/ARCHIVED são somente leitura.
 */
@Component
public class TeamMembershipGuard {

    private final ListMembershipRepository membershipRepository;
    private final CharacterRepository characterRepository;
    private final HuntingListRepository listRepository;

    public TeamMembershipGuard(ListMembershipRepository membershipRepository,
                               CharacterRepository characterRepository,
                               HuntingListRepository listRepository) {
        this.membershipRepository = membershipRepository;
        this.characterRepository = characterRepository;
        this.listRepository = listRepository;
    }

    /**
     * Autoriza uma AÇÃO DE ESCRITA no time: o personagem é do usuário, é membro
     * ativo/aprovado, e o time está ATIVO (aceita escrita). Devolve o
     * personagem carregado.
     */
    public Character requireActiveMemberForAction(Long userId, Long listId, Long characterId) {
        Character character = requireOwnedActiveMembership(userId, listId, characterId);
        requireWritableTeam(listId);
        return character;
    }

    /** Autoriza LEITURA: basta o usuário participar ativamente do time. */
    public void requireActiveMember(Long userId, Long listId) {
        boolean member = membershipRepository
                .existsByListIdAndUserIdAndActiveTrueAndStatus(listId, userId, MembershipStatus.APPROVED);
        if (!member) {
            throw new BusinessRuleException("Você não participa deste time");
        }
    }

    private Character requireOwnedActiveMembership(Long userId, Long listId, Long characterId) {
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

    private void requireWritableTeam(Long listId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        if (!list.allowsWrites()) {
            throw new BusinessRuleException(
                    "Este time está " + statusLabel(list) + " e não aceita mais alterações");
        }
    }

    private String statusLabel(HuntingList list) {
        return switch (list.getStatus()) {
            case COMPLETED -> "concluído";
            case ARCHIVED -> "arquivado";
            case CLOSED -> "encerrado";
            case ACTIVE -> "ativo";
        };
    }
}
