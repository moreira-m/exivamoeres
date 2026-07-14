package com.exivamoeres.domain;

/**
 * Origem da conta do usuário. ANONYMOUS permite usar o site sem cadastro;
 * a conta anônima pode ser promovida a LOCAL/OAuth depois, mantendo o mesmo id.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    DISCORD,
    ANONYMOUS
}
