package com.exivamoeres.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Um mundo do Tibia, como a TibiaData o reporta — <b>espelho</b>, não fonte.
 *
 * <p>Existe para a lista de mundos ter um <b>chão</b>: sem ela, uma indisponibilidade da
 * API externa no momento em que o cache está frio (todo restart) deixava o filtro da home
 * vazio. Ver o item S13.</p>
 *
 * <p>O nome é a chave porque a lista é um conjunto de nomes: não há outro atributo, e o
 * mundo não tem ciclo de vida próprio no nosso domínio — ele aparece e desaparece conforme
 * a TibiaData.</p>
 */
@Entity
@Table(name = "worlds")
@Getter
@Setter
@NoArgsConstructor
public class World {

    @Id
    @Column(nullable = false, length = 40)
    private String name;

    /** Quando esta lista foi confirmada pela TibiaData — para log e depuração. */
    @Column(nullable = false)
    private Instant refreshedAt = Instant.now();

    public World(String name) {
        this.name = name;
    }
}
