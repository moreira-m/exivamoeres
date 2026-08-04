package com.exivamoeres.service;

import com.exivamoeres.domain.Vocation;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MyTeamsScope;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.dto.list.MyJoinRequestResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.dto.list.UpdateSlotsRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Times de caça (soulcore teams): criação, entrada (com aprovação manual ou
 * automática conforme a política do time), saída e busca pública.
 *
 * Regras sempre validadas aqui, nunca só no frontend: tamanho máximo (ver
 * TeamProperties), world e Free/Premium (via TeamEligibilityService).
 */
public interface HuntingListService {

    /** Cria o time e já inclui o criador como primeiro membro (aprovado). */
    ListDetailResponse createList(Long ownerId, CreateListRequest request);

    /** Entra por share_code. Vira PENDING ou APPROVED conforme a join_policy do time. */
    ListDetailResponse joinByShareCode(Long userId, String shareCode, JoinListRequest request);

    /**
     * Edita os campos editáveis do time — **só o dono** (403 caso contrário) e
     * **só em time ATIVO** (arquivado/encerrado é somente leitura, igual chat).
     *
     * O payload é o conjunto completo dos campos editáveis: nulo/branco
     * **limpa**. World, criatura-alvo e política de entrada não são editáveis
     * (ver {@link com.exivamoeres.dto.list.UpdateListRequest}).
     */
    ListDetailResponse updateList(Long ownerId, Long listId, UpdateListRequest request);

    /** Só o dono do time pode aprovar. */
    void approveJoinRequest(Long ownerId, Long listId, Long membershipId);

    /** Só o dono do time pode recusar. */
    void rejectJoinRequest(Long ownerId, Long listId, Long membershipId);

    /** Sai do time = active=false; histórico nunca é deletado. */
    void leaveList(Long userId, Long listId);

    /**
     * Reativa um time ARQUIVADO (só o dono), renovando o prazo. Consome uma
     * vaga do limite de times ativos do plano.
     */
    ListDetailResponse renewTeam(Long ownerId, Long listId);

    /**
     * Expulsa um membro (só o dono; 403 caso contrário). Desativa a membership
     * e libera a vaga, preservando o histórico e as mensagens de chat.
     */
    void kickMember(Long ownerId, Long listId, Long membershipId);

    /**
     * Encerra o time (só o dono; 403 caso contrário) via exclusão lógica
     * (status CLOSED): some da busca e vira só leitura, mas membros continuam
     * vendo o histórico.
     */
    void deleteTeam(Long ownerId, Long listId);

    /**
     * Times em que o usuário é dono ou membro ativo aprovado, <b>paginados</b> e recortados
     * por {@link MyTeamsScope} (item P12).
     *
     * <p>Devolvia {@code List} com todos os status: a resposta crescia para sempre, porque o
     * limite do plano free é de times <b>ativos</b> — criar, deixar expirar e criar de novo
     * acumula histórico sem teto. O {@code totalElements} de cada página é o que a tela usa
     * para os contadores das abas e para o aviso do limite do plano.</p>
     */
    Page<ListSummaryResponse> listMyLists(Long userId, MyTeamsScope scope, Pageable pageable);

    /**
     * Detalhe público (sem autenticação) — usado pela busca e pela tela do time.
     *
     * @param viewerId quem está olhando, ou **nulo** quando anônimo. Só serve
     *                 para decidir se o `contact` do dono sai na resposta (dado
     *                 pessoal: apenas dono e membros aprovados). Não muda mais
     *                 nada do que é devolvido.
     */
    ListDetailResponse getList(Long listId, Long viewerId);

    /**
     * Busca pública (home): filtros opcionais por world, criatura-alvo, vaga
     * disponível e **vocação**. NÃO filtra por level — quem procura vê todos os
     * times; o requisito de level mínimo é só exibido e validado na entrada.
     *
     * @param vocation quando informada, devolve só times onde um personagem dessa
     *                 vocação **cabe agora**: vaga livre compatível (incluindo vaga
     *                 sem exigência) ou time sem composição que ainda tem vaga
     */
    Page<ListSummaryResponse> search(String world, Long creatureId, Boolean hasOpenSlots,
                                     Vocation vocation, Pageable pageable);

    /** Pedidos pendentes do time — só o dono enxerga. */
    List<MembershipResponse> listPendingRequests(Long ownerId, Long listId);

    /**
     * Substitui a **composição por vocação** do time (só o dono, só em time ATIVO).
     *
     * Lista vazia remove a composição. **Vaga ocupada não muda** — libere a vaga
     * antes. Recurso separado do `PATCH` de propósito: ver
     * {@link com.exivamoeres.dto.list.UpdateSlotsRequest}.
     */
    ListDetailResponse replaceSlots(Long ownerId, Long listId, UpdateSlotsRequest request);

    /**
     * Pedidos de entrada **do próprio usuário** — pendentes e recusados, mais
     * recentes primeiro. É o lado que faltava: `listMyLists` só devolve time de
     * dono ou de membro aprovado, então um pedido pendente não aparecia em lugar
     * nenhum e a pessoa não sabia se tinha sido ignorada ou recusada.
     */
    List<MyJoinRequestResponse> listMyJoinRequests(Long userId);

    /**
     * O solicitante desiste do próprio pedido (status `CANCELLED`).
     *
     * Só pedido **pendente** e só o dono do pedido; o histórico é preservado como
     * em todo o resto do projeto — nada é deletado.
     */
    void cancelMyJoinRequest(Long userId, Long membershipId);
}
