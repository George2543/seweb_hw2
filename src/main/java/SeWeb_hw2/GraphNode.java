package SeWeb_hw2;

/**
 * Represents a Node in the RDF Graph for vis.js visualization.
 */
public class GraphNode {
    private String id;
    private String label;

    public GraphNode() {}

    public GraphNode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}