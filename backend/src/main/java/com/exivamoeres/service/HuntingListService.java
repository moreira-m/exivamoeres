package com.exivamoeres.service;

import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MembershipResponse;
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

    /** Times em que o usuário é dono ou membro ativo aprovado. */
    List<ListSummaryResponse> listMyLists(Long userId);

    /** Detalhe público (sem autenticação) — usado pela busca e pela tela do time. */
    ListDetailResponse getList(Long listId);

    /** Busca pública (home): filtros opcionais por world, criatura-alvo e vaga disponível. */
    Page<ListSummaryResponse> search(String world, Long creatureId, Boolean hasOpenSlots, Pageable pageable);

    /** Pedidos pendentes do time — só o dono enxerga. */
    List<MembershipResponse> listPendingRequests(Long ownerId, Long listId);
}
