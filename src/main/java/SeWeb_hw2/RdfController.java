package SeWeb_hw2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller strictly for handling RDF specific operations
 * like uploading RDF/XML models and fetching graph metadata.
 */
@Controller
public class RdfController {

    @Autowired
    private RdfService rdfService;

    /**
     * Show the RDF Upload form.
     */
    @GetMapping("/rdf/upload")
    public String showUploadForm() {
        return "rdf-upload";
    }

    /**
     * Handle the RDF file upload logic via multipart form data.
     */
    @PostMapping("/rdf/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file) {
        if (!file.isEmpty()) {
            try {
                // Pipe the InputStream directly into Jena
                rdfService.uploadRdf(file.getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // Redirect directly to the canvas to view changes
        return "redirect:/rdf/graph";
    }

    /**
     * Show the Thymeleaf page containing the Vis.js canvas.
     */
    @GetMapping("/rdf/graph")
    public String showGraphPage() {
        return "rdf-graph";
    }

    /**
     * REST endpoint polled by VIS.JS frontend network to draw node edges.
     * Returns JSON of {"nodes": [...], "edges": [...]}
     */
    @ResponseBody
    @GetMapping("/api/rdf/graph")
    public GraphResponse getGraphApi() {
        return rdfService.getGraphData();
    }
}