package com.nasa.apod.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // pt
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "da", "do", "das", "dos",
            "em", "no", "na", "nos", "nas", "para", "por", "com", "sem", "sobre", "entre",
            "e", "ou", "mas", "que", "como", "quando", "onde", "qual", "quais", "porque", "porquê",
            "é", "ser", "estar", "sao", "são", "foi", "era", "tem", "têm", "ter", "isso", "isto",
            "aquele", "aquela", "aquilo", "estes", "essas", "esse", "essa", "tambem", "também",
            // en
            "the", "a", "an", "of", "to", "in", "on", "and", "or", "for", "with", "without",
            "what", "which", "how", "when", "where", "why", "is", "are", "was", "were", "be",
            "it", "this", "that", "these", "those"));

    private final List<Chunk> chunks;

    public KnowledgeBaseService(
            @Value("${ai.chat.knowledge:${AI_CHAT_KNOWLEDGE:}}") String knowledgeInline,
            @Value("${ai.chat.knowledgeFile:classpath:ai/knowledge.txt}") Resource knowledgeFile,
            @Value("${ai.chat.knowledgeDirPattern:classpath*:ai/knowledge/*.txt}") String dirPattern,
            @Value("${ai.chat.knowledgeChunkMaxChars:900}") int chunkMaxChars) {
        this.chunks = loadChunks(knowledgeInline, knowledgeFile, dirPattern, Math.max(200, chunkMaxChars));
        LOGGER.info("KnowledgeBase carregada: {} chunk(s)", this.chunks.size());
    }

    public String retrieveRelevantContext(String query, int topK, int maxChars) {
        if (query == null || query.isBlank() || chunks.isEmpty()) {
            return "";
        }

        List<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return "";
        }

        int safeTopK = Math.max(1, Math.min(topK, 8));
        int safeMaxChars = Math.max(200, Math.min(maxChars, 10_000));

        List<ScoredChunk> scored = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            double score = score(tokens, chunk);
            if (score > 0) {
                scored.add(new ScoredChunk(chunk, score));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (scored.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        int count = 0;
        for (ScoredChunk sc : scored) {
            if (count >= safeTopK) {
                break;
            }

            String prefix = "[Fonte: " + sc.chunk().id() + "]\n";
            String block = prefix + sc.chunk().text().trim() + "\n\n";
            if (out.length() + block.length() > safeMaxChars) {
                break;
            }

            out.append(block);
            count++;
        }

        return out.toString().trim();
    }

    private static double score(List<String> queryTokens, Chunk chunk) {
        double sum = 0;
        for (String t : queryTokens) {
            Integer c = chunk.termCounts().get(t);
            if (c != null) {
                sum += c;
            }
        }

        if (sum <= 0) {
            return 0;
        }
        return sum / Math.sqrt(Math.max(1, chunk.termCountTotal()));
    }

    private static List<Chunk> loadChunks(String inline, Resource knowledgeFile, String dirPattern, int chunkMaxChars) {
        List<Chunk> all = new ArrayList<>();

        String inlineText = inline != null ? inline.trim() : "";
        if (!inlineText.isBlank()) {
            all.addAll(chunkText("inline", inlineText, chunkMaxChars));
        }

        String single = readResourceSafe(knowledgeFile);
        if (!single.isBlank()) {
            all.addAll(chunkText(resourceId(knowledgeFile, "knowledge.txt"), single, chunkMaxChars));
        }

        if (dirPattern != null && !dirPattern.isBlank()) {
            try {
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                Resource[] resources = resolver.getResources(dirPattern.trim());
                for (Resource r : resources) {
                    if (r == null || !r.exists()) {
                        continue;
                    }
                    String text = readResourceSafe(r);
                    if (text.isBlank()) {
                        continue;
                    }
                    all.addAll(chunkText(resourceId(r, "knowledge"), text, chunkMaxChars));
                }
            } catch (IOException e) {
                // ignora
            }
        }

        // Pré-calcula term counts
        return all.stream()
                .map(KnowledgeBaseService::withTermCounts)
                .collect(Collectors.toList());
    }

    private static Chunk withTermCounts(Chunk chunk) {
        List<String> tokens = tokenize(chunk.text());
        Map<String, Integer> counts = new HashMap<>();
        for (String t : tokens) {
            counts.merge(t, 1, Integer::sum);
        }
        return new Chunk(chunk.id(), chunk.text(), counts, tokens.size());
    }

    private static List<Chunk> chunkText(String idPrefix, String text, int chunkMaxChars) {
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> paragraphs = Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        List<Chunk> out = new ArrayList<>();
        int i = 1;
        StringBuilder buf = new StringBuilder();
        for (String p : paragraphs) {
            if (buf.length() + p.length() + 2 > chunkMaxChars && buf.length() > 0) {
                out.add(new Chunk(idPrefix + "#" + i, buf.toString().trim(), Map.of(), 0));
                i++;
                buf.setLength(0);
            }
            buf.append(p).append("\n\n");
        }
        if (buf.length() > 0) {
            out.add(new Chunk(idPrefix + "#" + i, buf.toString().trim(), Map.of(), 0));
        }
        return out;
    }

    private static String readResourceSafe(Resource r) {
        if (r == null || !r.exists()) {
            return "";
        }
        try (InputStream in = r.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String resourceId(Resource r, String fallback) {
        try {
            String name = r.getFilename();
            return name != null ? name : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String noDiacritics = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String cleaned = noDiacritics.replaceAll("[^a-z0-9]+", " ").trim();
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split("\\s+"))
                .map(String::trim)
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOPWORDS.contains(t))
                .toList();
    }

    private record Chunk(String id, String text, Map<String, Integer> termCounts, int termCountTotal) {
    }

    private record ScoredChunk(Chunk chunk, double score) {
    }
}
