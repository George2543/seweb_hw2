package SeWeb_hw2;

import jakarta.annotation.PostConstruct;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.FileOutputStream; // ADDED THIS
import java.io.File; // ADDED THIS
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Property; // ADDED THIS
import org.apache.jena.rdf.model.Resource; // ADDED THIS
import org.apache.jena.rdf.model.ResIterator; // ADDED THIS
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.RDFNode;

/**
 * Service to manage the interactions with the Apache Jena RDF Model.
 * This handles loading the data and running SPARQL queries.
 */
@Service
public class RdfService {

    // The in-memory graph (model) storing our triples.
    private Model model;
    
    // The namespace defined in the RDF file.
    private static final String NAMESPACE = "http://example.org/book-recommendation#";

    /**
     * Lazy-injected reference to the vector store, so we can rebuild the
     * RAG search index whenever RDF data changes (add/edit/upload).
     * @Lazy breaks the circular dependency: LocalVectorStoreService → RdfService.
     */
    @Autowired
    @Lazy
    private VectorStoreService vectorStoreService;

    /**
     * Initializes the service by creating an empty model and loading our RDF data.
     */
    @PostConstruct
    public void init() {
        // Create an empty default model
        model = ModelFactory.createDefaultModel();
        
        // Load the books.rdf file from the classpath resources folder
        InputStream in = getClass().getResourceAsStream("/data/books.rdf");
        if (in != null) {
            // Read the data in RDF/XML format
            model.read(in, null, "RDF/XML");
            System.out.println("Data successfully loaded into Jena model.");
        } else {
            System.err.println("Could not find the books.rdf file.");
        }
    }

    /**
     * Prints all the loaded triples to the console. Useful for debugging.
     */
    public void printAllTriples() {
        System.out.println("--- All triples in the RDF model ---");
        // We write in TURTLE format because it's easier to read in the console
        model.write(System.out, "TURTLE");
    }

