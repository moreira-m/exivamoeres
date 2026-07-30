package com.exivamoeres.service.impl;

import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.Creature;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.TeamSlot;
import com.exivamoeres.domain.TeamStatus;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.domain.exception.ForbiddenOperationException;
import com.exivamoeres.domain.exception.ResourceNotFoundException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.JoinRequestIssue;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.dto.list.MyJoinRequestResponse;
import com.exivamoeres.dto.list.TeamSlotResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.dto.list.UpdateSlotsRequest;
import com.exivamoeres.repository.CharacterRepository;
import com.exivamoeres.repository.CreatureRepository;
import com.exivamoeres.repository.HuntingListRepository;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.UserRepository;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import com.exivamoeres.service.PlanPolicy;
import com.exivamoeres.service.ShareCodeGenerator;
import com.exivamoeres.service.TeamEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class HuntingListServiceImpl implements HuntingListService {

    private final HuntingListRepository listRepository;
    private final ListMembershipRepository membershipRepository;
    private final CharacterRepository characterRepository;
    private final CreatureRepository creatureRepository;
    private final UserRepository userRepository;
    private final TeamEligibilityService eligibilityService;
    private final PlanPolicy planPolicy;
    private final ShareCodeGenerator shareCodeGenerator;
    private final NotificationService notificationService;
    private final UserRateLimiter userRateLimiter;
    private final TeamSlotAssigner slotAssigner;
    private final int maxMembers;

    public HuntingListServiceImpl(HuntingListRepository listRepository,
                                  ListMembershipRepository membershipRepository,
                                  CharacterRepository characterRepository,
                                  CreatureRepository creatureRepository,
                                  UserRepository userRepository,
                                  TeamEligibilityService eligibilityService,
                                  PlanPolicy planPolicy,
                                  ShareCodeGenerator shareCodeGenerator,
                                  NotificationService notificationService,
                                  UserRateLimiter userRateLimiter,
                                  TeamSlotAssigner slotAssigner,
                                  TeamProperties teamProperties) {
        this.listRepository = listRepository;
        this.membershipRepository = membershipRepository;
        this.characterRepository = characterRepository;
        this.creatureRepository = creatureRepository;
        this.userRepository = userRepository;
        this.eligibilityService = eligibilityService;
        this.planPolicy = planPolicy;
        this.shareCodeGenerator = shareCodeGenerator;
        this.notificationService = notificationService;
        this.userRateLimiter = userRateLimiter;
        this.slotAssigner = slotAssigner;
        this.maxMembers = teamProperties.maxMembers();
    }

    @Override
    @Transactional
    public ListDetailResponse createList(Long ownerId, CreateListRequest request) {
        // Antes de qualquer consulta: criar em rajada é o abuso, não o pedido em si.
        userRateLimiter.checkTeamCreation(ownerId);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Creature target = creatureRepository.findById(request.targetCreatureId())
                .orElseThrow(() -> new ResourceNotFoundException("Criatura não encontrada"));
        Character character = loadOwnedCharacter(request.characterId(), ownerId);

        // Limite de times ativos conforme o plano (free tem teto; premium não).
        assertWithinActiveTeamLimit(owner);
        // A elegibilidade do criador é validada com o world e o level mínimo
        // do próprio time (o criador precisa atender ao requisito que define).
        // O snapshot é reaproveitado para a vocação: é o dado mais fresco que
        // existe (o local pode estar defasado até o próximo refresh).
        Vocation vocacaoDoCriador = Vocation.fromTibiaData(
                eligibilityService.assertEligible(character, request.world(), request.minimumLevel())
                        .vocation());

        HuntingList list = new HuntingList();
        // O time é identificado pela criatura-alvo; o título é opcional e cai
        // no nome da criatura quando não informado.
        list.setName(request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : target.getName());
        list.setWorld(request.world());
        list.setOwner(owner);
        list.setTargetCreature(target);
        list.setJoinPolicy(request.joinPolicy());
        list.setMinimumLevel(request.minimumLevel());
        list.setPricePerSlot(request.pricePerSlot());
        // Campos livres: espaço em branco é o mesmo que não informar — senão a
        // tela ganha um bloco de descrição vazio.
        list.setDescription(trimToNull(request.description()));
        list.setHuntSchedule(trimToNull(request.huntSchedule()));
        list.setContact(trimToNull(request.contact()));
        list.setShareCode(generateUniqueShareCode());
        list.setStatus(TeamStatus.ACTIVE);
        list.setExpiresAt(Instant.now().plus(planPolicy.teamDuration(owner.getPlan())));
        listRepository.save(list);

        // Composição opcional. Criada antes da membership do dono porque é ela que
        // decide em qual vaga ele entra — e se ele cabe na composição que definiu.
        slotAssigner.createSlots(list, request.slots());

        // O criador entra já aprovado como primeiro membro.
        ListMembership membership = new ListMembership();
        membership.setList(list);
        membership.setUser(owner);
        membership.setCharacter(character);
        membership.setActive(true);
        membership.setStatus(MembershipStatus.APPROVED);
        // Mesma regra de todo mundo: o dono também precisa caber na composição.
        // Se não couber, o 422 sai aqui e a transação inteira volta atrás.
        slotAssigner.assignSlot(list, vocacaoDoCriador).ifPresent(membership::setSlot);
        membershipRepository.save(membership);

        log.info("list.created listId={} ownerId={} world={} targetCreatureId={}",
                list.getId(), ownerId, list.getWorld(), target.getId());
        return buildDetail(list, ownerId);
    }

    @Override
    @Transactional
    public ListDetailResponse updateList(Long ownerId, Long listId, UpdateListRequest request) {
        // Antes de qualquer consulta, como em createList: editar em rajada
        // notifica os membros a cada volta, então a rajada é o abuso.
        userRateLimiter.checkTeamUpdate(ownerId);
        HuntingList list = loadOwnedList(listId, ownerId); // 403 se não for o dono
        if (!list.allowsWrites()) {
            // Mesma regra do chat e do soulcore: time não-ativo é só leitura. Um
            // time arquivado que continuasse editável seria um anúncio invisível
            // sendo maquiado.
            throw new BusinessRuleException(
                    "Este time não aceita mais alterações; ele está " + list.getStatus());
        }
        assertOwnerStillMeetsMinimumLevel(list, ownerId, request.minimumLevel());

        // Guardado ANTES da escrita: é o que decide quem precisa ser avisado.
        String horarioAnterior = list.getHuntSchedule();
        Integer levelAnterior = list.getMinimumLevel();

        // Título vazio volta a assumir o nome da criatura — mesma regra da criação.
        list.setName(trimToNull(request.name()) != null
                ? request.name().trim()
                : list.getTargetCreature().getName());
        list.setMinimumLevel(request.minimumLevel());
        list.setPricePerSlot(request.pricePerSlot());
        list.setDescription(trimToNull(request.description()));
        list.setHuntSchedule(trimToNull(request.huntSchedule()));
        list.setContact(trimToNull(request.contact()));

        notifyRelevantChanges(list, ownerId, horarioAnterior, levelAnterior);

        log.info("list.updated listId={} ownerId={} minimumLevel={}",
                listId, ownerId, list.getMinimumLevel());
        return buildDetail(list, ownerId);
    }

    @Override
    @Transactional
    public ListDetailResponse replaceSlots(Long ownerId, Long listId, UpdateSlotsRequest request) {
        // Mesmo teto da edição de texto: reconfigurar em rajada é o abuso.
        userRateLimiter.checkTeamUpdate(ownerId);
        HuntingList list = loadOwnedList(listId, ownerId); // 403 se não for o dono
        if (!list.allowsWrites()) {
            throw new BusinessRuleException(
                    "Este time não aceita mais alterações; ele está " + list.getStatus());
        }
        // A composição de antes, para saber **quem passou** a não caber. Sem isto só
        // daria para avisar "quem não cabe", e quem já não cabia receberia o mesmo
        // aviso a cada reconfiguração — o recorte de transição do P18.
        List<Vocation> composicaoAnterior = slotAssigner.compositionOf(listId);
        slotAssigner.replaceSlots(list, request.slots());
        int avisados = notifyPendingRequestsWithoutSlot(list, composicaoAnterior);
        log.info("list.slots.replaced listId={} ownerId={} slots={} pendingWithoutSlot={}",
                listId, ownerId, request.slots().size(), avisados);
        return buildDetail(list, ownerId);
    }

    @Override
    @Transactional
    public ListDetailResponse joinByShareCode(Long userId, String shareCode, JoinListRequest request) {
        // A elegibilidade abaixo consulta a TibiaData (com cache por personagem):
        // sem limite por usuário, entrar em time vira um proxy para a API deles.
        userRateLimiter.checkTibiaDataLookup(userId);
        Long listId = listRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"))
                .getId();
        // Trava a linha do time: impede corrida no limite de vagas.
        HuntingList list = listRepository.findByIdForUpdate(listId).orElseThrow();
        if (list.getStatus() != TeamStatus.ACTIVE) {
            throw new BusinessRuleException("Este time não está aceitando novos membros");
        }

        Character character = loadOwnedCharacter(request.characterId(), userId);
        Vocation vocacao = Vocation.fromTibiaData(
                eligibilityService.assertEligible(character, list.getWorld(), list.getMinimumLevel())
                        .vocation());
        // Recusa já no pedido quem não caberia em vaga nenhuma da composição —
        // nem ocupada. Esperar aprovação que nunca pode vir é o limbo que o P4 acabou.
        slotAssigner.assertVocationHasSlot(list, vocacao);

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
            // Entra aprovado, então já ocupa vaga (se o time tiver composição).
            slotAssigner.assignSlot(list, vocacao).ifPresent(membership::setSlot);
            membership.setStatus(MembershipStatus.APPROVED);
        } else {
            membership.setStatus(MembershipStatus.PENDING);
            // Aprovação manual: avisa o dono que há um pedido a decidir (item 7).
            notificationService.notifyJoinRequestReceived(list.getOwner().getId(), list);
        }
        membership.setActive(true);
        membershipRepository.save(membership);

        log.info("list.join listId={} userId={} characterId={} status={}",
                list.getId(), userId, character.getId(), membership.getStatus());
        // Entrou por AUTO_ACCEPT: já é membro aprovado e o contato do dono vem
        // na resposta. Se ficou PENDING, não vem — é o que separa "pedi" de
        // "estou no time".
        return buildDetail(list, userId);
    }

    @Override
    @Transactional
    public void approveJoinRequest(Long ownerId, Long listId, Long membershipId) {
        HuntingList list = loadOwnedListForUpdate(listId, ownerId);
        ListMembership membership = loadPendingRequest(membershipId, list.getId());

        // Ordem importa: vaga é um COUNT no banco, elegibilidade pode custar uma
        // chamada externa. Time cheio nunca consulta a TibiaData.
        assertHasOpenSlot(list.getId());
        Vocation vocacao = assertStillEligible(list, membership);
        // A vaga é atribuída AQUI, não no pedido: pedido não reserva vaga (cinco
        // pedidos travariam o time), então quem é aprovado primeiro ocupa primeiro.
        slotAssigner.assignSlot(list, vocacao).ifPresent(membership::setSlot);
        membership.setStatus(MembershipStatus.APPROVED);
        notificationService.notifyJoinRequestApproved(membership.getUser().getId(), list);
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
        notificationService.notifyJoinRequestRejected(membership.getUser().getId(), list);
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
        // Avisa o dono que um membro saiu (item 7).
        notificationService.notifyMemberLeft(list.getOwner().getId(), list);
        log.info("list.leave listId={} userId={} count={}", listId, userId, memberships.size());
    }

    @Override
    @Transactional
    public ListDetailResponse renewTeam(Long ownerId, Long listId) {
        HuntingList list = loadOwnedListForUpdate(listId, ownerId);
        if (list.getStatus() != TeamStatus.ARCHIVED) {
            throw new BusinessRuleException("Só é possível renovar um time arquivado");
        }
        // Renovar consome uma vaga do plano, igual criar.
        assertWithinActiveTeamLimit(list.getOwner());
        list.setStatus(TeamStatus.ACTIVE);
        list.setExpiresAt(Instant.now().plus(planPolicy.teamDuration(list.getOwner().getPlan())));
        log.info("team.renewed listId={} ownerId={} newExpiresAt={}", listId, ownerId, list.getExpiresAt());
        return buildDetail(list, ownerId);
    }

    @Override
    @Transactional
    public void kickMember(Long ownerId, Long listId, Long membershipId) {
        HuntingList list = loadOwnedList(listId, ownerId); // 403 se não for o dono
        ListMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado"));
        if (!membership.getList().getId().equals(listId)) {
            throw new ResourceNotFoundException("Membro não pertence a este time");
        }
        if (membership.getUser().getId().equals(ownerId)) {
            throw new BusinessRuleException("O dono não pode expulsar a si mesmo");
        }
        // Nunca deleta: desativa e preserva o histórico (mensagens de chat ficam).
        membership.setActive(false);
        // Notifica o expulso (item 7).
        notificationService.notifyKicked(membership.getUser().getId(), list);
        log.info("list.member.kicked listId={} membershipId={} kickedUserId={}",
                listId, membershipId, membership.getUser().getId());
    }

    @Override
    @Transactional
    public void deleteTeam(Long ownerId, Long listId) {
        HuntingList list = loadOwnedList(listId, ownerId); // 403 se não for o dono
        if (list.getStatus() == TeamStatus.CLOSED) {
            throw new BusinessRuleException("Este time já foi encerrado");
        }
        // Exclusão lógica: preserva o histórico (padrão do projeto). Some da
        // busca e vira só leitura; membros continuam vendo em "meus times".
        list.setStatus(TeamStatus.CLOSED);
        // Notifica todos os membros ativos aprovados, exceto o próprio dono (item 7).
        membershipRepository.findAllByListIdAndStatusAndActiveTrue(listId, MembershipStatus.APPROVED).stream()
                .map(m -> m.getUser().getId())
                .filter(uid -> !uid.equals(ownerId))
                .distinct()
                .forEach(uid -> notificationService.notifyTeamDeleted(uid, list));
        log.info("team.closed listId={} ownerId={}", listId, ownerId);
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
    public ListDetailResponse getList(Long listId, Long viewerId) {
        HuntingList list = listRepository.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("Time não encontrado"));
        return buildDetail(list, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ListSummaryResponse> search(String world, Long creatureId, Boolean hasOpenSlots,
                                            Vocation vocation, Pageable pageable) {
        // Todos os filtros vão para a query: o total da página precisa ser o
        // total de verdade, senão a paginação da home não sabe quando parar.
        return listRepository
                .search(blankToNull(world), creatureId, Boolean.TRUE.equals(hasOpenSlots),
                        vocation, maxMembers, pageable)
                .map(this::toSummary);
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

    @Override
    @Transactional(readOnly = true)
    public List<MyJoinRequestResponse> listMyJoinRequests(Long userId) {
        // APPROVED fica de fora: aprovado não é "pedido", é time — e aparece em
        // "meus times". CANCELLED também não: quem cancelou sabe que cancelou.
        List<ListMembership> pedidos = membershipRepository
                .findAllByUserIdAndStatusInOrderByJoinedAtDesc(
                        userId, List.of(MembershipStatus.PENDING, MembershipStatus.REJECTED));
        // A composição de todos os times de uma vez: sem isto, cada pedido da lista
        // custaria uma consulta de vagas (N+1 numa tela que já é uma lista).
        Map<Long, List<Vocation>> composicoes = slotAssigner.compositionsOf(
                pedidos.stream().map(m -> m.getList().getId()).distinct().toList());
        return pedidos.stream()
                .map(m -> MyJoinRequestResponse.from(
                        m, detectIssue(m, composicoes.getOrDefault(m.getList().getId(), List.of()))))
                .toList();
    }

    @Override
    @Transactional
    public void cancelMyJoinRequest(Long userId, Long membershipId) {
        ListMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        // 404 e não 403: a existência do pedido de outra pessoa é informação dela.
        if (!membership.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Pedido não encontrado");
        }
        if (membership.getStatus() != MembershipStatus.PENDING || !membership.isActive()) {
            throw new BusinessRuleException("Este pedido não está mais pendente");
        }
        // Status próprio: quem desistiu foi o solicitante, não o dono (que seria
        // REJECTED). Nada é deletado — o histórico é preservado.
        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setActive(false);
        log.info("list.request.cancelled listId={} membershipId={} userId={}",
                membership.getList().getId(), membershipId, userId);
    }

    /**
     * Motivo aparente de um pedido pendente não poder ser aprovado, usando **só
     * dado local** (nada de TibiaData): o level e o world já sincronizados do
     * personagem contra o requisito **atual** do time.
     *
     * Cobre os dois casos que a revalidação da aprovação (P15) mais recusa e que o
     * solicitante não tinha como descobrir — o dono subiu o level mínimo depois do
     * pedido, ou o personagem trocou de world. Perda de Premium **não** aparece
     * aqui (não é dado local), então nulo não é promessa de aprovação.
     */
    /**
     * O motivo pelo qual este pedido pendente <b>provavelmente</b> não será aprovado,
     * ou {@code null} quando não há nada aparente.
     *
     * <p>A ordem é a precedência do {@link JoinRequestIssue}: o que a pessoa
     * <b>não pode consertar</b> primeiro. World e vocação são definitivos (a ação é
     * usar outro personagem); level é temporário (a ação é jogar). Mostrar o motivo
     * consertável quando existe um definitivo manda a pessoa gastar tempo no lugar
     * errado.</p>
     *
     * @param composicao vocações exigidas por vaga do time — lista <b>vazia</b> é time
     *                   sem composição, que aceita qualquer vocação
     */
    private JoinRequestIssue detectIssue(ListMembership membership, List<Vocation> composicao) {
        if (membership.getStatus() != MembershipStatus.PENDING) {
            return null;
        }
        HuntingList list = membership.getList();
        Character character = membership.getCharacter();
        if (!list.getWorld().equalsIgnoreCase(character.getWorld())) {
            return JoinRequestIssue.WORLD_MISMATCH;
        }
        if (!cabeNaComposicao(composicao, character)) {
            return JoinRequestIssue.VOCATION_NOT_IN_COMPOSITION;
        }
        Integer minimum = list.getMinimumLevel();
        Integer level = character.getLevel();
        if (minimum != null && level != null && level < minimum) {
            return JoinRequestIssue.BELOW_MINIMUM_LEVEL;
        }
        return null;
    }

    /**
     * Existe <b>alguma</b> vaga na composição que aceite a vocação deste personagem?
     *
     * <p>"Alguma", e não "alguma livre", de propósito: pedido para vaga ocupada é
     * legítimo — quem está nela pode sair, e é assim que time cheio com aprovação
     * manual funciona. O que torna o pedido inaprovável é a composição não
     * <b>prever</b> a vocação (mesma regra do
     * {@code TeamSlotAssigner.assertVocationHasSlot}, aplicada ao pedido que já
     * existia).</p>
     */
    private boolean cabeNaComposicao(List<Vocation> composicao, Character character) {
        return TeamSlotAssigner.fitsComposition(
                composicao, Vocation.fromTibiaData(character.getVocation()));
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
            throw new ForbiddenOperationException("Apenas o dono do time pode fazer isso");
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

    /**
     * Revalida a elegibilidade **na hora da aprovação**, com o world e o level
     * mínimo ATUAIS do time.
     *
     * Por que não bastava validar na entrada: entre pedir e ser aprovado o mundo
     * muda. O dono pode ter subido o level mínimo (editar virou possível), o
     * personagem pode ter feito world transfer, perdido o Premium ou caído de
     * level. Sem isto, o time acabava com um membro que não cumpre a regra que o
     * próprio time anuncia — e quem descobria depois era o dono, que aprovou de
     * boa-fé.
     *
     * <p><b>O pedido continua `PENDING`.</b> Recusar por inelegibilidade não é
     * "o dono não quis" (que é o significado de `REJECTED`, e dispara a
     * notificação de recusa): a inelegibilidade costuma ser temporária e
     * consertável — renovar o premium, subir de level, voltar de world. Deixando
     * pendente, o dono aprova depois sem a pessoa precisar pedir de novo; e o
     * botão de recusar continua ali para quando ele quiser mesmo dizer não.</p>
     */
    private Vocation assertStillEligible(HuntingList list, ListMembership membership) {
        try {
            return Vocation.fromTibiaData(eligibilityService.assertEligible(
                    membership.getCharacter(), list.getWorld(), list.getMinimumLevel()).vocation());
        } catch (BusinessRuleException e) {
            // Reembrulha para deixar claro para o DONO (que é quem lê) que a
            // recusa é do candidato e que nada foi alterado.
            throw new BusinessRuleException(
                    "Não é possível aprovar este pedido agora: " + e.getMessage()
                            + ". O pedido continua pendente.");
        }
    }

    private void assertWithinActiveTeamLimit(User owner) {
        long activeTeams = listRepository.countByOwnerIdAndStatus(owner.getId(), TeamStatus.ACTIVE);
        int limit = planPolicy.maxActiveTeams(owner.getPlan());
        if (activeTeams >= limit) {
            throw new BusinessRuleException(
                    "Você atingiu o limite de " + limit + " times ativos do seu plano. "
                            + "Conclua, deixe expirar ou assine o premium para criar mais.");
        }
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

    private ListDetailResponse buildDetail(HuntingList list, Long viewerId) {
        List<MembershipResponse> members = membershipRepository
                .findAllByListIdAndActiveTrue(list.getId()).stream()
                .map(MembershipResponse::from)
                .toList();
        long approved = membershipRepository
                .countByListIdAndActiveTrueAndStatus(list.getId(), MembershipStatus.APPROVED);
        return ListDetailResponse.from(list, approved, maxMembers, members,
                canSeeContact(list, viewerId), slotsOf(list.getId()));
    }

    /**
     * Contato do dono é dado pessoal: só o dono e quem já foi aprovado no time
     * enxergam. Pedido pendente **não** basta — se bastasse, qualquer pessoa
     * pegaria o contato de qualquer dono só clicando em "entrar".
     */
    private boolean canSeeContact(HuntingList list, Long viewerId) {
        if (viewerId == null || list.getContact() == null) {
            return false;
        }
        return list.getOwner().getId().equals(viewerId)
                || membershipRepository.existsByListIdAndUserIdAndActiveTrueAndStatus(
                        list.getId(), viewerId, MembershipStatus.APPROVED);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Avisa os membros aprovados quando muda algo que altera a **decisão de
     * participar**: horário da caçada e level mínimo.
     *
     * O que fica de fora é a parte importante desta regra:
     * <ul>
     *   <li><b>Descrição, contato, título e preço não notificam.</b> Corrigir uma
     *       vírgula no texto não pode virar notificação para todo o time — o
     *       aviso que chega demais é o aviso que se aprende a ignorar.</li>
     *   <li><b>Valor igual não notifica.</b> Salvar o formulário sem mexer no
     *       campo é o caso mais comum de todos (o dono abriu para editar a
     *       descrição), e não é mudança nenhuma.</li>
     *   <li><b>O dono não recebe aviso da própria edição</b>, e pedido
     *       {@code PENDING} também não: quem ainda não está no time não tem
     *       plano para reorganizar.</li>
     * </ul>
     *
     * Definir pela primeira vez (nulo → valor) e apagar (valor → nulo) contam
     * como mudança: um horário que apareceu ou desapareceu muda o combinado.
     */
    private void notifyRelevantChanges(HuntingList list, Long ownerId,
                                       String horarioAnterior, Integer levelAnterior) {
        boolean horarioMudou = !Objects.equals(horarioAnterior, list.getHuntSchedule());
        boolean levelMudou = !Objects.equals(levelAnterior, list.getMinimumLevel());
        if (!horarioMudou && !levelMudou) {
            return;
        }
        List<Long> destinatarios = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(list.getId(), MembershipStatus.APPROVED).stream()
                .map(m -> m.getUser().getId())
                .filter(uid -> !uid.equals(ownerId))
                .distinct()
                .toList();
        destinatarios.forEach(uid -> {
            if (horarioMudou) {
                notificationService.notifyTeamScheduleChanged(uid, list);
            }
            if (levelMudou) {
                notificationService.notifyTeamMinimumLevelChanged(uid, list);
            }
        });
        int emRisco = levelMudou ? notifyPendingRequestsAtRisk(list, levelAnterior) : 0;
        log.info("list.updated.notified listId={} scheduleChanged={} minimumLevelChanged={} members={} pendingAtRisk={}",
                list.getId(), horarioMudou, levelMudou, destinatarios.size(), emRisco);
    }

    /**
     * Avisa quem tem pedido **pendente** que o pedido dele passou a não caber no
     * requisito — o level mínimo subiu acima do personagem que ele usou.
     *
     * <p>Três recortes que impedem isto de virar spam:</p>
     * <ul>
     *   <li><b>Só a transição.</b> Notifica quem <b>estava</b> dentro do requisito
     *       e <b>passou</b> a estar fora. Quem já não cabia antes da edição não é
     *       avisado de novo: subir de 300 para 400 não é notícia nova para quem
     *       tem level 150 — ele já sabia que não cabia.</li>
     *   <li><b>Só o level mínimo.</b> Mudança de horário não avisa pendente: quem
     *       ainda não entrou provavelmente não se organizou em volta do horário.
     *       (World não é editável, então não existe esse caso.)</li>
     *   <li><b>Level desconhecido não avisa.</b> Sem o level sincronizado não há
     *       violação a provar — o mesmo critério do aviso da tela "meus pedidos".</li>
     * </ul>
     *
     * <p>A notificação aponta o time e diz que o pedido corre risco; <b>os números</b>
     * (requisito atual × level do personagem) ficam na aba "meus pedidos", que já os
     * calcula. Notificação empurra, tela explica.</p>
     *
     * @return quantos solicitantes foram avisados
     */
    private int notifyPendingRequestsAtRisk(HuntingList list, Integer levelAnterior) {
        Integer levelAtual = list.getMinimumLevel();
        if (levelAtual == null) {
            return 0; // requisito removido: ninguém passou a ficar de fora
        }
        List<Long> avisados = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(list.getId(), MembershipStatus.PENDING).stream()
                .filter(m -> passouAFicarDeFora(m.getCharacter().getLevel(), levelAnterior, levelAtual))
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
        avisados.forEach(uid -> notificationService.notifyJoinRequestAtRisk(uid, list));
        return avisados.size();
    }

    /**
     * Avisa quem tem pedido <b>pendente</b> que a composição nova do time não tem vaga
     * para a vocação do personagem dele (P21).
     *
     * <p>Os mesmos recortes do irmão de level ({@link #notifyPendingRequestsAtRisk}),
     * porque o problema é o mesmo:</p>
     * <ul>
     *   <li><b>Só a transição.</b> Quem cabia e passou a não caber. Quem já não cabia
     *       (pediu antes de o dono apertar a composição uma primeira vez) não é
     *       avisado de novo a cada reordenação.</li>
     *   <li><b>Composição removida não avisa.</b> Lista vazia = time sem composição =
     *       todo mundo cabe: ninguém passou a ficar de fora.</li>
     *   <li><b>Vaga ocupada não conta como falta de vaga.</b> A pergunta é se a
     *       composição <b>prevê</b> a vocação — pedido para vaga ocupada é legítimo,
     *       e tratá-lo como problema encheria de aviso todo time cheio.</li>
     * </ul>
     *
     * <p>A notificação diz o time e o motivo; <b>qual</b> vocação ficou sem vaga fica
     * na aba "meus pedidos", que já recebe o {@code characterVocation}. Notificação
     * empurra, tela explica — a mesma divisão do P18.</p>
     *
     * @return quantos solicitantes foram avisados
     */
    private int notifyPendingRequestsWithoutSlot(HuntingList list, List<Vocation> composicaoAnterior) {
        List<Vocation> composicaoAtual = slotAssigner.compositionOf(list.getId());
        if (composicaoAtual.isEmpty()) {
            return 0; // composição removida: todo mundo volta a caber
        }
        List<Long> avisados = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(list.getId(), MembershipStatus.PENDING).stream()
                .filter(m -> passouAFicarSemVaga(m.getCharacter(), composicaoAnterior, composicaoAtual))
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();
        avisados.forEach(uid -> notificationService.notifyJoinRequestCompositionMismatch(uid, list));
        return avisados.size();
    }

    private boolean passouAFicarSemVaga(Character character,
                                        List<Vocation> composicaoAnterior,
                                        List<Vocation> composicaoAtual) {
        Vocation vocacao = Vocation.fromTibiaData(character.getVocation());
        boolean cabiaAntes = TeamSlotAssigner.fitsComposition(composicaoAnterior, vocacao);
        boolean cabeAgora = TeamSlotAssigner.fitsComposition(composicaoAtual, vocacao);
        return cabiaAntes && !cabeAgora;
    }

    private boolean passouAFicarDeFora(Integer levelDoPersonagem, Integer minimoAnterior, Integer minimoAtual) {
        if (levelDoPersonagem == null) {
            return false;
        }
        boolean estavaDeFora = minimoAnterior != null && levelDoPersonagem < minimoAnterior;
        boolean estaDeFora = levelDoPersonagem < minimoAtual;
        return estaDeFora && !estavaDeFora;
    }

    /**
     * Ao subir o level mínimo, o dono precisa continuar cumprindo o requisito que
     * ele mesmo define — é a mesma invariante da criação, e sem ela dá para
     * montar "time exige level 500, dono é level 100".
     *
     * Duas escolhas explícitas aqui:
     * <ul>
     *   <li><b>Quem já foi aprovado fica.</b> A elegibilidade sempre foi validada
     *       no momento da entrada e nunca reavaliada depois (personagem que perde
     *       level também não é expulso). Reavaliar na edição transformaria uma
     *       correção de anúncio em expulsão em massa — e o projeto não remove
     *       membro nas costas de ninguém.</li>
     *   <li><b>Level local, não TibiaData.</b> Usa o `level` já sincronizado do
     *       personagem: editar o anúncio não pode depender de API externa nem
     *       gastar a cota de consultas do usuário. Level desconhecido (nulo, nunca
     *       sincronizado) não bloqueia — não dá para provar violação, e travar a
     *       edição por isso seria um beco sem explicação.</li>
     * </ul>
     */
    private void assertOwnerStillMeetsMinimumLevel(HuntingList list, Long ownerId, Integer minimumLevel) {
        if (minimumLevel == null) {
            return;
        }
        List<ListMembership> ownerMemberships = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(list.getId(), MembershipStatus.APPROVED).stream()
                .filter(m -> m.getUser().getId().equals(ownerId))
                .toList();
        if (ownerMemberships.isEmpty()) {
            return;
        }
        boolean algumAtende = ownerMemberships.stream()
                .map(m -> m.getCharacter().getLevel())
                .anyMatch(level -> level == null || level >= minimumLevel);
        if (!algumAtende) {
            int maiorLevel = ownerMemberships.stream()
                    .map(m -> m.getCharacter().getLevel())
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElseThrow();
            throw new BusinessRuleException(
                    "Você não pode exigir level " + minimumLevel
                            + ": seu personagem no time tem level " + maiorLevel);
        }
    }

    private ListSummaryResponse toSummary(HuntingList list) {
        long approved = membershipRepository
                .countByListIdAndActiveTrueAndStatus(list.getId(), MembershipStatus.APPROVED);
        return ListSummaryResponse.from(list, approved, maxMembers, slotsOf(list.getId()));
    }

    /**
     * As vagas do time com quem as ocupa. Lista vazia = time sem composição.
     *
     * Uma consulta de vagas + uma de memberships por time — o mesmo custo por item
     * que a contagem de membros que já existia (e que segue como gatilho de escala
     * na seção 5 do NEXT_STEPS, agora com mais um motivo).
     */
    private List<TeamSlotResponse> slotsOf(Long listId) {
        List<TeamSlot> slots = slotAssigner.slotsOf(listId);
        if (slots.isEmpty()) {
            return List.of();
        }
        List<ListMembership> ocupantes = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(listId, MembershipStatus.APPROVED);
        return slots.stream()
                .map(slot -> ocupantes.stream()
                        .filter(m -> m.getSlot() != null && m.getSlot().getId().equals(slot.getId()))
                        .findFirst()
                        .map(m -> TeamSlotResponse.occupied(slot, m))
                        .orElseGet(() -> TeamSlotResponse.open(slot)))
                .toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
