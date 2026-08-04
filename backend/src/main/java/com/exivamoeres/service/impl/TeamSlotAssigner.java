package com.exivamoeres.service.impl;

import com.exivamoeres.config.TeamProperties;
import com.exivamoeres.domain.HuntingList;
import com.exivamoeres.domain.ListMembership;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.TeamSlot;
import com.exivamoeres.domain.Vocation;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.error.ErrorCode;
import com.exivamoeres.repository.ListMembershipRepository;
import com.exivamoeres.repository.TeamSlotRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Composição do time por vocação: configurar as vagas e decidir quem cabe em qual.
 *
 * <p>Concentra a regra num lugar só porque ela aparece em quatro fluxos (criar,
 * entrar, aprovar e reconfigurar) — é o mesmo motivo do {@code TeamMembershipGuard}.</p>
 *
 * <p><b>Um time ou tem composição, ou não tem.</b> Sem vaga configurada, nada muda
 * em relação ao que existia antes: qualquer vocação entra. É o estado de todos os
 * times criados antes da V17, e continua sendo o default de quem não quiser
 * restringir.</p>
 */
@Component
public class TeamSlotAssigner {

    private final TeamSlotRepository slotRepository;
    private final ListMembershipRepository membershipRepository;
    private final int maxMembers;

    public TeamSlotAssigner(TeamSlotRepository slotRepository,
                            ListMembershipRepository membershipRepository,
                            TeamProperties teamProperties) {
        this.slotRepository = slotRepository;
        this.membershipRepository = membershipRepository;
        this.maxMembers = teamProperties.maxMembers();
    }

    /**
     * Cria a composição de um time novo. Lista nula, vazia ou só com nulos = **sem
     * composição** (o mesmo que não configurar).
     *
     * <p>Uma lista mais curta que o time é completada com <b>vagas livres</b>: quem
     * quer "1 EK e 1 ED, o resto tanto faz" manda duas entradas, não cinco.</p>
     */
    public List<TeamSlot> createSlots(HuntingList list, List<Vocation> requested) {
        List<Vocation> normalizado = normalize(requested);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        List<TeamSlot> slots = new ArrayList<>();
        for (int i = 0; i < normalizado.size(); i++) {
            TeamSlot slot = new TeamSlot();
            slot.setList(list);
            slot.setPosition(i + 1);
            slot.setVocation(normalizado.get(i));
            slots.add(slotRepository.save(slot));
        }
        return slots;
    }

    /**
     * Substitui a composição do time (o `PUT /api/lists/{id}/slots`).
     *
     * <p><b>A composição tem que caber no time que existe.</b> Toda membership ativa
     * e aprovada é <b>reassentada</b> na composição nova; se alguém não couber, a
     * operação é recusada inteira, dizendo quem barra. Nenhum membro é expulso, e o
     * time nunca fica anunciando uma composição que não corresponde a quem está
     * dentro — que é o estado errado que uma checagem de "vaga ocupada" deixaria
     * passar em time criado antes da V17 (membros sem vaga nenhuma).</p>
     *
     * <p>Consequência boa: o dono pode <b>reordenar</b> livremente enquanto todos
     * continuarem cabendo. Lista vazia remove a composição e libera as vagas de
     * todos — sempre permitido, porque time sem composição aceita qualquer um.</p>
     *
     * <p>A vocação usada aqui é a <b>local</b> (sincronizada), não a da TibiaData:
     * reconfigurar o anúncio não pode depender de API externa nem gastar a cota do
     * usuário — a mesma decisão do aviso de "meus pedidos" (P4). Entrada e aprovação
     * continuam usando o snapshot fresco.</p>
     */
    public List<TeamSlot> replaceSlots(HuntingList list, List<Vocation> requested) {
        List<Vocation> desejado = normalize(requested);
        List<ListMembership> membros = membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(list.getId(), MembershipStatus.APPROVED);

        // 1. Todo mundo cabe? Decide antes de escrever qualquer coisa.
        List<TeamSlot> novasVagas = materialize(list, desejado);
        Map<Long, TeamSlot> assentos = seat(membros, novasVagas);

        // 2. Persiste as vagas (reaproveitando as posições que continuam existindo,
        //    para não invalidar ids à toa) e reassenta os membros.
        List<TeamSlot> atuais = slotRepository.findAllByListIdOrderByPosition(list.getId());
        for (TeamSlot atual : atuais) {
            if (desejado.size() >= atual.getPosition()) {
                atual.setVocation(desejado.get(atual.getPosition() - 1));
            }
        }
        List<TeamSlot> resultado = new ArrayList<>(
                atuais.stream().filter(s -> desejado.size() >= s.getPosition()).toList());
        for (int posicao = atuais.size() + 1; posicao <= desejado.size(); posicao++) {
            TeamSlot slot = new TeamSlot();
            slot.setList(list);
            slot.setPosition(posicao);
            slot.setVocation(desejado.get(posicao - 1));
            resultado.add(slotRepository.save(slot));
        }

        // Reassenta pelo POSIÇÃO calculada no passo 1 (as vagas persistidas podem ter
        // ids diferentes das materializadas em memória).
        for (ListMembership membro : membros) {
            TeamSlot planejada = assentos.get(membro.getId());
            membro.setSlot(planejada == null ? null : resultado.stream()
                    .filter(s -> s.getPosition() == planejada.getPosition())
                    .findFirst().orElse(null));
        }

        // Vagas que sobraram (composição encurtada) saem depois de ninguém apontar
        // para elas.
        atuais.stream()
                .filter(s -> desejado.size() < s.getPosition())
                .forEach(slotRepository::delete);
        return resultado;
    }

