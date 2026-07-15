package com.exivamoeres.controller;

import com.exivamoeres.dto.character.CharacterSummaryResponse;
import com.exivamoeres.dto.soulcore.CharacterSoulcoreResponse;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.CharacterService;
import com.exivamoeres.service.SoulcoreService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;
    private final SoulcoreService soulcoreService;

    public CharacterController(CharacterService characterService, SoulcoreService soulcoreService) {
        this.characterService = characterService;
        this.soulcoreService = soulcoreService;
    }

    /** Personagens verificados do usuário (usados para entrar em times, chat, etc). */
    @GetMapping("/mine")
    public List<CharacterSummaryResponse> myCharacters(@AuthenticationPrincipal AuthenticatedUser user) {
        return characterService.listMyCharacters(user.id());
    }

    /** Cores já desbloqueados por um personagem (perfil público). */
    @GetMapping("/{characterId}/soulcores")
    public List<CharacterSoulcoreResponse> soulcores(@PathVariable Long characterId) {
        return soulcoreService.listCharacterSoulcores(characterId);
    }
}
