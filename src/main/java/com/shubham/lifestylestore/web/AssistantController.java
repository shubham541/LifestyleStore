package com.shubham.lifestylestore.web;

import com.shubham.lifestylestore.service.AssistantService;
import com.shubham.lifestylestore.service.AssistantService.Reply;
import com.shubham.lifestylestore.service.AssistantService.Turn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Assistant")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    public record TurnDto(@NotBlank @Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 600) String content) {
    }

    /** Whole conversation (client-held, capped at 12 turns) plus an optional product the customer is viewing. */
    public record ChatRequest(@NotEmpty @Size(max = 12) List<@Valid TurnDto> messages,
                              @Size(max = 40) String productId) {
    }

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/chat")
    public Reply chat(@Valid @RequestBody ChatRequest r) {
        return assistant.chat(r.messages().stream().map(t -> new Turn(t.role(), t.content())).toList(),
                r.productId());
    }
}
