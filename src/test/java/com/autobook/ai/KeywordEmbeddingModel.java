package com.autobook.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.CRC32;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic stand-in for the Gemini embedding model: a hashed bag of word stems. Texts that share
 * meaningful words get a high cosine similarity and unrelated texts get almost none, which makes
 * retrieval in tests predictable. It is not a semantic model.
 */
public class KeywordEmbeddingModel implements EmbeddingModel {

    static final int DIMENSIONS = 512;

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "are", "you", "your", "can", "what",
            "how", "with", "this", "that", "does", "have", "has", "from", "will", "not", "any", "who", "when",
            "where", "which", "about", "into", "may", "our", "per", "its", "all", "one", "there", "their", "they",
            "them", "than", "then", "also", "but", "use", "used", "using", "should", "would", "could");

    private final List<String> embeddedTexts = new ArrayList<>();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vector(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public synchronized List<String> embeddedTexts() {
        return List.copyOf(embeddedTexts);
    }

    synchronized float[] vector(String text) {
        embeddedTexts.add(text == null ? "" : text);
        float[] vector = new float[DIMENSIONS];
        // Small constant component so no vector is all zeros (cosine similarity needs a non-zero norm).
        vector[0] = 0.05f;
        for (String token : (text == null ? "" : text).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() < 3 || STOP_WORDS.contains(token)) {
                continue;
            }
            String stem = token.length() > 4 ? token.substring(0, 4) : token;
            CRC32 crc = new CRC32();
            crc.update(stem.getBytes(StandardCharsets.UTF_8));
            vector[1 + (int) (crc.getValue() % (DIMENSIONS - 1))] += 1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
