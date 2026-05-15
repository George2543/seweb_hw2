package SeWeb_hw2;

/**
 * DTO (Data Transfer Object) for the incoming chat request from the frontend.
 *
 * RAG Architecture Note:
 * This request object carries the user's natural language question along with
 * contextual metadata (pageType, bookTitle) that helps the RAG pipeline
 * understand what the user is looking at, enabling more relevant chunk retrieval.
 */
public class ChatRequest {

    /** The natural language question from the user, e.g., "Who wrote Dune?" */
    private String message;

    /**
     * The type of page the user is currently viewing.
     * Values: "books" (list page), "book" (detail page), "home", etc.
     * This helps the system tailor its retrieval strategy.
     */
    private String pageType;

    /**
     * The title of the book currently being viewed (only set when pageType = "book").
     * Example: "Dune", "Harry Potter and the Sorcerer's Stone"
     */
    private String bookTitle;

    // --- Getters and Setters ---

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }

    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
}
