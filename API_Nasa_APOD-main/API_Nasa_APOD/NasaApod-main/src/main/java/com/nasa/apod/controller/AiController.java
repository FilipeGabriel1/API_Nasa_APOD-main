package com.nasa.apod.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nasa.apod.service.GenerativeAiChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiController.class);

    private final GenerativeAiChatService generativeAiChatService;

    public AiController(GenerativeAiChatService generativeAiChatService) {
        this.generativeAiChatService = generativeAiChatService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Chat com IA generativa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta gerada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatResponse.class))),
            @ApiResponse(responseCode = "400", description = "Entrada inválida"),
            @ApiResponse(responseCode = "503", description = "IA não configurada"),
            @ApiResponse(responseCode = "502", description = "Falha ao obter resposta do provedor")
    })
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body("Informe o campo 'message'.");
        }

        String message = request.message().trim();
        if (message.length() > 4_000) {
            return ResponseEntity.badRequest().body("A mensagem é muito longa.");
        }

        try {
            String reply = generativeAiChatService.generateReply(message, request.context());
            return ResponseEntity.ok(new ChatResponse(reply));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(e.getMessage() != null ? e.getMessage() : "IA não configurada.");
        } catch (Exception e) {
            LOGGER.error("Erro ao gerar resposta da IA", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Não foi possível obter resposta da IA neste momento.");
        }
    }

    public record ChatRequest(String message, String context) {
    }

    public record ChatResponse(String reply) {
    }
}
