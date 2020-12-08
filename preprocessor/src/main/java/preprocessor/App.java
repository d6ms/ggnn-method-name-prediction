package preprocessor;

import org.kohsuke.args4j.CmdLineException;

import java.util.List;

public class App {

    public static void main(String[] args) {
        CommandLineValues opt;
        try {
            opt = new CommandLineValues(args);
        } catch (CmdLineException e) {
            e.printStackTrace();
            return;
        }
        GraphExtractionConfig cfg = new GraphExtractionConfig(opt);

        if (opt.file != null) {
            FileExtractionTask task = new FileExtractionTask(opt.file.toPath(), cfg);
            try {
                List<Graph> graphs = task.call();
                for (Graph graph : graphs) {
                    System.out.println(graph);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (opt.project != null) {
            ProjectExtractionTask task = new ProjectExtractionTask(opt.project.toFile(), opt.outputDir, cfg);
            task.run();
        }
    }

}