    /**
     * Helper method to execute a SELECT SPARQL query and parse the ResultSet 
     * into a List of Maps for easier consumption in the frontend (Thymeleaf).
     */
    private List<Map<String, String>> executeSelectQuery(String queryString) {
        List<Map<String, String>> resultsList = new ArrayList<>();
        
        // QueryExecution is a resource that needs to be closed, hence try-with-resources
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            
            // Loop through each row of results
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Map<String, String> row = new HashMap<>();
                
                // Put every mapped variable into our Hashmap
                soln.varNames().forEachRemaining(varName -> {
                    if (soln.get(varName) != null) {
                        // Using .toString() handles Literal strings
                        row.put(varName, soln.get(varName).toString()); 
                    }
                });
                resultsList.add(row);
            }
        }
        return resultsList;
    }

    /**
     * Retrieves all books defined in the model.
     */
    public List<Map<String, String>> getAllBooks() {
        // SPARQL Query to retrieve all books
        // GROUP_CONCAT is used in case a book has multiple themes (e.g., Science Fiction, Fantasy)
        String queryString = 
            "PREFIX ex: <" + NAMESPACE + "> " +
            "SELECT ?book ?title (GROUP_CONCAT(?theme; separator=', ') AS ?themes) ?author ?readingLevel " +
            "WHERE { " +
            "   ?book a ex:Book . " +
            "   ?book ex:title ?title . " +
            "   OPTIONAL { ?book ex:theme ?theme } . " +
            "   OPTIONAL { ?book ex:author ?author } . " +
            "   OPTIONAL { ?book ex:suitableReadingLevel ?readingLevel } . " +
            "} GROUP BY ?book ?title ?author ?readingLevel";
            
        return executeSelectQuery(queryString);
    }

    /**
     * Retrieves a specific book by its exact title.
     * Uses ParameterizedSparqlString to safely handle spaces and quotes in titles.
     */
    public List<Map<String, String>> getBookByTitle(String title) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setCommandText(
            "PREFIX ex: <" + NAMESPACE + "> " +
            "SELECT ?book ?title (GROUP_CONCAT(?theme; separator=', ') AS ?themes) ?author ?readingLevel " +
            "WHERE { " +
            "   ?book a ex:Book . " +
            "   ?book ex:title ?title . " +
            "   FILTER (str(?title) = ?targetTitle) " + // Safely filter by title
            "   OPTIONAL { ?book ex:theme ?theme } . " +
            "   OPTIONAL { ?book ex:author ?author } . " +
            "   OPTIONAL { ?book ex:suitableReadingLevel ?readingLevel } . " +
            "} GROUP BY ?book ?title ?author ?readingLevel"
        );
        pss.setLiteral("targetTitle", title);
            
        return executeSelectQuery(pss.toString());
    }

    /**
     * Recommends books for a given username based on reading level and preferred theme.
     */
    public List<Map<String, String>> getRecommendedBooksForUser(String username) {
        // Use a subquery to find matching books, then fetch all details including all themes
        String queryString = 
            "PREFIX ex: <" + NAMESPACE + "> " +
            "SELECT ?book ?title (GROUP_CONCAT(?theme; separator=', ') AS ?themes) ?author ?readingLevel " +
            "WHERE { " +
            "   { " +
            "       SELECT DISTINCT ?book " +
            "       WHERE { " +
            "           ?user a ex:User . " +
            "           FILTER (str(?user) = \"http://example.org/book-recommendation#" + username + "\") " +
            "           ?user ex:preferredTheme ?userTheme . " +
            "           ?user ex:readingLevel ?userLevel . " +
            "           " +
            "           ?book a ex:Book . " +
            "           ?book ex:theme ?userTheme . " +
            "           ?book ex:suitableReadingLevel ?userLevel . " +
            "       } " +
            "   } " +
            "   ?book ex:title ?title . " +
            "   OPTIONAL { ?book ex:theme ?theme } . " +
            "   OPTIONAL { ?book ex:author ?author } . " +
            "   OPTIONAL { ?book ex:suitableReadingLevel ?readingLevel } . " +
            "} GROUP BY ?book ?title ?author ?readingLevel";
            
        return executeSelectQuery(queryString);
    }

    /**
     * Returns the underlying Jena Model so other services can access the RDF graph.
     */
    public org.apache.jena.rdf.model.Model getModel() {
        return model;
    }

    /**
     * Retrieves all users defined in the model, along with their preferences.
     * Used by the RAG pipeline to build user-related text chunks.
     */
    public List<Map<String, String>> getAllUsers() {
        String queryString =
            "PREFIX ex: <" + NAMESPACE + "> " +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
            "SELECT ?user ?name ?preferredTheme ?readingLevel " +
            "WHERE { " +
            "   ?user a ex:User . " +
            "   OPTIONAL { ?user rdfs:label ?name } . " +
            "   OPTIONAL { ?user ex:preferredTheme ?preferredTheme } . " +
            "   OPTIONAL { ?user ex:readingLevel ?readingLevel } . " +
            "}";

        return executeSelectQuery(queryString);
    }

    /**
     * Saves the current working Model back into the rdf file.
     * Writing it back to src/main/resources/... ensures persistence for this homework assignment.
     */
    public void saveModel() {
        try {
            // Write back to the exact location requested so homework changes persist
            File file = new File("src/main/resources/data/books.rdf");
            FileOutputStream out = new FileOutputStream(file);
            model.write(out, "RDF/XML");
            out.close();
            System.out.println("Model safely rewritten/updated to books.rdf.");
            
            // Rebuild the RAG vector store index to reflect the updated data
            if (vectorStoreService != null) {
                vectorStoreService.rebuildIndexFromRdf();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an entirely new Book into the Jena Model and saves to file.
     */
    public void addBook(String title, String author, String themesStr, String readingLevel) {
        // Construct standard URI without spaces
        String bookUri = NAMESPACE + title.replaceAll("\\s+", "");
        Resource book = model.createResource(bookUri);
        
        // Define it as type Book
        Property typeProp = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        Resource bookClass = model.createResource(NAMESPACE + "Book");
        model.add(book, typeProp, bookClass);
        
        Property titleProp = model.createProperty(NAMESPACE + "title");
        model.add(book, titleProp, title);
        
        if (author != null && !author.isBlank()) {
            Property authorProp = model.createProperty(NAMESPACE + "author");
            model.add(book, authorProp, author);
        }
        
        if (themesStr != null && !themesStr.isBlank()) {
            Property themeProp = model.createProperty(NAMESPACE + "theme");
            String[] themes = themesStr.split(",");
            for (String t : themes) {
                model.add(book, themeProp, t.trim());
            }
        }
        
        if (readingLevel != null && !readingLevel.isBlank()) {
            Property levelProp = model.createProperty(NAMESPACE + "suitableReadingLevel");
            model.add(book, levelProp, readingLevel);
        }
        
        // Finally, persist modifications
        saveModel();
    }

    /**
     * Updates an existing book inside the Model. We remove the old properties and insert the new ones.
     */
    public void updateBook(String title, String author, String themesStr, String readingLevel) {
        // We find the book based on the literal exact title String
        Property titleProp = model.createProperty(NAMESPACE + "title");
        ResIterator iter = model.listResourcesWithProperty(titleProp, title);
        
        if (iter.hasNext()) {
            Resource book = iter.nextResource();
            
            // Remove the old author and add the new one
            Property authorProp = model.createProperty(NAMESPACE + "author");
            book.removeAll(authorProp);
            if (author != null && !author.isBlank()) {
                book.addProperty(authorProp, author);
            }
            
            // Remove the old themes and add the new comma separated ones
            Property themeProp = model.createProperty(NAMESPACE + "theme");
            book.removeAll(themeProp);
            if (themesStr != null && !themesStr.isBlank()) {
                String[] themes = themesStr.split(",");
                for (String t : themes) {
                    book.addProperty(themeProp, t.trim());
                }
            }
            
            // Remove the old reading level and add the newly specified one
            Property levelProp = model.createProperty(NAMESPACE + "suitableReadingLevel");
            book.removeAll(levelProp);
            if (readingLevel != null && !readingLevel.isBlank()) {
                book.addProperty(levelProp, readingLevel);
            }
        }
        
        // Push changes to disk
        saveModel();
    }

    /**
     * Upload an external RDF file into the model, and save it.
     */
    public void uploadRdf(InputStream in) {
        // Read the RDF data from the uploaded stream into the current model
        model.read(in, null, "RDF/XML");
        System.out.println("New RDF file appended to model.");
        // Persist the combined model to our main books.rdf
        saveModel();
    }

    /**
     * Convert the Model statements into JSON-friendly format for Graph Visualization.
     */
    public GraphResponse getGraphData() {
        GraphResponse response = new GraphResponse();
        Map<String, GraphNode> nodeMap = new HashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        StmtIterator iter = model.listStatements();
        while (iter.hasNext()) {
            Statement stmt = iter.nextStatement();
            
            // Short labels
            String subLabel = getShortLabel(stmt.getSubject());
            String objLabel = getShortLabel(stmt.getObject());
            String predLabel = getShortLabel(stmt.getPredicate());

            // Build Nodes Map to ensure uniqueness
            if (!nodeMap.containsKey(subLabel)) {
                nodeMap.put(subLabel, new GraphNode(subLabel, subLabel));
            }
            if (!nodeMap.containsKey(objLabel)) {
                nodeMap.put(objLabel, new GraphNode(objLabel, objLabel));
            }

            // Create Edge
            edges.add(new GraphEdge(subLabel, objLabel, predLabel));
        }

        response.setNodes(new ArrayList<>(nodeMap.values()));
        response.setEdges(edges);
        return response;
    }

    /**
     * Helper to shorten long URIs into readable strings for the graph UI.
     */
    private String getShortLabel(RDFNode node) {
        if (node.isLiteral()) {
            return node.asLiteral().getString();
        } else if (node.isResource() && node.asResource().getURI() != null) {
            String uri = node.asResource().getURI();
            if (uri.contains("#")) {
                return uri.substring(uri.lastIndexOf('#') + 1);
            } else if (uri.contains("/")) {
                return uri.substring(uri.lastIndexOf('/') + 1);
            }
            return uri;
        }
        return node.toString();
    }
}