package com.exivamoeres.repository;

import com.exivamoeres.domain.World;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorldRepository extends JpaRepository<World, String> {

    /** A última lista conhecida, em ordem alfabética (é como a tela mostra). */
    List<World> findAllByOrderByNameAsc();
}
