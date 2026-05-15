package SeWeb_hw2;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * =============================================================================
 * ChatService — The "Generation" Step of the RAG Pipeline
 * =============================================================================
 *
 * RAG (Retrieval-Augmented Generation) Architecture Overview:
 *
 * ┌─────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────┐
 * │  User asks   │───►│  VectorStore     │───►│  ChatService     │───►│  Answer  │
 * │  a question  │    │  retrieves top-K │    │  calls Gemini    │    │  to user │
 * │              │    │  relevant chunks │    │  LLM with chunks │    │          │
 * └─────────────┘    └──────────────────┘    └──────────────────┘    └──────────┘
 *
 * This service implements the GENERATION step:
 *   1. Receives the user's natural language question
 *   2. Calls VectorStoreService to retrieve the most relevant text chunks
 *   3. Constructs a RAG prompt with the retrieved chunks as context
 *   4. Sends the prompt to Google Gemini LLM via the REST API
 *   5. Returns the LLM's answer (which is grounded in the RDF data, not model knowledge)
 *
 * IMPORTANT: The chatbot trusts the RDF/vector data as ground truth.
 * If the database says Harry Potter was written by "Gigel", the LLM will answer "Gigel"
 * because the prompt instructs it to use ONLY the provided context.
 *
 * The LLM integration uses Google AI Studio's Gemini API (gemini-2.0-flash model).
 * No external SDK is needed — we use Java 17's built-in java.net.http.HttpClient.
 * =============================================================================
 */
