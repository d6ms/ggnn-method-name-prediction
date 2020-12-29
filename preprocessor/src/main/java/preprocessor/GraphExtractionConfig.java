package preprocessor;

public class GraphExtractionConfig {
    public int maxVertices;
    public boolean excludeBoilerplates;
    public boolean outputInPackage;

    public GraphExtractionConfig(CommandLineValues opt) {
        this.maxVertices = opt.maxVertices;
        this.excludeBoilerplates = opt.excludeBoilerplates;
        this.outputInPackage = opt.outputInPackage;
    }
}
