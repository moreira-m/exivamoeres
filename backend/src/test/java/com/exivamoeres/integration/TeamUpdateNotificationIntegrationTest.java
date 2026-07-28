package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.MembershipStatus;
import com.exivamoeres.domain.NotificationType;
import com.exivamoeres.domain.User;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.dto.list.JoinListRequest;
import com.exivamoeres.dto.list.ListDetailResponse;
import com.exivamoeres.dto.list.UpdateListRequest;
import com.exivamoeres.dto.notification.NotificationResponse;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avisar os membros quando o time muda (item P16).
 *
 * A regra tem dois lados, e o segundo é o que impede a feature de virar spam:
 * **horário e level mínimo avisam; descrição, contato, título e preço não.** E
 * salvar o formulário sem mexer no campo não é mudança nenhuma.
 *
 * Cada teste usa um world próprio — a base é compartilhada entre as classes.
 */
class TeamUpdateNotificationIntegrationTest extends TeamIntegrationTestBase {

    @Autowired HuntingListService listService;
    @Autowired NotificationService notificationService;

    @Test
    void mudarOHorarioAvisaOMembroAprovado() {
        Ctx ctx = teamComMembro("SchedNotifyWorld", "Sched");

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, null, "Sáb 3h da manhã", null));

        assertThat(tipos(ctx.membroId)).contains(NotificationType.TEAM_SCHEDULE_CHANGED);
    }

    @Test
    void mudarOLevelMinimoAvisaOMembroAprovado() {
        Ctx ctx = teamComMembro("LevelNotifyWorld", "Level");

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 200, null, null, null, null));

        assertThat(tipos(ctx.membroId)).contains(NotificationType.TEAM_MINIMUM_LEVEL_CHANGED);
    }

    @Test
    void definirOHorarioPelaPrimeiraVezTambemAvisa() {
        // O time nasceu sem horário: nulo → valor é mudança do combinado.
        Ctx ctx = teamComMembro("FirstSchedWorld", "First");

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, null, "Dom 10h BRT", null));

        assertThat(tipos(ctx.membroId)).contains(NotificationType.TEAM_SCHEDULE_CHANGED);
    }

    @Test
    void apagarOHorarioAvisa() {
        Ctx ctx = teamComMembro("ClearSchedWorld", "ClearS", "Seg 20h BRT");

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, null, "  ", null));

        // Horário que desapareceu também muda o plano de quem contava com ele.
        assertThat(tipos(ctx.membroId)).contains(NotificationType.TEAM_SCHEDULE_CHANGED);
    }

    @Test
    void mudarSoADescricaoNaoAvisaNinguem() {
        Ctx ctx = teamComMembro("DescNotifyWorld", "Desc", "Seg 20h BRT");
        long antes = notificationService.countUnread(ctx.membroId);

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest("Outro título", null, 999L,
                        "Descrição reescrita", "Seg 20h BRT", "discord: novo#0001"));

        // Título, preço, descrição e contato mudaram — e ninguém foi incomodado.
        assertThat(notificationService.countUnread(ctx.membroId)).isEqualTo(antes);
    }

    @Test
    void salvarOMesmoHorarioNaoAvisa() {
        Ctx ctx = teamComMembro("SameValueWorld", "Same", "Ter 21h BRT");
        long antes = notificationService.countUnread(ctx.membroId);

        // O caso mais comum de todos: o dono abriu o formulário para mexer em
        // outra coisa e salvou o horário igual.
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, "só a descrição", "Ter 21h BRT", null));

        assertThat(notificationService.countUnread(ctx.membroId)).isEqualTo(antes);
    }

    @Test
    void oDonoNaoRecebeAvisoDaPropriaEdicao() {
        Ctx ctx = teamComMembro("OwnerNotifyWorld", "Owner");
        long antes = notificationService.countUnread(ctx.ownerId);

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 300, null, null, "Qua 22h BRT", null));

        assertThat(notificationService.countUnread(ctx.ownerId)).isEqualTo(antes);
    }

    @Test
    void pedidoPendenteNaoEhAvisado() {
        String world = "PendingNotifyWorld";
        // MANUAL_APPROVAL: o solicitante fica PENDING e ainda não é membro.
        Ctx ctx = teamComMembro(world, "Pend", null, JoinPolicy.MANUAL_APPROVAL);
        long antes = notificationService.countUnread(ctx.membroId);

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, null, null, null, "Sex 19h BRT", null));

        // Quem ainda não está no time não tem plano para reorganizar.
        assertThat(notificationService.countUnread(ctx.membroId)).isEqualTo(antes);
        assertThat(tipos(ctx.membroId)).doesNotContain(NotificationType.TEAM_SCHEDULE_CHANGED);
    }

    @Test
    void mudarHorarioELevelJuntosGeraUmAvisoDeCada() {
        Ctx ctx = teamComMembro("BothNotifyWorld", "Both", "Seg 20h BRT");

        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest(null, 250, null, null, "Qui 23h BRT", null));

        assertThat(tipos(ctx.membroId))
                .contains(NotificationType.TEAM_SCHEDULE_CHANGED,
                        NotificationType.TEAM_MINIMUM_LEVEL_CHANGED);
    }

    @Test
    void aNotificacaoGuardaONomeDoTimeParaOTexto() {
        Ctx ctx = teamComMembro("NameNotifyWorld", "NameN");

        // Manda o título junto, como a tela faz: o payload do PATCH é o
        // formulário inteiro, e omitir o nome faria o time voltar a se chamar
        // como a criatura-alvo (contrato do P14).
        listService.updateList(ctx.ownerId, ctx.listId,
                new UpdateListRequest("NameN Team", null, null, null, "Seg 20h BRT", null));

        // O frontend monta o texto com o nome; sem ele a notificação fica sem sujeito.
        assertThat(notificacoes(ctx.membroId))
                .filteredOn(n -> n.type() == NotificationType.TEAM_SCHEDULE_CHANGED)
                .allSatisfy(n -> {
                    assertThat(n.listName()).isEqualTo("NameN Team");
                    assertThat(n.listId()).isEqualTo(ctx.listId);
                });
    }

    // ----- Helpers -----

    private record Ctx(Long listId, Long ownerId, Long membroId) {
    }

    private Ctx teamComMembro(String world, String prefix) {
        return teamComMembro(world, prefix, null, JoinPolicy.AUTO_ACCEPT);
    }

    private Ctx teamComMembro(String world, String prefix, String horario) {
        return teamComMembro(world, prefix, horario, JoinPolicy.AUTO_ACCEPT);
    }

    private Ctx teamComMembro(String world, String prefix, String horario, JoinPolicy policy) {
        User owner = createUser(prefix.toLowerCase() + "-notify-dono@teste.com");
        Character ownerChar = createCharacter(prefix + " Dono", world, owner);
        stubPremium(prefix + " Dono", world);
        ListDetailResponse detail = listService.createList(owner.getId(), new CreateListRequest(
                prefix + " Team", world, creature("Demon").getId(), policy, ownerChar.getId(),
                null, null, null, horario, null, null));

        User membro = createUser(prefix.toLowerCase() + "-notify-membro@teste.com");
        Character membroChar = createCharacter(prefix + " Membro", world, membro);
        stubPremium(prefix + " Membro", world);
        listService.joinByShareCode(membro.getId(), detail.summary().shareCode(),
                new JoinListRequest(membroChar.getId()));

        return new Ctx(detail.summary().id(), owner.getId(), membro.getId());
    }

    private List<NotificationResponse> notificacoes(Long userId) {
        return notificationService.list(userId, PageRequest.of(0, 20)).getContent();
    }

    private List<NotificationType> tipos(Long userId) {
        return notificacoes(userId).stream().map(NotificationResponse::type).toList();
    }
}
