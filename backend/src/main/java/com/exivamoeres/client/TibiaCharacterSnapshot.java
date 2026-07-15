package com.exivamoeres.client;

/**
 * Visão normalizada de um personagem retornado pela TibiaData API.
 * found = false quando o personagem não existe no Tibia.com.
 */
public record TibiaCharacterSnapshot(
        boolean found,
        String name,
        String world,
        String comment,
        String accountStatus,
        String vocation
) {
    public static TibiaCharacterSnapshot notFound() {
        return new TibiaCharacterSnapshot(false, null, null, null, null, null);
    }

    /** "Free Account" nunca pode participar de times (regra de negócio). */
    public boolean isPremium() {
        return accountStatus != null && accountStatus.toLowerCase().contains("premium");
    }
}
