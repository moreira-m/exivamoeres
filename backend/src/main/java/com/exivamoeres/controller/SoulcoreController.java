package com.exivamoeres.controller;

import com.exivamoeres.dto.soulcore.ListSoulcoreResponse;
import com.exivamoeres.dto.soulcore.SoulcoreActionRequest;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.SoulcoreService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lists/{listId}/soulcores")
public class SoulcoreController {

    private final SoulcoreService soulcoreService;

    public SoulcoreController(SoulcoreService soulcoreService) {
        this.soulcoreService = soulcoreService;
    }

    /** Board do time: estado de todos os cores rastreados (membros veem via detalhe). */
    @GetMapping
    public List<ListSoulcoreResponse> board(@PathVariable Long listId) {
        return soulcoreService.listBoard(listId);
    }

    @PostMapping("/{creatureId}/obtain")
    public ListSoulcoreResponse obtain(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long listId,
                                       @PathVariable Long creatureId,
                                       @Valid @RequestBody SoulcoreActionRequest request) {
        return soulcoreService.markObtained(user.id(), listId, creatureId, request.characterId());
    }

    @PostMapping("/{creatureId}/unlock")
    public ListSoulcoreResponse unlock(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long listId,
                                       @PathVariable Long creatureId,
                                       @Valid @RequestBody SoulcoreActionRequest request) {
        return soulcoreService.markUnlocked(user.id(), listId, creatureId, request.characterId());
    }
}
