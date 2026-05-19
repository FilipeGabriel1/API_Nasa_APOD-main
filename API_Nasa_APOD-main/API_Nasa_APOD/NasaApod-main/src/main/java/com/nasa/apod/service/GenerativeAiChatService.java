package com.nasa.apod.service;

public interface GenerativeAiChatService {

    /**
     * Gera uma resposta para a mensagem do usuário.
     *
     * @param userMessage Pergunta/mensagem do usuário
     * @param context     Contexto opcional (ex.: descrição do APOD atual)
     */
    String generateReply(String userMessage, String context);
}
