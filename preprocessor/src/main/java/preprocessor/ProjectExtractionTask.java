package preprocessor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ProjectExtractionTask implements Runnable {

    private final File projectDir;
    private final Path outPath;
    private final GraphExtractionConfig cfg;

    public ProjectExtractionTask(File projectDir, Path outPath, GraphExtractionConfig cfg) {
        this.projectDir = projectDir;
        this.outPath = outPath;
        this.cfg = cfg;
    }

    @Override
    public void run() {
        String projectName = projectDir.getName();
        File graphFile = outPath.resolve(projectName + ".graph").toFile();
        File vocabFile = outPath.resolve(projectName + ".vocab").toFile();

        WordHistogram hist = new WordHistogram();
        try (FileWriter gw = new FileWriter(graphFile);
             FileWriter vw = new FileWriter(vocabFile)) {
            Files.walk(projectDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().contains("Test"))
                    .flatMap(path -> {
                        FileExtractionTask task = new FileExtractionTask(path, cfg);
                        List<Graph> graphs = new ArrayList<>();
                        try {
                            graphs = task.call();
                        } catch (Exception | StackOverflowError e) {  // 型の解決時にStackOverflowになることがある
//                            logger.warning("failed to extract file: " + path);
                        }
                        return graphs.stream();
                    })
                    .forEach(g -> {
                        g.getVertices().stream()
                                .map(Graph.Vertex::getLabel)
                                .flatMap(s -> Stream.of(s.split("\\|")))
                                .forEach(hist::count);
                        try {
                            gw.write(g.toString() + "\n\n");
                        } catch (IOException e) {
//                            logger.warning("failed to process file: " + outFile);
                            throw new UncheckedIOException(e);
                        }
                    });
            vw.write(hist.toString());
            System.out.println("complete preprocessing " + projectDir);
        } catch (Exception e) {
            System.err.println("failed to process project: " + projectDir);
            e.printStackTrace(System.err);
        }
    }
}

