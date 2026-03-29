package com.nasa.apod.controller;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.apod.model.Apod;
import com.nasa.apod.model.BuscaAstronomicaItem;
import com.nasa.apod.service.TraducaoCacheService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/apod")
public class NasaController {

    // Logger para registrar mensagens de log (info, warn, error)
    private static final Logger LOGGER = LoggerFactory.getLogger(NasaController.class);

        private static final Map<String, String> TERMO_PT_PARA_EN = Map.ofEntries(
            Map.entry("estrela", "star"),
            Map.entry("estrelas", "stars"),
            Map.entry("planeta", "planet"),
            Map.entry("planetas", "planets"),
            Map.entry("meteoro", "meteor"),
            Map.entry("meteoros", "meteors"),
            Map.entry("meteorito", "meteorite"),
            Map.entry("meteoritos", "meteorites"),
            Map.entry("cometa", "comet"),
            Map.entry("cometas", "comets"),
            Map.entry("galaxia", "galaxy"),
            Map.entry("galaxias", "galaxies"),
            Map.entry("nebulosa", "nebula"),
            Map.entry("nebulosas", "nebula"),
            Map.entry("lua", "moon"),
            Map.entry("sol", "sun"),
            Map.entry("buraco negro", "black hole"),
            Map.entry("buracos negros", "black holes"),
            Map.entry("universo", "universe"),
            Map.entry("espaco", "space"),
            Map.entry("espaço", "space"));

    // RestTemplate é usado para fazer chamadas HTTP para APIs externas (NASA, serviço de tradução)
    private final RestTemplate restTemplate;

    // URL montada da API APOD da NASA (baseUrl + ?api_key=APIKEY)
    private final String apodUrl;

    // URL base da API de busca do acervo multimídia da NASA
    private final String nasaSearchUrl;

    // Serviço responsável por traduzir textos para português (com cache)
    private final TraducaoCacheService traducaoCacheService;

    /**
     * Construtor do controller. Dependências são injetadas pelo Spring.
     * @param restTemplate bean para chamadas HTTP
     * @param baseUrl url base da API da NASA (injetada de application.properties)
     * @param apiKey chave da API da NASA (injetada de application.properties)
     * @param traducaoCacheService serviço que traduz textos com cache (inglês -> pt-BR)
     */
    public NasaController(RestTemplate restTemplate,
                          @Value("${nasa.api.url}") String baseUrl,
                          @Value("${nasa.api.key}") String apiKey,
                          @Value("${nasa.images.api.url}") String nasaSearchUrl,
                          TraducaoCacheService traducaoCacheService) {
        this.restTemplate = restTemplate;
        // Monta a URL completa com a chave de API
        this.apodUrl = String.format("%s?api_key=%s", baseUrl, apiKey);
        this.nasaSearchUrl = nasaSearchUrl;
        this.traducaoCacheService = traducaoCacheService;
    }

