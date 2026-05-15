package SeWeb_hw2;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * =============================================================================
 * LocalVectorStoreService — Local Keyword-Based Vector Store Implementation
 * =============================================================================
 *
 * RAG Architecture Note:
 * This is a LOCAL implementation of the VectorStoreService interface.
 * Instead of using real vector embeddings (e.g., OpenAI ada-002, sentence-transformers),
 * it uses a TF-IDF-inspired keyword similarity approach.
 *
 * HOW IT WORKS:
 * ┌──────────────────────────────────────────────────────────────┐
 * │  1. CHUNKING PHASE (rebuildIndexFromRdf)                    │
 * │     RDF Model → SPARQL queries → Human-readable text chunks │
 * │     Example: "Dune is a book written by Frank Herbert.      │
 * │              It has themes Science Fiction and Fantasy.      │
 * │              It is suitable for Advanced readers."           │
 * │                                                             │
 * │  2. INDEXING PHASE (rebuildIndexFromRdf)                    │
 * │     Each chunk → Set of lowercase keyword tokens            │
 * │     Stored in an in-memory List (simulates vector DB)       │
 * │                                                             │
 * │  3. RETRIEVAL PHASE (searchRelevantChunks)                  │
 * │     User query → tokenize → count keyword overlaps with     │
 * │     each chunk → sort by score → return top-K chunks        │
 * └──────────────────────────────────────────────────────────────┘
 *
 * In a production system, steps 2 and 3 would use dense vector embeddings
 * and approximate nearest neighbor (ANN) search via FAISS, Pinecone, or ChromaDB.
 *
 * This implementation is acceptable for a demo because:
 *   - The code is cleanly structured behind the VectorStoreService interface
 *   - Swapping in a real vector DB only requires a new implementation class
 *   - The chunking logic (step 1) remains exactly the same
 * =============================================================================
 */
@Service
public class LocalVectorStoreService implements VectorStoreService {

    @Autowired
    private RdfService rdfService;

    /**
     * Internal storage: each entry is one text chunk about a book or user.
     * In a production vector DB, each chunk would also have a vector embedding.
     */
    private final List<String> chunks = new ArrayList<>();

    /**
     * Pre-computed keyword sets for each chunk (parallel to the chunks list).
     * This simulates the "vector index" — in production, these would be
     * dense float[] embeddings stored in FAISS/Pinecone.
     */
    private final List<Set<String>> chunkKeywords = new ArrayList<>();

    /**
     * Automatically rebuild the index when the application starts.
     * This ensures the vector store is ready before any chat requests arrive.
     */
    @PostConstruct
    public void init() {
        rebuildIndexFromRdf();
    }

    /**
     * ==========================================================================
     * STEP 1 & 2 of the RAG pipeline: CHUNKING + INDEXING
     * ==========================================================================
     *
     * Reads all Books and Users from the RDF model via SPARQL queries,
     * converts each entity into a human-readable text chunk, and indexes
     * the chunk's keywords for later similarity search.
     *
     * This method is called:
     *   - On application startup (via @PostConstruct)
     *   - After any RDF data modification (add/edit/upload)
     */
    @Override
    public void rebuildIndexFromRdf() {
        chunks.clear();
        chunkKeywords.clear();

        // --- Chunk all Book entities ---
        List<Map<String, String>> books = rdfService.getAllBooks();
        for (Map<String, String> book : books) {
            String chunk = buildBookChunk(book);
            addChunk(chunk);
        }

        // --- Chunk all User entities ---
        List<Map<String, String>> users = rdfService.getAllUsers();
        for (Map<String, String> user : users) {
            String chunk = buildUserChunk(user);
            addChunk(chunk);
        }

        System.out.println("[RAG] Vector store rebuilt with " + chunks.size() + " chunks.");
    }

    /**
     * ==========================================================================
     * STEP 3 of the RAG pipeline: RETRIEVAL
     * ==========================================================================
     *
     * Given a user's natural language question, finds the most relevant
     * text chunks by computing keyword overlap scores.
     *
     * Algorithm (TF-IDF inspired):
     *   1. Tokenize the question into lowercase keywords
     *   2. For each chunk, count how many query keywords appear in the chunk
     *   3. Sort chunks by descending match count
     *   4. Return the top-K highest-scoring chunks
     *
     * In production, this would use cosine similarity between the query
     * embedding and each chunk embedding, computed via ANN search.
     *
     * @param question The user's natural language query
     * @param topK     Maximum number of chunks to return
     * @return Ordered list of the most relevant text chunks
     */
    @Override
    public List<String> searchRelevantChunks(String question, int topK) {
        // Tokenize the user's question into individual keywords
        Set<String> queryTokens = tokenize(question);

        // Score each chunk by counting keyword overlaps with the query
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Set<String> chunkTokens = chunkKeywords.get(i);

            // Count how many query tokens appear in this chunk's keyword set
            long matchCount = queryTokens.stream()
                    .filter(chunkTokens::contains)
                    .count();

            if (matchCount > 0) {
                scored.add(new ScoredChunk(chunks.get(i), matchCount));
            }
        }

