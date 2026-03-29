package com.nasa.apod.service;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serviço que envolve a TraducaoService com um cache em memória.
 * Evita fazer múltiplas requisições HTTP para traduzir o mesmo texto.
 */
@Service
public class TraducaoCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraducaoCacheService.class);

    private final TraducaoService traducaoService;
    
    // Cache: chave = texto original, valor = texto traduzido
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public TraducaoCacheService(TraducaoService traducaoService) {
        this.traducaoService = traducaoService;
    }

    /**
     * Traduz um texto para português, utilizando cache para evitar requisições repetidas.
     * @param texto texto a traduzir
     * @return texto traduzido (ou original se falhar)
     */
    public String traduzirParaPortugues(String texto) {
        // Se texto é vazio ou nulo, retorna como está
        if (texto == null || texto.trim().isEmpty()) {
            return texto;
        }

        // Verifica se já existe no cache
        if (cache.containsKey(texto)) {
            LOGGER.debug("Tradução encontrada no cache para: {}", texto.substring(0, Math.min(50, texto.length())));
            return cache.get(texto);
        }

        // Não estava no cache, chama o serviço
        LOGGER.debug("Traduzindo (não estava em cache): {}", texto.substring(0, Math.min(50, texto.length())));
        String traducao = traducaoService.traduzirParaPortugues(texto);

        // Evita cachear falhas temporárias: se tradução vier igual ao original,
        // deixa sem cache para tentar novamente em próximas requisições.
        if (traducao != null && !traducao.equals(texto)) {
            cache.put(texto, traducao);
        }

        return traducao;
    }

    /**
     * Retorna o tamanho atual do cache (útil para monitoramento)
     */
    public int getTamanhoCache() {
        return cache.size();
    }

    /**
     * Limpa o cache (útil se precisar liberar memória)
     */
    public void limparCache() {
        LOGGER.info("Cache de tradução foi limpo. Tamanho anterior: {}", cache.size());
        cache.clear();
    }
}
