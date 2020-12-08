package preprocessor;

import org.kohsuke.args4j.CmdLineException;

public class App {

    public static void main(String[] args) {
        CommandLineValues opt;
        try {
            opt = new CommandLineValues(args);
        } catch (CmdLineException e) {
            e.printStackTrace();
            return;
        }

        if (opt.file != null) {
            GraphExtractionTask task = new GraphExtractionTask(opt.file.toPath());
            task.run();
        }
    }

}
