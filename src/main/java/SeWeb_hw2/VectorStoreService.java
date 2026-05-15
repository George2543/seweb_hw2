package SeWeb_hw2;

import java.util.List;

/**
 * =============================================================================
 * VectorStoreService — Abstraction Layer for the RAG Vector Database
 * =============================================================================
 *
 * RAG Architecture Note:
 * In a full Retrieval-Augmented Generation pipeline, the vector store is
 * responsible for:
 *
 *   1. INDEXING: Converting text chunks into dense vector embeddings and
 *      storing them in a searchable index (e.g., FAISS, Pinecone, ChromaDB).
 *
 *   2. RETRIEVAL: Given a user query, converting it to a vector embedding
 *      and performing approximate nearest neighbor (ANN) search to find
 *      the most semantically similar chunks.
 *
 * This interface abstracts the vector store so we can:
 *   - Use a simple local keyword-matching implementation for the demo
 *   - Swap in a production vector database (Pinecone, Weaviate, Chroma)
 *     without changing the ChatService or any other component
 *
 * The two core operations are:
 *   - rebuildIndexFromRdf(): Re-chunk the RDF data and rebuild the search index
 *   - searchRelevantChunks(): Find the top-K most relevant chunks for a query
 * =============================================================================
 */
public interface VectorStoreService {

    /**
     * Rebuilds the entire vector index from the current RDF model.
     *
     * This method should:
     *   1. Read all triples from the Jena RDF model
     *   2. Convert them into human-readable text chunks
     *      (e.g., "Dune is a book written by Frank Herbert...")
     *   3. Index those chunks for later similarity search
     *
     * Called on application startup and whenever the RDF data changes
     * (e.g., after adding/editing a book or uploading an RDF file).
     */
    void rebuildIndexFromRdf();

    /**
     * Searches the vector index and returns the top-K most relevant text chunks
     * for the given natural language question.
     *
     * @param question The user's natural language query
     * @param topK     Maximum number of chunks to return
     * @return A list of human-readable text chunks ranked by relevance
     *
     * In a production system, this would:
     *   1. Embed the question using the same embedding model
     *   2. Perform ANN search in the vector index
     *   3. Return the top-K nearest chunks
     *
     * Our local demo implementation uses keyword/TF-IDF-style similarity instead.
     */
    List<String> searchRelevantChunks(String question, int topK);
}
