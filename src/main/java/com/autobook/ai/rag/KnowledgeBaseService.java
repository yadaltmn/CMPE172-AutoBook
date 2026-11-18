package com.autobook.ai.rag;

import com.autobook.ai.AiModels;
import com.autobook.ai.AiProperties;
import com.autobook.exception.AiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * The AutoBook knowledge base for retrieval-augmented generation.
 *
 * <ol>
 *     <li><b>Read:</b> the Markdown knowledge source ({@code knowledge/autobook-faq.md}).</li>
 *     <li><b>Chunk:</b> one chunk per {@code ## } section, so every answer can cite a section.</li>
 *     <li><b>Embed and store:</b> the chunks are embedded with the configured embedding model
 *     (Gemini in production) and kept in a Spring AI {@link SimpleVectorStore}. This happens once,
 *     on first use, and is retried later if the embedding call fails.</li>
 *     <li><b>Retrieve:</b> a question is embedded and compared by cosine similarity; only chunks above
 *     the similarity threshold are returned.</li>
 * </ol>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final AiModels aiModels;
    private final AiProperties.Faq settings;
    private final ResourceLoader resourceLoader;
    private volatile List<KnowledgeChunk> chunks;
    private volatile SimpleVectorStore vectorStore;

    public KnowledgeBaseService(AiModels aiModels, AiProperties properties, ResourceLoader resourceLoader) {
        this.aiModels = aiModels;
        this.settings = properties.faq();
        this.resourceLoader = resourceLoader;
    }

    /** The chunks of the knowledge source (reading the file does not need the AI model). */
    public List<KnowledgeChunk> chunks() {
        List<KnowledgeChunk> loaded = chunks;
        if (loaded == null) {
            synchronized (this) {
                if (chunks == null) {
                    chunks = split(readSource(), sourceName());
                }
                loaded = chunks;
            }
        }
        return loaded;
    }

    /**
     * Chunks above the similarity threshold, most similar first. An empty list means the knowledge
     * base does not cover the question.
     */
    public List<RetrievedPassage> retrieve(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(settings.topK())
                .similarityThreshold(settings.similarityThreshold())
                .build();
        List<Document> documents;
        try {
            documents = vectorStore().similaritySearch(request);
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Knowledge base search failed: {}", ex.getClass().getSimpleName());
            throw new AiUnavailableException("The AutoBook knowledge base is temporarily unavailable.", ex);
        }
        List<RetrievedPassage> passages = new ArrayList<>();
        for (Document document : Objects.requireNonNullElse(documents, List.<Document>of())) {
            passages.add(new RetrievedPassage(document.getId(),
                    String.valueOf(document.getMetadata().getOrDefault("section", "")),
                    Objects.requireNonNullElse(document.getText(), ""),
                    Objects.requireNonNullElse(document.getScore(), 0.0)));
        }
        return passages;
    }

    public boolean isIndexed() {
        return vectorStore != null;
    }

    private SimpleVectorStore vectorStore() {
        SimpleVectorStore store = vectorStore;
        if (store != null) {
            return store;
        }
        synchronized (this) {
            if (vectorStore == null) {
                List<KnowledgeChunk> source = chunks();
                SimpleVectorStore created = SimpleVectorStore.builder(aiModels.embeddingModel()).build();
                List<Document> documents = source.stream()
                        .map(chunk -> new Document(chunk.id(), chunk.text(),
                                Map.of("section", chunk.section(), "source", sourceName())))
                        .toList();
                created.add(documents);
                vectorStore = created;
                log.info("Knowledge base indexed: {} chunks from {} embedded with {}", documents.size(), sourceName(),
                        aiModels.embeddingModelName());
            }
            return vectorStore;
        }
    }

    /**
     * Splits Markdown into one chunk per "## " section. Text before the first section (the file's
     * introduction) becomes its own chunk.
     */
    public static List<KnowledgeChunk> split(String markdown, String sourceName) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String section = "Introduction";
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                addChunk(result, sourceName, section, body);
                section = line.substring(3).trim();
                body.setLength(0);
            } else if (!line.startsWith("# ")) {
                body.append(line).append('\n');
            }
        }
        addChunk(result, sourceName, section, body);
        return List.copyOf(result);
    }

    private static void addChunk(List<KnowledgeChunk> result, String sourceName, String section, StringBuilder body) {
        String text = body.toString().strip();
        if (text.isEmpty()) {
            return;
        }
        String slug = section.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        result.add(new KnowledgeChunk(sourceName + "#" + slug, section, section + "\n" + text));
    }

    private String readSource() {
        Resource resource = resourceLoader.getResource(settings.knowledgeSource());
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read knowledge source " + settings.knowledgeSource(), ex);
        }
    }

    private String sourceName() {
        String location = settings.knowledgeSource();
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
