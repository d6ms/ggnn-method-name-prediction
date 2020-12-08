package preprocessor;

public class GraphExtractionConfig {
    public int maxVertices;
    public boolean excludeBoilerplates;

    public GraphExtractionConfig(CommandLineValues opt) {
        this.maxVertices = opt.maxVertices;
        this.excludeBoilerplates = opt.excludeBoilerplates;
    }
}
