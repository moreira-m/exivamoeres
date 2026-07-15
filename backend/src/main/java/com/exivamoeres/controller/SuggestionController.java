package com.exivamoeres.controller;

import com.exivamoeres.dto.suggestion.SuggestionResponse;
import com.exivamoeres.security.AuthenticatedUser;
import com.exivamoeres.service.SuggestionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping("/lists/{listId}/suggestions")
    public List<SuggestionResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable Long listId) {
        return suggestionService.listSuggestions(user.id(), listId);
    }

    @PostMapping("/suggestions/{suggestionId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal AuthenticatedUser user,
                        @PathVariable Long suggestionId) {
        suggestionService.dismissSuggestion(user.id(), suggestionId);
    }
}
