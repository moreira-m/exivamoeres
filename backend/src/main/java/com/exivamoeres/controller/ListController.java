package com.exivamoeres.controller;

import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.ListSummaryResponse;
import com.exivamoeres.dto.list.MembershipResponse;
import com.exivamoeres.dto.list.MyJoinRequestResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.HuntingListService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lists")
public class ListController {

    private final HuntingListService listService;

    public ListController(HuntingListService listService) {
        this.listService = listService;
    }

    // ----- Público (sem login) -----

    /** Busca da home: filtros opcionais por world, criatura-alvo e vaga disponível. */
    @GetMapping("/search")
    public Page<ListSummaryResponse> search(@RequestParam(required = false) String world,
                                            @RequestParam(required = false) Long creatureId,
                                            @RequestParam(required = false) Boolean hasOpenSlots,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return listService.search(world, creatureId, hasOpenSlots, PageRequest.of(page, Math.min(size, 50)));
    }

    /**
     * Endpoint público, mas o usuário é lido quando existe: o `contact` do dono
     * só vai na resposta para o dono e para membros aprovados. Anônimo ⇒ o
     * principal é nulo, e o service trata como "não pode ver".
     */
    @GetMapping("/{id}")
    public ListDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long id) {
        return listService.getList(id, user != null ? user.id() : null);
    }

    // ----- Autenticado -----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListDetailResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody CreateListRequest request) {
        return listService.createList(user.id(), request);
    }

    @GetMapping("/mine")
    public List<ListSummaryResponse> myLists(@AuthenticationPrincipal AuthenticatedUser user) {
        return listService.listMyLists(user.id());
    }

    /**
     * Edita o time (só o dono; 403 caso contrário). O corpo traz **todos** os
     * campos editáveis — nulo/branco limpa o valor. World, criatura e política de
     * entrada não são editáveis e são ignorados se enviados.
     */
    @PatchMapping("/{id}")
    public ListDetailResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long id,
                                    @Valid @RequestBody UpdateListRequest request) {
        return listService.updateList(user.id(), id, request);
    }

    /**
     * Pedidos de entrada do próprio usuário (pendentes e recusados).
     *
     * ⚠️ O caminho tem **dois segmentos** (`mine/requests`), então não colide com
     * o `GET /api/lists/{id}` público — que casa um segmento só.
     */
    @GetMapping("/mine/requests")
    public List<MyJoinRequestResponse> myJoinRequests(@AuthenticationPrincipal AuthenticatedUser user) {
        return listService.listMyJoinRequests(user.id());
    }

    /** O solicitante desiste do próprio pedido (404 se o pedido não é dele). */
    @DeleteMapping("/mine/requests/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMyJoinRequest(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable Long membershipId) {
        listService.cancelMyJoinRequest(user.id(), membershipId);
    }

    @PostMapping("/{shareCode}/join")
    public ListDetailResponse join(@AuthenticationPrincipal AuthenticatedUser user,
                                   @PathVariable String shareCode,
                                   @Valid @RequestBody JoinListRequest request) {
        return listService.joinByShareCode(user.id(), shareCode, request);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        listService.leaveList(user.id(), id);
    }

    /** Reativa um time arquivado (só o dono, se tiver vaga no plano). */
    @PostMapping("/{id}/renew")
    public ListDetailResponse renew(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return listService.renewTeam(user.id(), id);
    }

    @GetMapping("/{id}/requests")
    public List<MembershipResponse> pendingRequests(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @PathVariable Long id) {
        return listService.listPendingRequests(user.id(), id);
    }

    @PostMapping("/{id}/requests/{membershipId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@AuthenticationPrincipal AuthenticatedUser user,
                        @PathVariable Long id, @PathVariable Long membershipId) {
        listService.approveJoinRequest(user.id(), id, membershipId);
    }

    @PostMapping("/{id}/requests/{membershipId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthenticatedUser user,
                       @PathVariable Long id, @PathVariable Long membershipId) {
        listService.rejectJoinRequest(user.id(), id, membershipId);
    }

    /** Expulsa um membro do time (só o dono; 403 caso contrário). */
    @DeleteMapping("/{id}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@AuthenticationPrincipal AuthenticatedUser user,
                     @PathVariable Long id, @PathVariable Long membershipId) {
        listService.kickMember(user.id(), id, membershipId);
    }

    /** Encerra o time (só o dono; 403 caso contrário). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        listService.deleteTeam(user.id(), id);
    }
}
