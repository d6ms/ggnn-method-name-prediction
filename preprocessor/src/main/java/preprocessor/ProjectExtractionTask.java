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
import java.util.logging.*;
import java.util.stream.Stream;

public class ProjectExtractionTask implements Runnable {

    private final File projectDir;
    private final Path outPath;
    private final GraphExtractionConfig cfg;
    private Logger logger;

    public ProjectExtractionTask(File projectDir, Path outPath, GraphExtractionConfig cfg, Logger logger) {
        this(projectDir, outPath, cfg);
        this.logger = logger;
    }

    public ProjectExtractionTask(File projectDir, Path outPath, GraphExtractionConfig cfg) {
        this.projectDir = projectDir;
        this.outPath = outPath;
        this.cfg = cfg;
        Logger logger = Logger.getLogger(DatasetExtractionTask.class.getName());
        logger.setLevel(Level.INFO);
        Handler handler = new StreamHandler(System.out, new LogFormatter());
        handler.setFormatter(new LogFormatter());
        logger.addHandler(handler);
        this.logger = logger;
    }

    private class GraphWriter implements AutoCloseable {

        private FileWriter fw;
        private Map<String, Integer> indices = new HashMap<>();

        public void write(String content, String packageName) throws IOException {
            if (cfg.outputInPackage) {
                int index = indices.getOrDefault(packageName, 0);
                index++;

                Path graphFile = outPath.resolve(packageName).resolve(index + ".txt");
                if (!graphFile.getParent().toFile().exists()) {
                    graphFile.getParent().toFile().mkdirs();
                }

                try (var lfw = new FileWriter(graphFile.toFile())) {
                    lfw.write(content);
                }

                indices.put(packageName, index);
            } else {
                if (fw == null) {
                    String projectName = projectDir.getName();
                    File graphFile = outPath.resolve(projectName + ".graph").toFile();
                    fw = new FileWriter(graphFile);
                }
                fw.write(content + "\n\n");
            }
        }

        @Override
        public void close() throws Exception {
            if (fw != null) {
                fw.close();
            }
        }
    }

    @Override
    public void run() {
        String projectName = projectDir.getName();
        File vocabFile = outPath.resolve(projectName + ".vocab").toFile();
        File targetFile = outPath.resolve(projectName + ".target").toFile();

        WordHistogram vocabHist = new WordHistogram();
        WordHistogram targetHist = new WordHistogram();
        try (GraphWriter gw = new GraphWriter();
             FileWriter vw = new FileWriter(vocabFile);
             FileWriter tw = new FileWriter(targetFile)) {
            Files.walk(projectDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().contains("Test"))
                    .flatMap(path -> {
                        FileExtractionTask task = new FileExtractionTask(path, cfg);
                        List<Graph> graphs = new ArrayList<>();
                        try {
                            graphs = task.call();
                        } catch (Exception | StackOverflowError e) {  // 型の解決時にStackOverflowになることがある
                            logger.warning("failed to extract file: " + path);
                        }
                        return graphs.stream();
                    })
                    .forEach(g -> {
                        g.getVertices().stream()
                                .map(Graph.Vertex::getLabel)
                                .flatMap(s -> Stream.of(s.split("\\|")))
                                .forEach(vocabHist::count);
                        targetHist.count(g.getMethodName());
                        try {
                            gw.write(GraphPrinter.print(g), g.getPackageName());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            vw.write(vocabHist.toString());
            tw.write(targetHist.toString());
            logger.info("complete preprocessing " + projectDir);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failed to process project: " + projectDir, e);
        }
    }
}

