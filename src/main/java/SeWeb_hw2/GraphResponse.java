package SeWeb_hw2;

import java.util.List;

/**
 * Encapsulates the entire graph to be returned as JSON.
 */
public class GraphResponse {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;

    public GraphResponse() {}

    public List<GraphNode> getNodes() { return nodes; }
    public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }

    public List<GraphEdge> getEdges() { return edges; }
    public void setEdges(List<GraphEdge> edges) { this.edges = edges; }
}