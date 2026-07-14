package com.exivamoeres.client;

/**
 * Visão normalizada de um personagem retornado pela TibiaData API.
 * found = false quando o personagem não existe no Tibia.com.
 */
public record TibiaCharacterSnapshot(
        boolean found,
        String name,
        String world,
        String comment
) {
    public static TibiaCharacterSnapshot notFound() {
        return new TibiaCharacterSnapshot(false, null, null, null);
    }
}
