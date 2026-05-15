package SeWeb_hw2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Basic Web Controller to serve Home page and interact with RdfService.
 */
@Controller
public class HomeController {

    @Autowired
    private RdfService rdfService;

    /**
     * Shows a home page.
     * Maps to HTTP GET /
     */
    @GetMapping("/")
    public String home() {
        // Also print all triples to console when hitting the home page, for homework demonstration
        rdfService.printAllTriples();
        return "index"; // Looks for src/main/resources/templates/index.html
    }
}