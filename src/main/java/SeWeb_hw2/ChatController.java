package SeWeb_hw2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * =============================================================================
 * ChatController — REST API for the RAG Chatbot
 * =============================================================================
 *
 * Exposes the following endpoints:
 *
 *   GET  /api/chat/starters?pageType=books
 *   GET  /api/chat/starters?pageType=book&bookTitle=Dune
 *   POST /api/chat         (JSON body: ChatRequest)
 *
 * These endpoints are consumed by the floating chat widget (chat-widget.js)
 * embedded in every Thymeleaf page via a fragment include.
 * =============================================================================
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatStarterService chatStarterService;

    @Autowired
    private VectorStoreService vectorStoreService;

    /**
     * Returns context-aware conversation starters based on the current page.
     *
     * Examples:
     *   GET /api/chat/starters?pageType=books
     *   GET /api/chat/starters?pageType=book&bookTitle=Dune
     *
     * @param pageType  The type of page the user is on ("books", "book", "home")
     * @param bookTitle Optional book title when on a book detail page
     * @return A list of 3 suggested starter questions
     */
    @GetMapping("/starters")
    public List<String> getStarters(
            @RequestParam String pageType,
            @RequestParam(required = false) String bookTitle) {
        return chatStarterService.getStarters(pageType, bookTitle);
    }

    /**
     * Processes a user's chat message through the full RAG pipeline.
     *
     * The request JSON body should contain:
     * {
     *   "message": "What book has the author Frank Herbert and the theme Science Fiction?",
     *   "pageType": "books",
     *   "bookTitle": null
     * }
     *
     * The response JSON will contain:
     * {
     *   "answer": "Dune.",
     *   "retrievedChunks": ["Dune is a book written by Frank Herbert..."]
     * }
     *
     * @param request The chat request DTO
     * @return A ChatResponse with the answer and retrieved context chunks
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request);
    }

    /**
     * Manually triggers a rebuild of the vector store index.
     * Useful for debugging or after external RDF data changes.
     *
     * POST /api/chat/rebuild-index
     */
    @PostMapping("/rebuild-index")
    public String rebuildIndex() {
        vectorStoreService.rebuildIndexFromRdf();
        return "Vector store index rebuilt successfully.";
    }
}