    @GetMapping
    @Operation(summary = "Busca APOD (Astronomy Picture of the Day) da NASA")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "APOD retornado com sucesso", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Apod.class))),
        @ApiResponse(responseCode = "502", description = "Resposta vazia da API da NASA"),
        @ApiResponse(responseCode = "503", description = "Serviço indisponível")
    })
    public ResponseEntity<?> buscarApod(@RequestParam(value = "date", required = false) String date) {
        try {
            String urlConsulta = apodUrl;
            if (date != null && !date.isBlank()) {
                urlConsulta = apodUrl + "&date=" + date.trim();
            }

            // Faz a chamada GET para a API da NASA e desserializa o JSON em Apod.class
            Apod resposta = restTemplate.getForObject(urlConsulta, Apod.class);
            if (resposta == null) {
                // Se a API retornar vazio (null), logamos e retornamos 502 ao cliente
                LOGGER.warn("Resposta vazia da API APOD");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Não foi possível obter os dados da NASA neste momento.");
            }
            
            // Se existir título, traduz para português
            if (resposta.getTitle() != null) {
                resposta.setTitle(traducaoCacheService.traduzirParaPortugues(resposta.getTitle()));
            }
            // Se existir explicação, traduz para português
            if (resposta.getExplanation() != null) {
                resposta.setExplanation(traducaoCacheService.traduzirParaPortugues(resposta.getExplanation()));
            }
            
            // Retorna 200 OK com o objeto Apod (serializado para JSON automaticamente)
            return ResponseEntity.ok(resposta);
        } catch (RestClientResponseException e) {
            // Exceção quando a API externa respondeu com um status HTTP (ex.: 401, 429, 500)
            int statusCode = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
            // Log detalhado com corpo da resposta para depuração
            LOGGER.error("Erro ao chamar a API APOD: status={}, corpo={}", statusCode, e.getResponseBodyAsString(), e);
            // Repasse do status recebido da API externa para o cliente
            return ResponseEntity.status(statusCode)
                    .body("Erro ao acessar a API da NASA: " + e.getStatusText());
        } catch (RestClientException e) {
            // Erros de cliente HTTP (ex.: timeout, problemas de rede)
            LOGGER.error("Erro inesperado ao chamar a API APOD", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Serviço indisponível no momento. Tente novamente mais tarde.");
        }
    }

    @GetMapping("/image")
    @Operation(summary = "Retorna a mídia (imagem) do APOD como binário")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Imagem retornada com sucesso",
                content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "204", description = "APOD atual é um vídeo ou não há imagem para retornar"),
        @ApiResponse(responseCode = "502", description = "Resposta vazia da API da NASA"),
        @ApiResponse(responseCode = "503", description = "Serviço indisponível")
    })
    public ResponseEntity<?> buscarApodImage() {
        try {
            Apod resposta = restTemplate.getForObject(apodUrl, Apod.class);
            if (resposta == null || resposta.getUrl() == null) {
                LOGGER.warn("Resposta vazia da API APOD ou sem URL de mídia");
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Não foi possível obter a URL da mídia da NASA neste momento.");
            }

            String mediaUrl = resposta.getUrl();

            // Busca o conteúdo bruto da mídia (imagem/video) e obtém headers
            ResponseEntity<byte[]> mediaResp = restTemplate.exchange(mediaUrl, HttpMethod.GET, null, byte[].class);
            if (!mediaResp.getStatusCode().is2xxSuccessful() || mediaResp.getBody() == null) {
                LOGGER.warn("Falha ao baixar mídia: status={}", mediaResp.getStatusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Não foi possível baixar a mídia da URL fornecida pela NASA.");
            }

            MediaType contentType = mediaResp.getHeaders().getContentType();
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM;
            }

            // Se for vídeo ou HTML, não retornamos como imagem binária (Swagger mostrará download)
            if (contentType.getType().equalsIgnoreCase("video") || MediaType.TEXT_HTML.includes(contentType)) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("APOD atual não é uma imagem.");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            // Opcional: instruir browser a baixar com nome
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=apod-media");

            return new ResponseEntity<>(mediaResp.getBody(), headers, HttpStatus.OK);
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
            LOGGER.error("Erro ao chamar a API APOD para mídia: status={}, corpo={}", statusCode, e.getResponseBodyAsString(), e);
            return ResponseEntity.status(statusCode)
                    .body("Erro ao acessar a API da NASA: " + e.getStatusText());
        } catch (RestClientException e) {
            LOGGER.error("Erro inesperado ao obter mídia APOD", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Serviço indisponível no momento. Tente novamente mais tarde.");
        }
    }

    @GetMapping("/search")
    @Operation(summary = "Busca planetas, estrelas e outros conteúdos no acervo da NASA")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultados de busca retornados com sucesso",
                content = @Content(mediaType = "application/json",
                        schema = @Schema(implementation = BuscaAstronomicaItem.class))),
        @ApiResponse(responseCode = "400", description = "Parâmetro de busca inválido"),
        @ApiResponse(responseCode = "503", description = "Serviço indisponível")
    })
    public ResponseEntity<?> buscarConteudoAstronomico(
            @RequestParam("query") String query,
            @RequestParam(value = "limit", defaultValue = "8") Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Informe o parâmetro query para pesquisar.");
        }

        int limiteSeguro = Math.max(1, Math.min(limit, 20));

        try {
            List<BuscaAstronomicaItem> resultados = new ArrayList<>();
            Set<String> chavesUnicas = new LinkedHashSet<>();
            List<String> consultas = gerarConsultasBusca(query.trim());

            for (String consulta : consultas) {
                JsonNode items = buscarItemsNaNasa(consulta);
                if (items == null || !items.isArray()) {
                    continue;
                }

                adicionarResultados(items, resultados, chavesUnicas, limiteSeguro);
                if (resultados.size() >= limiteSeguro) {
                    break;
                }
            }

            if (resultados.isEmpty()) {
                LOGGER.info("Busca sem resultados para query={} (consultas testadas={})", query, consultas);
            }

            return ResponseEntity.ok(resultados);
        } catch (RestClientResponseException e) {
            int statusCode = e.getStatusCode() != null ? e.getStatusCode().value() : 500;
            LOGGER.error("Erro ao chamar a busca da NASA: status={}, corpo={}", statusCode, e.getResponseBodyAsString(), e);
            return ResponseEntity.status(statusCode)
                    .body("Erro ao acessar a busca da NASA: " + e.getStatusText());
        } catch (RestClientException e) {
            LOGGER.error("Erro inesperado ao buscar conteúdos astronômicos", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Serviço indisponível no momento. Tente novamente mais tarde.");
        }
    }

    private JsonNode buscarItemsNaNasa(String query) {
        JsonNode resposta = restTemplate.getForObject(
                nasaSearchUrl + "?media_type=image,video&q={query}",
                JsonNode.class,
                query);

        if (resposta == null || resposta.path("collection").isMissingNode()) {
            return null;
        }

        return resposta.path("collection").path("items");
    }

    private void adicionarResultados(JsonNode items,
                                     List<BuscaAstronomicaItem> resultados,
                                     Set<String> chavesUnicas,
                                     int limiteSeguro) {
        for (JsonNode item : items) {
            if (resultados.size() >= limiteSeguro) {
                break;
            }

            JsonNode data = item.path("data");
            if (!data.isArray() || data.isEmpty()) {
                continue;
            }

            JsonNode info = data.get(0);
            String title = textoOuNulo(info, "title");
            String description = textoOuNulo(info, "description");
            String mediaType = textoOuNulo(info, "media_type");
            String nasaId = textoOuNulo(info, "nasa_id");
            String dateCreated = textoOuNulo(info, "date_created");

            JsonNode links = item.path("links");
            String mediaUrl = null;
            if (links.isArray() && !links.isEmpty()) {
                mediaUrl = textoOuNulo(links.get(0), "href");
            }

            if (mediaUrl == null || mediaUrl.isBlank()) {
                continue;
            }

            String chaveUnica = nasaId != null ? nasaId : mediaUrl;
            if (!chavesUnicas.add(chaveUnica)) {
                continue;
            }

            if (title != null) {
                title = traducaoCacheService.traduzirParaPortugues(title);
            }

            if (description != null) {
                description = traducaoCacheService.traduzirParaPortugues(description);
            }

            resultados.add(new BuscaAstronomicaItem(
                    title,
                    description,
                    mediaType,
                    mediaUrl,
                    nasaId,
                    dateCreated));
        }
    }

    private List<String> gerarConsultasBusca(String queryOriginal) {
        LinkedHashSet<String> consultas = new LinkedHashSet<>();
        consultas.add(queryOriginal);

        String queryNormalizada = normalizarTexto(queryOriginal);
        consultas.add(queryNormalizada);

        String queryTraduzida = traduzirTermosPtParaEn(queryNormalizada);
        consultas.add(queryTraduzida);

        for (String token : queryNormalizada.split("\\s+")) {
            if (token.length() < 3 || isStopWord(token)) {
                continue;
            }
            consultas.add(token);
            consultas.add(traduzirTermosPtParaEn(token));
        }

        return consultas.stream()
                .filter(q -> q != null && !q.isBlank())
                .toList();
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }

        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return semAcento.toLowerCase(Locale.ROOT).trim();
    }

    private String traduzirTermosPtParaEn(String texto) {
        String resultado = texto;
        for (Map.Entry<String, String> entry : TERMO_PT_PARA_EN.entrySet()) {
            resultado = resultado.replace(entry.getKey(), entry.getValue());
        }
        return resultado;
    }

    private boolean isStopWord(String token) {
        return "de".equals(token)
                || "da".equals(token)
                || "do".equals(token)
                || "das".equals(token)
                || "dos".equals(token)
                || "e".equals(token)
                || "o".equals(token)
                || "a".equals(token)
                || "os".equals(token)
                || "as".equals(token);
    }

    private String textoOuNulo(JsonNode node, String campo) {
        JsonNode valor = node.path(campo);
        if (valor.isMissingNode() || valor.isNull()) {
            return null;
        }
        String texto = valor.asText();
        return texto == null || texto.isBlank() ? null : texto;
    }
}

