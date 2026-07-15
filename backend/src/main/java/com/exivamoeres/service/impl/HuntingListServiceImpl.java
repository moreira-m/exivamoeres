package com.exivamoeres.service.impl;

import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.Creature;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.ShareCodeGenerator;
import com.exivamoeres.service.TeamEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class HuntingListServiceImpl implements HuntingListService {

    private final HuntingListRepository listRepository;
    private final ListMembershipRepository membershipRepository;
    private final CharacterRepository characterRepository;
    private final CreatureRepository creatureRepository;
    private final UserRepository userRepository;
    private final TeamEligibilityService eligibilityService;
    private final ShareCodeGenerator shareCodeGenerator;
    private final int maxMembers;

    public HuntingListServiceImpl(HuntingListRepository listRepository,
                                  ListMembershipRepository membershipRepository,
                                  CharacterRepository characterRepository,
                                  CreatureRepository creatureRepository,
                                  UserRepository userRepository,
                                  TeamEligibilityService eligibilityService,
                                  ShareCodeGenerator shareCodeGenerator,
                                  TeamProperties teamProperties) {
        this.listRepository = listRepository;
        this.membershipRepository = membershipRepository;
        this.characterRepository = characterRepository;
        this.creatureRepository = creatureRepository;
        this.userRepository = userRepository;
        this.eligibilityService = eligibilityService;
        this.shareCodeGenerator = shareCodeGenerator;
        this.maxMembers = teamProperties.maxMembers();
    }

    @Override
    @Transactional
    public ListDetailResponse createList(Long ownerId, CreateListRequest request) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Creature target = creatureRepository.findById(request.targetCreatureId())
                .orElseThrow(() -> new ResourceNotFoundException("Criatura não encontrada"));
        Character character = loadOwnedCharacter(request.characterId(), ownerId);

        // A elegibilidade do criador é validada com o world do próprio time.
        eligibilityService.assertEligible(character, request.world());

        HuntingList list = new HuntingList();
        list.setName(request.name());
        list.setWorld(request.world());
        list.setOwner(owner);
        list.setTargetCreature(target);
        list.setJoinPolicy(request.joinPolicy());
        list.setShareCode(generateUniqueShareCode());
        listRepository.save(list);

        // O criador entra já aprovado como primeiro membro.
        ListMembership membership = new ListMembership();
        membership.setList(list);
        membership.setUser(owner);
        membership.setCharacter(character);
        membership.setActive(true);
        membership.setStatus(MembershipStatus.APPROVED);
        membershipRepository.save(membership);

        log.info("list.created listId={} ownerId={} world={} targetCreatureId={}",
                list.getId(), ownerId, list.getWorld(), target.getId());
        return buildDetail(list);
    }

    @Override
    @Transactional
    public ListDetailResponse joinByShareCode(Long userId, String shareCode, JoinListRequest request) {
        Long listId = listRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"))
                .getId();
        // Trava a linha do time: impede corrida no limite de vagas.
        HuntingList list = listRepository.findByIdForUpdate(listId).orElseThrow();

        Character character = loadOwnedCharacter(request.characterId(), userId);
        eligibilityService.assertEligible(character, list.getWorld());

        ListMembership membership = membershipRepository
                .findByListIdAndCharacterId(list.getId(), character.getId())
                .orElseGet(() -> newMembership(list, character));
        membership.setUser(character.getOwner());

        if (membership.isActive() && membership.getStatus() == MembershipStatus.APPROVED) {
            throw new BusinessRuleException("Este personagem já é membro do time");
        }
        if (membership.isActive() && membership.getStatus() == MembershipStatus.PENDING) {
            throw new BusinessRuleException("Já existe um pedido pendente para este personagem");
        }

        if (list.getJoinPolicy() == JoinPolicy.AUTO_ACCEPT) {
            assertHasOpenSlot(list.getId());
            membership.setStatus(MembershipStatus.APPROVED);
        } else {
            membership.setStatus(MembershipStatus.PENDING);
        }
        membership.setActive(true);
        membershipRepository.save(membership);

        log.info("list.join listId={} userId={} characterId={} status={}",
                list.getId(), userId, character.getId(), membership.getStatus());
        return buildDetail(list);
    }

    @Override
    @Transactional
    public void approveJoinRequest(Long ownerId, Long listId, Long membershipId) {
        HuntingList list = loadOwnedListForUpdate(listId, ownerId);
        ListMembership membership = loadPendingRequest(membershipId, list.getId());

        assertHasOpenSlot(list.getId());
        membership.setStatus(MembershipStatus.APPROVED);
        log.info("list.request.approved listId={} membershipId={}", listId, membershipId);
    }

    @Override
    @Transactional
    public void rejectJoinRequest(Long ownerId, Long listId, Long membershipId) {
        HuntingList list = loadOwnedList(listId, ownerId);
        ListMembership membership = loadPendingRequest(membershipId, list.getId());

        // Recusa preserva o histórico: marca REJECTED e desativa, nunca deleta.
        membership.setStatus(MembershipStatus.REJECTED);
        membership.setActive(false);
        log.info("list.request.rejected listId={} membershipId={}", listId, membershipId);
    }

    @Override
    @Transactional
    public void leaveList(Long userId, Long listId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        List<ListMembership> memberships = membershipRepository.findAllByUserIdAndActiveTrue(userId).stream()
                .filter(m -> m.getList().getId().equals(listId))
                .toList();
        if (memberships.isEmpty()) {
            throw new BusinessRuleException("Você não participa deste time");
        }
        if (list.getOwner().getId().equals(userId)) {
            throw new BusinessRuleException(
                    "O dono não pode sair do próprio time; transfira ou exclua o time");
        }
        // Sair nunca deleta histórico — só desativa (regra herdada da sessão 1).
        memberships.forEach(m -> m.setActive(false));
        log.info("list.leave listId={} userId={} count={}", listId, userId, memberships.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ListSummaryResponse> listMyLists(Long userId) {
        // Times onde é dono OU membro ativo aprovado — sem duplicar.
        List<HuntingList> owned = listRepository.findAllByOwnerId(userId);
        List<HuntingList> joined = membershipRepository.findAllByUserIdAndActiveTrue(userId).stream()
                .filter(m -> m.getStatus() == MembershipStatus.APPROVED)
                .map(ListMembership::getList)
                .toList();

        List<HuntingList> all = new ArrayList<>(owned);
        joined.stream()
                .filter(l -> owned.stream().noneMatch(o -> o.getId().equals(l.getId())))
                .forEach(all::add);
        return all.stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ListDetailResponse getList(Long listId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        return buildDetail(list);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ListSummaryResponse> search(String world, Long creatureId, Boolean hasOpenSlots, Pageable pageable) {
        Page<ListSummaryResponse> page = listRepository
                .search(blankToNull(world), creatureId, pageable)
                .map(this::toSummary);
        if (Boolean.TRUE.equals(hasOpenSlots)) {
            List<ListSummaryResponse> filtered = page.getContent().stream()
                    .filter(ListSummaryResponse::hasOpenSlots)
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipResponse> listPendingRequests(Long ownerId, Long listId) {
        loadOwnedList(listId, ownerId);
        return membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(listId, MembershipStatus.PENDING).stream()
                .map(MembershipResponse::from)
                .toList();
    }

    // ----- Helpers -----

    private ListMembership newMembership(HuntingList list, Character character) {
        ListMembership membership = new ListMembership();
        membership.setList(list);
        membership.setCharacter(character);
        return membership;
    }

    private Character loadOwnedCharacter(Long characterId, Long userId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResourceNotFoundException("Personagem não encontrado"));
        if (character.getOwner() == null || !character.getOwner().getId().equals(userId)) {
            throw new BusinessRuleException(
                    "Você só pode usar personagens que já verificou (claim aprovado)");
        }
        return character;
    }

    private HuntingList loadOwnedList(Long listId, Long ownerId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        assertOwner(list, ownerId);
        return list;
    }

    private HuntingList loadOwnedListForUpdate(Long listId, Long ownerId) {
        HuntingList list = listRepository.findByIdForUpdate(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        assertOwner(list, ownerId);
        return list;
    }

    private void assertOwner(HuntingList list, Long ownerId) {
        if (!list.getOwner().getId().equals(ownerId)) {
            throw new BusinessRuleException("Apenas o dono do time pode fazer isso");
        }
    }

    private ListMembership loadPendingRequest(Long membershipId, Long listId) {
        ListMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        if (!membership.getList().getId().equals(listId)) {
            throw new ResourceNotFoundException("Pedido não pertence a este time");
        }
        if (membership.getStatus() != MembershipStatus.PENDING || !membership.isActive()) {
            throw new BusinessRuleException("Este pedido não está mais pendente");
        }
        return membership;
    }

    private void assertHasOpenSlot(Long listId) {
        long approved = membershipRepository
                .countByListIdAndActiveTrueAndStatus(listId, MembershipStatus.APPROVED);
        if (approved >= maxMembers) {
            throw new BusinessRuleException(
                    "O time está cheio (máximo de " + maxMembers + " jogadores)");
        }
    }

    private String generateUniqueShareCode() {
        // Colisão é improvável, mas o share_code é UNIQUE no banco; tenta de novo.
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = shareCodeGenerator.generate();
            if (listRepository.findByShareCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessRuleException("Não foi possível gerar um código de convite; tente novamente");
    }

    private ListDetailResponse buildDetail(HuntingList list) {
        List<MembershipResponse> members = membershipRepository
                .findAllByListIdAndActiveTrue(list.getId()).stream()
                .map(MembershipResponse::from)
                .toList();
        long approved = membershipRepository
                .countByListIdAndActiveTrueAndStatus(list.getId(), MembershipStatus.APPROVED);
        return ListDetailResponse.from(list, approved, maxMembers, members);
    }

    private ListSummaryResponse toSummary(HuntingList list) {
        long approved = membershipRepository
                .countByListIdAndActiveTrueAndStatus(list.getId(), MembershipStatus.APPROVED);
        return ListSummaryResponse.from(list, approved, maxMembers);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
