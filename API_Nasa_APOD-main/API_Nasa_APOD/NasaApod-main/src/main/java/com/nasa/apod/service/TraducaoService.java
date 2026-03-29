package com.nasa.apod.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class TraducaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraducaoService.class);
    private static final String TRADUCAO_API_URL = "https://api.mymemory.translated.net/get?q={texto}&langpair=en|pt-BR";
    private static final String GOOGLE_TRADUCAO_API_URL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=pt&dt=t&q={texto}";
    private static final String LIMITE_MYMEMORY = "USED ALL AVAILABLE FREE TRANSLATIONS";

    private final RestTemplate restTemplate;

    public TraducaoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Tradução pública: tenta traduzir e, em caso de erro, retorna o original
    public String traduzirParaPortugues(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return texto;
        }

        try {
            // MyMemory costuma limitar o tamanho; chama direto se pequeno
            if (texto.length() <= 500) {
                return traduzirTexto(texto);
            }

            StringBuilder resultado = new StringBuilder();
            int inicio = 0;
            final int tamanhoMaximo = 500;

            while (inicio < texto.length()) {
                int fim = Math.min(inicio + tamanhoMaximo, texto.length());
                String parte = texto.substring(inicio, fim);

                // Tenta evitar cortar no meio de uma frase: procura um ponto próximo ao fim
                if (fim < texto.length() && !parte.endsWith(".") && !parte.endsWith("!") && !parte.endsWith("?")) {
                    int ultimoPonto = parte.lastIndexOf('.');
                    if (ultimoPonto > (int) (tamanhoMaximo * 0.7)) {
                        parte = texto.substring(inicio, inicio + ultimoPonto + 1);
                        fim = inicio + ultimoPonto + 1;
                    }
                }

                String parteTraduzida = traduzirTexto(parte);
                resultado.append(parteTraduzida);

                inicio = fim;
                if (inicio < texto.length() && !parteTraduzida.endsWith(" ")) {
                    resultado.append(" ");
                }
            }

            return resultado.toString();
        } catch (Exception e) {
            LOGGER.error("Erro ao traduzir texto, retornando original", e);
            return texto;
        }
    }

    // Chamada à API MyMemory para textos curtos
    private String traduzirTexto(String texto) {
        String traducaoMyMemory = traduzirViaMyMemory(texto);
        if (traducaoMyMemory != null && !traducaoMyMemory.isBlank()) {
            return traducaoMyMemory;
        }

        String traducaoGoogle = traduzirViaGoogle(texto);
        if (traducaoGoogle != null && !traducaoGoogle.isBlank()) {
            return traducaoGoogle;
        }

        LOGGER.warn("Falha nas traduções externas, retornando texto original");
        return texto;
    }

    private String traduzirViaMyMemory(String texto) {
        try {
            RespostaTraducao resposta = restTemplate.getForObject(TRADUCAO_API_URL, RespostaTraducao.class, texto);
            if (resposta == null || resposta.getResponseData() == null || resposta.getResponseData().getTranslatedText() == null) {
                LOGGER.warn("Resposta MyMemory inválida");
                return null;
            }

            String traducao = resposta.getResponseData().getTranslatedText();
            if (traducao.toUpperCase().contains(LIMITE_MYMEMORY) || resposta.getResponseStatus() == 429) {
                LOGGER.warn("Limite diário da MyMemory atingido");
                return null;
            }

            return traducao;
        } catch (RestClientException e) {
            LOGGER.warn("Erro ao chamar MyMemory, tentando fallback", e);
            return null;
        }
    }

    private String traduzirViaGoogle(String texto) {
        try {
            JsonNode resposta = restTemplate.getForObject(GOOGLE_TRADUCAO_API_URL, JsonNode.class, texto);
            if (resposta == null || !resposta.isArray() || resposta.isEmpty()) {
                return null;
            }

            JsonNode blocosTraducao = resposta.get(0);
            if (blocosTraducao == null || !blocosTraducao.isArray()) {
                return null;
            }

            StringBuilder textoFinal = new StringBuilder();
            for (JsonNode bloco : blocosTraducao) {
                if (bloco.isArray() && bloco.size() > 0) {
                    String trecho = bloco.get(0).asText("");
                    textoFinal.append(trecho);
                }
            }

            String traducao = textoFinal.toString().trim();
            return traducao.isEmpty() ? null : traducao;
        } catch (RestClientException e) {
            LOGGER.warn("Erro ao chamar fallback de tradução", e);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @SuppressWarnings("unused")
    private static class RespostaTraducao {
        @JsonProperty("responseData")
        private DadosTraducao responseData;

        @JsonProperty("responseStatus")
        private Integer responseStatus;

        public DadosTraducao getResponseData() {
            return responseData;
        }

        public void setResponseData(DadosTraducao responseData) {
            this.responseData = responseData;
        }

        public Integer getResponseStatus() {
            return responseStatus;
        }

        public void setResponseStatus(Integer responseStatus) {
            this.responseStatus = responseStatus;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @SuppressWarnings("unused")
    private static class DadosTraducao {
        @JsonProperty("translatedText")
        private String translatedText;

        public String getTranslatedText() {
            return translatedText;
        }

        public void setTranslatedText(String translatedText) {
            this.translatedText = translatedText;
        }
    }
}