        // Sort by descending score (most relevant first)
        scored.sort((a, b) -> Long.compare(b.score, a.score));

        // Return only the top-K chunks
        return scored.stream()
                .limit(topK)
                .map(sc -> sc.text)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Private helper methods
    // =========================================================================

    /**
     * Converts a Book's SPARQL result row into a human-readable text chunk.
     *
     * Example output:
     * "Dune is a book written by Frank Herbert. It has themes Science Fiction
     *  and Fantasy. It is suitable for Advanced readers."
     */
    private String buildBookChunk(Map<String, String> book) {
        StringBuilder sb = new StringBuilder();
        String title = book.getOrDefault("title", "Unknown");
        String author = book.getOrDefault("author", "Unknown");
        String themes = book.getOrDefault("themes", "");
        String level = book.getOrDefault("readingLevel", "");

        sb.append(title).append(" is a book written by ").append(author).append(".");

        if (!themes.isEmpty()) {
            // Replace comma-separated themes with "and" for natural language
            String formattedThemes = themes.replace(", ", " and ");
            sb.append(" It has themes ").append(formattedThemes).append(".");
        }

        if (!level.isEmpty()) {
            sb.append(" It is suitable for ").append(level).append(" readers.");
        }

        return sb.toString();
    }

    /**
     * Converts a User's SPARQL result row into a human-readable text chunk.
     *
     * Example output:
     * "Alice is a user. Alice prefers Science Fiction and has reading level Intermediate."
     */
    private String buildUserChunk(Map<String, String> user) {
        StringBuilder sb = new StringBuilder();
        String name = user.getOrDefault("name", "Unknown");
        String theme = user.getOrDefault("preferredTheme", "");
        String level = user.getOrDefault("readingLevel", "");

        sb.append(name).append(" is a user.");

        if (!theme.isEmpty()) {
            sb.append(" ").append(name).append(" prefers ").append(theme);
            if (!level.isEmpty()) {
                sb.append(" and has reading level ").append(level);
            }
            sb.append(".");
        } else if (!level.isEmpty()) {
            sb.append(" ").append(name).append(" has reading level ").append(level).append(".");
        }

        return sb.toString();
    }

    /**
     * Adds a chunk to the store and pre-computes its keyword set (the "index").
     */
    private void addChunk(String chunk) {
        chunks.add(chunk);
        chunkKeywords.add(tokenize(chunk));
    }

    /**
     * Tokenizes a string into a set of lowercase keywords.
     * Removes common stop words and punctuation.
     *
     * In a production system, this would be replaced by an embedding model
     * (e.g., OpenAI text-embedding-ada-002 or sentence-transformers).
     */
    private Set<String> tokenize(String text) {
        // Common English stop words to ignore during matching
        Set<String> stopWords = Set.of(
            "is", "a", "the", "an", "and", "or", "of", "for", "to", "in",
            "it", "has", "have", "with", "by", "from", "that", "this",
            "are", "was", "were", "be", "been", "being", "do", "does",
            "did", "will", "would", "could", "should", "may", "might",
            "shall", "can", "what", "which", "who", "whom", "whose",
            "where", "when", "how", "not", "no", "but", "if", "then",
            "than", "too", "very", "just", "about", "above", "after",
            "me", "my", "i", "you", "your", "we", "our", "they", "their",
            "he", "she", "him", "her", "its", "on", "at", "as", "so"
        );

        Set<String> tokens = new HashSet<>();
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")  // Strip punctuation
                .split("\\s+");                        // Split on whitespace

        for (String word : words) {
            if (!word.isEmpty() && !stopWords.contains(word)) {
                tokens.add(word);
                // Basic stemming: strip trailing 's' so "books" also matches "book"
                if (word.length() > 3 && word.endsWith("s")) {
                    tokens.add(word.substring(0, word.length() - 1));
                }
            }
        }
        return tokens;
    }

    /**
     * Internal helper class to pair a chunk with its relevance score.
     */
    private static class ScoredChunk {
        final String text;
        final long score;

        ScoredChunk(String text, long score) {
            this.text = text;
            this.score = score;
        }
    }
}
