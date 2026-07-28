package com.exivamoeres.dto.list;

import com.exivamoeres.domain.Vocation;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * A composição do time, na ordem das vagas. `null` numa posição = vaga livre.
 *
 * <p>Substitui a composição inteira (é um `PUT`): lista **vazia remove** a
 * composição, e o time volta a aceitar qualquer vocação.</p>
 *
 * <p><b>Por que não entrou no `PATCH` do time.</b> O contrato do
 * {@link UpdateListRequest} é "manda tudo, campo nulo limpa" — e limpar composição
 * pode ser **recusado** (vaga ocupada não muda). Um cliente que omitisse `slots`
 * numa edição de descrição passaria a receber 422 por causa de uma vaga ocupada, o
 * que não faz sentido nenhum. Recurso separado, regra separada.</p>
 */
public record UpdateSlotsRequest(
        @NotNull
        List<Vocation> slots
) {
}
