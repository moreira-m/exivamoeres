package com.exivamoeres.integration;

import com.exivamoeres.domain.Character;
import com.exivamoeres.domain.JoinPolicy;
import com.exivamoeres.domain.User;
import com.exivamoeres.domain.exception.BusinessRuleException;
import com.exivamoeres.dto.list.CreateListRequest;
import com.exivamoeres.service.HuntingListService;
import com.exivamoeres.service.ShareCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Não conseguir gerar código de convite é <b>falha do servidor</b>, não recusa de regra
 * (item T18).
 *
 * <p>O {@code generateUniqueShareCode} tenta cinco códigos aleatórios e, se todos
 * colidirem, desiste. Isso lançava {@code BusinessRuleException} — <b>422</b>, com a frase
 * "tente novamente". Duas coisas erradas:</p>
 *
 * <ul>
 *   <li>422 quer dizer <b>"corrija o payload"</b>, e não há nada no payload para corrigir:
 *       o usuário não escolhe o código de convite;</li>
 *   <li>como 4xx, a anomalia ficava <b>fora do alerta de taxa de 5xx</b> — o único que
 *       significa "o site está quebrado por um bug". Cinco colisões seguidas de um código
 *       aleatório é exatamente isso.</li>
 * </ul>
 *
 * <p>⚠️ O caminho é inalcançável com o gerador real (é o ponto dele), então o gerador é
 * substituído por um que devolve <b>sempre o mesmo código</b>. Sem isso não há teste
 * possível — e foi por não haver que o status errado durou.</p>
 */
class ShareCodeExhaustionIntegrationTest extends TeamIntegrationTestBase {

    @MockBean ShareCodeGenerator shareCodeGenerator;

    @Autowired HuntingListService listService;

    @Test
    void codigoDeConviteQueSempreColideNaoEhRecusaDeRegra() {
        when(shareCodeGenerator.generate()).thenReturn("COLIDE1");
        criarTime("share-1@teste.com", "Share Um", "ShareWorld");

        // O segundo time recebe o mesmo código cinco vezes seguidas.
        assertThatThrownBy(() -> criarTime("share-2@teste.com", "Share Dois", "ShareWorld"))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("5 tentativas");
    }

    private void criarTime(String email, String personagem, String world) {
        User dono = createUser(email);
        Character character = createCharacter(personagem, world, dono);
        stubPremium(personagem, world, 500, "Elder Druid");
        listService.createList(dono.getId(), new CreateListRequest(
                personagem + " Team", world, creature("Demon").getId(), JoinPolicy.AUTO_ACCEPT,
                character.getId(), null, null, null, null, null, null));
    }
}
