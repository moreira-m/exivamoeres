package com.exivamoeres.security;

/**
 * Principal colocado no SecurityContext pelo JwtAuthenticationFilter.
 * Carrega só o necessário do token — quem precisar da entidade completa
 * busca no repositório pelo id.
 */
public record AuthenticatedUser(Long id, String displayName) {
}
