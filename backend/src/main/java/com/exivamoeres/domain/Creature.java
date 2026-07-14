package com.exivamoeres.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Criatura do Bestiary do Tibia. Cada criatura tem exatamente um Soul Core.
 * difficulty segue as estrelas do Bestiary (1 a 5).
 * O catálogo é dado de referência, populado por migration/seed — não há CRUD.
 */
@Entity
@Table(name = "creatures")
@Getter
@Setter
@NoArgsConstructor
public class Creature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    /** Estrelas do Bestiary: 1 (Harmless) a 5 (Challenging). */
    @Column(nullable = false)
    private int difficulty;
}
