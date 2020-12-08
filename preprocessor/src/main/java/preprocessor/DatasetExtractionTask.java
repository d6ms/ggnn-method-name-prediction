package preprocessor;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.*;

public class DatasetExtractionTask implements Runnable {

    private final Path targetDir;
    private final Path outputDir;
    private final int numWorkers;
    private final GraphExtractionConfig cfg;
    private final Path logDir;

    private static final Logger logger = Logger.getLogger(DatasetExtractionTask.class.getName());

    public DatasetExtractionTask(Path targetDir, Path outputDir, int numWorkers, GraphExtractionConfig cfg, Path logDir) {
        this.targetDir = targetDir;
        this.outputDir = outputDir;
        this.numWorkers = numWorkers;
        this.cfg = cfg;
        this.logDir = logDir;

        logger.setLevel(Level.INFO);
        try {
            if (!logDir.toFile().exists()) {
                logDir.toFile().mkdirs();
            }
            Handler handler = new FileHandler(logDir.resolve("preprocess.log").toString());
            handler.setFormatter(new LogFormatter());
            logger.addHandler(handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void run() {
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Map.Entry<String, List<File>> en : listProjects().entrySet()) {
                String dataType = en.getKey();
                for (File project : en.getValue()) {
                    Path outPath = outputDir.resolve(dataType).resolve(project.getName());
                    if (!outPath.toFile().exists()) {
                        outPath.toFile().mkdirs();
                    }
                    Future<?> future = executor.submit(new ProjectExtractionTask(project, outPath, cfg, logger));
                    futures.add(future);
                }
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                }
            }
        } finally {
            executor.shutdown();
        }
        logger.info("complete preprocessing all projects");
    }

    private Map<String, List<File>> listProjects() {
        Map<String, List<File>> result = new HashMap<>();
        for (File dataTypeLevel : targetDir.toFile().listFiles()) {
            if (!dataTypeLevel.isDirectory()) {
                continue;
            }
            for (File projectLevel : dataTypeLevel.listFiles()) {
                if (!projectLevel.isDirectory()) {
                    continue;
                }
                String dataType = dataTypeLevel.toPath().getFileName().toString();
                result.computeIfAbsent(dataType, k -> new ArrayList<>());
                result.get(dataType).add(projectLevel);
            }
        }
        return result;
    }
}
