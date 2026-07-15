package com.exivamoeres.controller;

import com.exivamoeres.dto.creature.CreatureResponse;
import com.exivamoeres.service.CreatureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/creatures")
public class CreatureController {

    private final CreatureService creatureService;

    public CreatureController(CreatureService creatureService) {
        this.creatureService = creatureService;
    }

    @GetMapping
    public List<CreatureResponse> list() {
        return creatureService.listAll();
    }
}
