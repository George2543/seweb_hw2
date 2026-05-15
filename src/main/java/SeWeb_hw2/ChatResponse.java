package SeWeb_hw2;

import java.util.List;

/**
 * DTO (Data Transfer Object) for the chat response sent back to the frontend.
 *
 * RAG Architecture Note:
 * The response includes both the final answer and the retrieved context chunks.
 * Exposing the retrieved chunks allows the frontend to optionally display them,
 * demonstrating the RAG pipeline's "retrieval" step to the professor.
 */
public class ChatResponse {

    /** The assistant's generated answer based on retrieved RDF chunks. */
    private String answer;

    /**
     * The text chunks that were retrieved from the vector store and used
     * as context to generate the answer. This makes the RAG pipeline transparent.
     */
    private List<String> retrievedChunks;

    // --- Getters and Setters ---

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(List<String> retrievedChunks) { this.retrievedChunks = retrievedChunks; }
}
