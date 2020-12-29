package preprocessor;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class handles the programs arguments.
 */
public class CommandLineValues {
    @Option(name = "--file", required = false)
    public File file = null;

    @Option(name = "--dataset", required = false, forbids = {"--file", "--project"})
    public Path dataset;

    @Option(name = "--project", required = false, forbids = {"--file", "--dataset"})
    public Path project;

    @Option(name = "--output_dir", required = false, forbids = "--file")
    public Path outputDir = new File("./output").toPath();

    @Option(name = "--log_dir", required = false)
    public Path logDir = Paths.get("./logs");

    @Option(name = "--num_workers", required = false)
    public int numWorkers = 1;

    @Option(name = "--max_vertices", required = false)
    public int maxVertices = 500;

    @Option(name = "--exclude_boilerplates", required = false)
    public boolean excludeBoilerplates = false;

    @Option(name = "--output_in_package", required = false)
    public boolean outputInPackage = false;

    public CommandLineValues(String... args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            throw e;
        }
    }

    public CommandLineValues() {

    }
}