package com.exivamoeres.client;

/** Dados de uma criatura resolvidos via TibiaData, usados para cachear o ícone localmente. */
public record TibiaCreatureSnapshot(boolean found, String name, String imageUrl) {

    public static TibiaCreatureSnapshot notFound() {
        return new TibiaCreatureSnapshot(false, null, null);
    }
}
