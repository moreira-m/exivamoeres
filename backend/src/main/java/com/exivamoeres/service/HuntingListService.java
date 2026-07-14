package com.exivamoeres.service;

/**
 * ESQUELETO PARA A SESSÃO 2 — nenhum método implementado ainda.
 * Contratos definidos agora para a próxima sessão seguir o mesmo desenho
 * dos services existentes (interface + impl, DTOs próprios, @Transactional
 * na implementação).
 */
public interface HuntingListService {

    /**
     * Cria uma lista vinculada a um world, com share_code único gerado no
     * mesmo estilo do VerificationCodeGenerator.
     * TODO(sessão 2): implementar + DTOs (CreateListRequest/ListResponse).
     */
    Object createList(Long ownerId, String name, String world);

    /**
     * Entra numa lista pelo share_code usando um personagem do usuário.
     * Regras: personagem deve pertencer ao usuário (claim aprovado) e ser do
     * mesmo world da lista; reativar membership inativa em vez de duplicar.
     * TODO(sessão 2): implementar.
     */
    Object joinByShareCode(Long userId, String shareCode, Long characterId);

    /** TODO(sessão 2): sair da lista = active=false, nunca deletar histórico. */
    void leaveList(Long userId, Long listId);

    /** TODO(sessão 2): listar listas ativas do usuário. */
    Object listMyLists(Long userId);

    /** TODO(sessão 2): detalhe da lista com membros e soulcores. */
    Object getList(Long userId, Long listId);
}
