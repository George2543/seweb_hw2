package SeWeb_hw2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller strictly for handling Book resources mapping properties using BookDto.
 */
@Controller
public class BookController {

    @Autowired
    private RdfService rdfService;

    /**
     * Shows books. If a username is provided, filters to show recommendations.
     * Maps to HTTP GET /books
     */
    @GetMapping("/books")
    public String books(@RequestParam(required = false) String username, Model model) {
        List<Map<String, String>> booksList;

        if (username != null && !username.isEmpty()) {
            booksList = rdfService.getRecommendedBooksForUser(username);
            model.addAttribute("username", username);
        } else {
            booksList = rdfService.getAllBooks();
        }

        // Convert raw results from RdfService into cleanly structured BookDtos
        List<BookDto> dtoList = booksList.stream().map(this::mapToDto).collect(Collectors.toList());

        model.addAttribute("books", dtoList);
        return "books";
    }

    /**
     * Show the Add Book form.
     */
    @GetMapping("/books/add")
    public String showAddBookForm() {
        return "add-book";
    }

    /**
     * Handle the Add Book submission.
     */
    @PostMapping("/books/add")
    public String handleAddBook(
            @RequestParam String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String themes,
            @RequestParam(required = false) String readingLevel) {
        
        rdfService.addBook(title, author, themes, readingLevel);
        return "redirect:/books/" + title;
    }

    /**
     * Show single Book details page.
     */
    @GetMapping("/books/{title}")
    public String showBookDetails(@PathVariable String title, Model model) {
        List<Map<String, String>> results = rdfService.getBookByTitle(title);
        
        if (results != null && !results.isEmpty()) {
            BookDto dto = mapToDto(results.get(0));
            model.addAttribute("book", dto);
            return "book-details";
        }
        
        // If not found, show friendly error map
        model.addAttribute("errorTitle", title);
        return "book-not-found";
    }

    /**
     * Show the Edit Book form.
     */
    @GetMapping("/books/{title}/edit")
    public String showEditBookForm(@PathVariable String title, Model model) {
        List<Map<String, String>> results = rdfService.getBookByTitle(title);
        if (results != null && !results.isEmpty()) {
            BookDto dto = mapToDto(results.get(0));
            model.addAttribute("book", dto);
            return "edit-book";
        }
        
        model.addAttribute("errorTitle", title);
        return "book-not-found"; 
    }

    /**
     * Handle the Edit Book configuration.
     */
    @PostMapping("/books/{title}/edit")
    public String handleEditBook(
            @PathVariable String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String themes,
            @RequestParam(required = false) String readingLevel) {
        
        rdfService.updateBook(title, author, themes, readingLevel);
        return "redirect:/books/" + title;
    }
    
    /**
     * Local helper mapping the Map representation to strongly typed Dto.
     */
    private BookDto mapToDto(Map<String, String> map) {
        BookDto dto = new BookDto();
        dto.setTitle(map.get("title"));
        dto.setAuthor(map.get("author"));
        dto.setThemes(map.get("themes"));
        dto.setReadingLevel(map.get("readingLevel"));
        dto.setUri(map.get("book")); // The ?book resource URI from the SPARQL Query
        return dto;
    }
}