@Service
public class ChatService {

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * Google Gemini API key, read from application.properties.
     * Obtain a free key from https://aistudio.google.com
     */
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    /**
     * The Gemini model to use. Default: gemini-2.0-flash (fast, free tier).
     * Can be overridden in application.properties.
     */
    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    /** Reusable HTTP client for making requests to the Gemini API. */
    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        // Create a reusable HTTP client with a 30-second timeout
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        System.out.println("[RAG] ChatService initialized with Gemini model: " + geminiModel);
    }

    /**
     * Processes a user's chat message through the full RAG pipeline.
     *
     * The pipeline:
     *   1. RETRIEVE relevant chunks from the vector store
     *   2. BUILD a RAG prompt with the chunks as context
     *   3. SEND the prompt to Google Gemini LLM
     *   4. RETURN the LLM's grounded answer
     *
     * @param request The chat request containing the user's question and context
     * @return A ChatResponse with the LLM's answer and the retrieved chunks
     */
    public ChatResponse chat(ChatRequest request) {
        String question = request.getMessage();

        // =====================================================================
        // STEP 1: RETRIEVAL — Get relevant chunks from the vector store
        // =====================================================================
        // We retrieve the top 5 most relevant chunks. In production, this number
        // would be tuned based on the LLM's context window size and the desired
        // precision/recall trade-off.
        List<String> relevantChunks = vectorStoreService.searchRelevantChunks(question, 5);

        // For broad listing questions (e.g., "What books are in the database?"),
        // keyword search may return only a subset of chunks. For listing queries,
        // we want ALL chunks so the LLM can enumerate everything in the database.
        String qLower = question.toLowerCase();
        boolean isBroadQuery = qLower.contains("database") || qLower.contains("all books")
                || qLower.contains("all users") || qLower.contains("in the system")
                || qLower.contains("available") || (qLower.contains("books") && qLower.contains("list"))
                || qLower.contains("who are") || qLower.contains("what themes")
                || qLower.contains("enjoy") || qLower.contains("recommend")
                || (qLower.contains("what books") && !qLower.contains("author") && !qLower.contains("theme"));
        if (isBroadQuery) {
            // Retrieve all chunks for comprehensive listing answers
            relevantChunks = vectorStoreService.searchRelevantChunks("book user theme level author", 50);
        }

        // =====================================================================
        // STEP 2: BUILD RAG PROMPT — Combine context chunks with the question
        // =====================================================================
        // The RAG prompt instructs the LLM to answer using ONLY the provided
        // context. This ensures the answer is grounded in our RDF data, not
        // in the model's pre-trained knowledge.
        String prompt = buildRagPrompt(question, relevantChunks);

        // =====================================================================
        // STEP 3: CALL GEMINI LLM — Send the prompt and get a response
        // =====================================================================
        String answer = callGemini(prompt);

        // =====================================================================
        // STEP 4: BUILD RESPONSE — Include both the LLM answer and retrieved chunks
        // =====================================================================
        ChatResponse response = new ChatResponse();
        response.setAnswer(answer);
        response.setRetrievedChunks(relevantChunks);
        return response;
    }

    /**
     * ==========================================================================
     * RAG Prompt Construction
     * ==========================================================================
     *
     * Builds the prompt that will be sent to the Gemini LLM. The prompt:
     *   1. Sets the system instruction: answer ONLY from the context
     *   2. Provides the retrieved text chunks as numbered context items
     *   3. Appends the user's question at the end
     *
     * This is the core of the RAG pattern — by providing context in the prompt,
     * we override the LLM's pre-trained knowledge with our RDF data.
     *
     * @param question The user's natural language question
     * @param chunks   The retrieved context chunks from the vector store
     * @return The complete prompt string to send to the LLM
     */
    private String buildRagPrompt(String question, List<String> chunks) {
        StringBuilder sb = new StringBuilder();

        // System instruction: ground the LLM's answer in our data
        sb.append("You are a helpful book recommendation assistant. ");
        sb.append("Answer the user's question using ONLY the context provided below. ");
        sb.append("Do NOT use any prior knowledge. If the context does not contain the answer, say so. ");
        sb.append("Keep your answers concise and direct.\n\n");

        // Provide the retrieved chunks as numbered context items
        if (chunks.isEmpty()) {
            sb.append("Context: No relevant information found in the database.\n\n");
        } else {
            sb.append("Context (from our book recommendation database):\n");
            for (int i = 0; i < chunks.size(); i++) {
                sb.append(i + 1).append(". ").append(chunks.get(i)).append("\n");
            }
            sb.append("\n");
        }

        // Append the user's question
        sb.append("Question: ").append(question).append("\n");
        sb.append("Answer:");

        return sb.toString();
    }

    /**
     * ==========================================================================
     * Google Gemini API Integration
     * ==========================================================================
     *
     * Sends a prompt to the Google Gemini LLM via the REST API and returns
     * the generated text response.
     *
     * API endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
     * Authentication: API key passed as query parameter
     * Request format: JSON with "contents" array containing "parts" with "text"
     *
     * We use Java 17's built-in HttpClient — no external HTTP library needed.
     *
     * @param prompt The complete RAG prompt to send to the LLM
     * @return The LLM's generated text response
     */
    private String callGemini(String prompt) {
        try {
            // Build the Gemini API URL with the model name and API key
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + geminiModel + ":generateContent?key=" + geminiApiKey;

            // Construct the JSON request body
            // We escape the prompt text to handle quotes, newlines, backslashes etc.
            String jsonBody = """
                {
                  "contents": [{
                    "parts": [{"text": %s}]
                  }],
                  "generationConfig": {
                    "temperature": 0.3,
                    "maxOutputTokens": 500
                  }
                }
                """.formatted(escapeJsonString(prompt));

            // Build and send the HTTP POST request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Parse the response to extract the generated text
            if (response.statusCode() == 200) {
                return extractTextFromGeminiResponse(response.body());
            } else {
                System.err.println("[RAG] Gemini API error (HTTP " + response.statusCode() + "): " + response.body());
                return "Sorry, the AI service returned an error. Please try again.";
            }

        } catch (Exception e) {
            System.err.println("[RAG] Error calling Gemini API: " + e.getMessage());
            e.printStackTrace();
            return "Sorry, I couldn't reach the AI service. Please check your connection and try again.";
        }
    }

    /**
     * Parses the Gemini API JSON response to extract the generated text.
     *
     * The Gemini response format is:
     * {
     *   "candidates": [{
     *     "content": {
     *       "parts": [{"text": "The actual answer text here"}]
     *     }
     *   }]
     * }
     *
     * We use simple string parsing instead of a JSON library to avoid
     * adding another dependency (the project already doesn't use Jackson directly).
     */
    private String extractTextFromGeminiResponse(String responseBody) {
        try {
            // Find the "text" field inside the response
            // Look for: "text": "..." or "text": "...\n..."
            int textKeyIndex = responseBody.indexOf("\"text\"");
            if (textKeyIndex < 0) {
                System.err.println("[RAG] Could not find 'text' field in Gemini response: " + responseBody);
                return "Sorry, I received an unexpected response format.";
            }

            // Find the start of the text value (after "text": )
            int colonIndex = responseBody.indexOf(":", textKeyIndex);
            int quoteStart = responseBody.indexOf("\"", colonIndex + 1);
            if (quoteStart < 0) {
                return "Sorry, I received an unexpected response format.";
            }

            // Find the end of the text value (handle escaped quotes)
            StringBuilder result = new StringBuilder();
            int i = quoteStart + 1;
            while (i < responseBody.length()) {
                char c = responseBody.charAt(i);
                if (c == '\\' && i + 1 < responseBody.length()) {
                    char next = responseBody.charAt(i + 1);
                    switch (next) {
                        case '"': result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        case 'n': result.append('\n'); break;
                        case 'r': result.append('\r'); break;
                        case 't': result.append('\t'); break;
                        default: result.append(c).append(next); break;
                    }
                    i += 2;
                } else if (c == '"') {
                    break; // End of string
                } else {
                    result.append(c);
                    i++;
                }
            }

            String text = result.toString().trim();
            if (text.isEmpty()) {
                return "I don't have enough information to answer that question.";
            }
            return text;

        } catch (Exception e) {
            System.err.println("[RAG] Error parsing Gemini response: " + e.getMessage());
            return "Sorry, I couldn't parse the AI response.";
        }
    }

    /**
     * Properly escapes a string for inclusion in a JSON value.
     * Handles: backslash, double-quote, newline, carriage return, tab.
     *
     * Returns the string wrapped in double quotes, ready for JSON insertion.
     */
    private String escapeJsonString(String input) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
