package SeWeb_hw2;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating context-aware conversation starters.
 *
 * RAG Architecture Note:
 * Context-aware starters improve user experience by suggesting relevant
 * questions based on the page the user is currently viewing. This guides
 * users toward questions the RAG system can answer well, since the RDF
 * data is the ground truth.
 */
@Service
public class ChatStarterService {

    /**
     * Returns a list of 3 conversation starter suggestions based on the
     * current page context.
     *
     * @param pageType  The type of page: "books", "book", "home", etc.
     * @param bookTitle The title of the current book (only for "book" pageType)
     * @return A list of suggested questions
     */
    public List<String> getStarters(String pageType, String bookTitle) {
        List<String> starters = new ArrayList<>();

        if ("book".equals(pageType) && bookTitle != null && !bookTitle.isEmpty()) {
            // --- Book detail page: starters are specific to the displayed book ---
            starters.add("Who wrote " + bookTitle + "?");
            starters.add("What themes does " + bookTitle + " have?");
            starters.add("Which users might like " + bookTitle + "?");
        } else if ("books".equals(pageType)) {
            // --- Books list page: starters are about browsing/filtering ---
            starters.add("What book am I most likely to enjoy from this list?");
            starters.add("Show me beginner books.");
            starters.add("Which books are Science Fiction?");
        } else {
            // --- Home page or any other page: general knowledge starters ---
            starters.add("What books are in the database?");
            starters.add("Who are the users in the system?");
            starters.add("What themes are available?");
        }

        return starters;
    }
}