    /** Vagas "de mentira" (só posição + vocação) para simular o assentamento. */
    private List<TeamSlot> materialize(HuntingList list, List<Vocation> desejado) {
        List<TeamSlot> vagas = new ArrayList<>();
        for (int i = 0; i < desejado.size(); i++) {
            TeamSlot slot = new TeamSlot();
            slot.setList(list);
            slot.setPosition(i + 1);
            slot.setVocation(desejado.get(i));
            vagas.add(slot);
        }
        return vagas;
    }

    /**
     * Distribui os membros nas vagas: vaga com exigência primeiro (senão o primeiro
     * Knight consome a vaga livre e a de Knight sobra), e quem tem vocação mais
     * restrita é assentado antes.
     *
     * @return membershipId → vaga planejada (vazio quando não há composição)
     * @throws BusinessRuleException se algum membro não couber
     */
    private Map<Long, TeamSlot> seat(List<ListMembership> membros, List<TeamSlot> vagas) {
        if (vagas.isEmpty()) {
            return Map.of(); // sem composição: ninguém ocupa vaga
        }
        Map<Long, TeamSlot> assentos = new HashMap<>();
        Set<Integer> ocupadas = new HashSet<>();
        // Vocação com menos vagas disponíveis primeiro: assentar o caso difícil antes
        // evita recusar por ordem de chegada.
        List<ListMembership> ordenados = new ArrayList<>(membros);
        ordenados.sort(java.util.Comparator.comparingLong(m -> vagas.stream()
                .filter(v -> v.accepts(vocationOf(m)))
                .count()));

        for (ListMembership membro : ordenados) {
            Vocation vocacao = vocationOf(membro);
            TeamSlot escolhida = vagas.stream()
                    .filter(v -> !ocupadas.contains(v.getPosition()))
                    .filter(v -> v.accepts(vocacao))
                    .sorted((a, b) -> Boolean.compare(a.getVocation() == null, b.getVocation() == null))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            ErrorCode.COMPOSITION_EXCLUDES_MEMBER,
                            "A composição não tem vaga para " + descrever(vocacao)
                                    + " (" + membro.getCharacter().getName() + "), que já está no time.")
                            .with("vocation", nomeDaVocacao(vocacao))
                            .with("character", membro.getCharacter().getName()));
            ocupadas.add(escolhida.getPosition());
            assentos.put(membro.getId(), escolhida);
        }
        return assentos;
    }

    private Vocation vocationOf(ListMembership membership) {
        return Vocation.fromTibiaData(membership.getCharacter().getVocation());
    }

    /**
     * Escolhe a vaga que este personagem vai ocupar, ou devolve vazio quando o time
     * <b>não tem composição</b> (nada a decidir).
     *
     * <p>Pega a <b>primeira vaga livre compatível</b>, e prefere vaga com vocação
     * exigida à vaga livre: senão o primeiro Knight a entrar consumiria a vaga
     * "qualquer" e a vaga de Knight ficaria sobrando, fazendo o time anunciar que
     * falta Knight quando não falta.</p>
     *
     * @throws BusinessRuleException se o time tem composição e não há vaga livre
     *                               compatível
     */
    public Optional<TeamSlot> assignSlot(HuntingList list, Vocation vocation) {
        List<TeamSlot> slots = slotRepository.findAllByListIdOrderByPosition(list.getId());
        if (slots.isEmpty()) {
            return Optional.empty();
        }
        Set<Long> ocupadas = occupiedSlotIds(list.getId());
        return slots.stream()
                .filter(s -> !ocupadas.contains(s.getId()))
                .filter(s -> s.accepts(vocation))
                // Vaga com exigência primeiro; a vaga livre é o último recurso.
                .sorted((a, b) -> Boolean.compare(a.getVocation() == null, b.getVocation() == null))
                .findFirst()
                .map(Optional::of)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NO_FREE_SLOT_FOR_VOCATION,
                        "Não há vaga livre para " + descrever(vocation) + " neste time.")
                        .with("vocation", nomeDaVocacao(vocation)));
    }

    /**
     * Recusa, já no pedido, quem não caberia em vaga nenhuma — nem ocupada.
     *
     * <p>Diferente do {@link #assignSlot}: aqui não se exige vaga <b>livre</b>. Um
     * pedido para vaga ocupada é legítimo (alguém pode sair, e é assim que o time
     * cheio já funciona com aprovação manual); um pedido para uma vocação que a
     * composição nem prevê nunca poderá ser aprovado, e deixar a pessoa esperando
     * por isso é o tipo de limbo que o P4 existiu para acabar.</p>
     */
    public void assertVocationHasSlot(HuntingList list, Vocation vocation) {
        List<TeamSlot> slots = slotRepository.findAllByListIdOrderByPosition(list.getId());
        if (slots.isEmpty()) {
            return;
        }
        boolean cabeEmAlguma = slots.stream().anyMatch(s -> s.accepts(vocation));
        if (!cabeEmAlguma) {
            throw new BusinessRuleException(ErrorCode.VOCATION_WITHOUT_SLOT,
                    "A composição deste time não tem vaga para " + descrever(vocation) + ".")
                    .with("vocation", descrever(vocation));
        }
    }

    public List<TeamSlot> slotsOf(Long listId) {
        return slotRepository.findAllByListIdOrderByPosition(listId);
    }

    /** As vocações exigidas por vaga, na ordem — vazio quando o time não tem composição. */
    public List<Vocation> compositionOf(Long listId) {
        return slotsOf(listId).stream().map(TeamSlot::getVocation).toList();
    }

    /**
     * A composição de vários times numa consulta só, para telas que listam pedidos de
     * times diferentes (o "meus pedidos"). Time sem vaga não aparece no mapa — quem
     * chama trata ausência como "sem composição".
     */
    public Map<Long, List<Vocation>> compositionsOf(Collection<Long> listIds) {
        if (listIds.isEmpty()) {
            return Map.of();
        }
        return slotRepository.findAllByListIdIn(listIds).stream()
                .sorted(java.util.Comparator.comparingInt(TeamSlot::getPosition))
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s.getList().getId(),
                        java.util.stream.Collectors.mapping(TeamSlot::getVocation, java.util.stream.Collectors.toList())));
    }

    /**
     * Esta vocação tem lugar nesta composição? Vazio = time sem composição = cabe.
     *
     * <p><b>Lugar</b>, não <b>vaga livre</b>: é a pergunta do pedido pendente (ver
     * {@link #assertVocationHasSlot}), não a da aprovação.</p>
     */
    public static boolean fitsComposition(List<Vocation> composicao, Vocation vocacao) {
        return composicao.isEmpty() || composicao.stream().anyMatch(exigida -> vocacao != null && vocacao.fits(exigida));
    }

    /** Memberships ativas e aprovadas que ocupam vaga, por id de vaga. */
    public Set<Long> occupiedSlotIds(Long listId) {
        return membershipRepository
                .findAllByListIdAndStatusAndActiveTrue(listId, MembershipStatus.APPROVED).stream()
                .map(ListMembership::getSlot)
                .filter(java.util.Objects::nonNull)
                .map(TeamSlot::getId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    private boolean isOccupied(TeamSlot slot) {
        return occupiedSlotIds(slot.getList().getId()).contains(slot.getId());
    }

    /**
     * Normaliza a lista pedida: apara o tamanho ao máximo do time e trata
     * "tudo livre" como "sem composição" (5 vagas livres se comportam exatamente
     * como time sem vaga, então não vale gravar cinco linhas para isso).
     */
    private List<Vocation> normalize(List<Vocation> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > maxMembers) {
            throw new BusinessRuleException(ErrorCode.COMPOSITION_TOO_LARGE,
                    "A composição não pode ter mais de " + maxMembers + " vagas.")
                    .with("max", maxMembers);
        }
        if (requested.stream().allMatch(java.util.Objects::isNull)) {
            return List.of();
        }
        return requested;
    }

    private String descrever(Vocation vocation) {
        return vocation == null || vocation == Vocation.NONE ? "personagem sem vocação" : vocation.name();
    }

    /**
     * O <b>valor do enum</b> para os params da recusa — não o rótulo em português do
     * {@link #descrever}.
     *
     * ⚠️ A tela traduz `NONE` como "sem vocação" (`enums.vocation.NONE`), então mandar o
     * texto pronto daqui faria a frase em inglês receber uma palavra em português no meio.
     * Nulo e `NONE` são a mesma coisa para quem lê, e o enum já tem o valor para isso.
     */
    private String nomeDaVocacao(Vocation vocation) {
        return vocation == null ? Vocation.NONE.name() : vocation.name();
    }
}
