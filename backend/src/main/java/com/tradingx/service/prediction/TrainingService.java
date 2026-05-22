package com.tradingx.service.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tradingx.model.prediction.ModelVersionEntity;
import com.tradingx.model.prediction.TrainingTaskEntity;
import com.tradingx.repository.prediction.ModelVersionRepository;
import com.tradingx.repository.prediction.TrainingTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrainingService {

    private static final Logger logger = LoggerFactory.getLogger(TrainingService.class);

    private final TrainingTaskRepository trainingTaskRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ScheduledExecutorService executorService;

    @Value("${ml.training.python-path:python}")
    private String pythonPath;

    @Value("${ml.training.script-path:ml_training/train.py}")
    private String trainScriptPath;

    @Value("${ml.training.output-path:models/}")
    private String modelOutputPath;

    @Value("${ml.training.cpu-limit:0.8}")
    private double cpuLimit;

    @Value("${ml.training.timeout-hours:400}")
    private int trainingTimeoutHours;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    private final Map<Long, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<Long, StringBuilder> activeOutputBuffers = new ConcurrentHashMap<>();

    public TrainingService(TrainingTaskRepository trainingTaskRepository,
                           ModelVersionRepository modelVersionRepository) {
        this.trainingTaskRepository = trainingTaskRepository;
        this.modelVersionRepository = modelVersionRepository;
        this.executorService = Executors.newScheduledThreadPool(2);
    }

    // ========================= 公开接口 =========================

    public TrainingTaskEntity submitTraining(TrainingRequest request) {
        TrainingTaskEntity task = new TrainingTaskEntity();
        task.setSymbol(request.getSymbol());
        task.setEpochs(request.getEpochs());
        task.setBatchSize(request.getBatchSize());
        task.setSeqLength(request.getSeqLength());
        task.setPredictionHorizon(request.getPredictionHorizon());
        task.setLearningRate(request.getLearningRate());
        task.setModelName(request.getModelName());
        task.setTrainAll(request.getTrainAll());
        task.setStatus("PENDING");
        task.setProgressPct(0.0);
        task.setStartTime(LocalDateTime.now());
        task = trainingTaskRepository.save(task);

        final Long taskId = task.getId();
        logger.info("Training task {} submitted, symbol={}", taskId, request.getSymbol());

        executorService.submit(() -> {
            try {
                runTrainingTask(taskId, request);
            } catch (Exception e) {
                logger.error("Training task {} execution error: {}", taskId, e.getMessage(), e);
                updateTaskStatus(taskId, "FAILED", e.getMessage());
            }
        });

        return task;
    }

    public List<TrainingTaskEntity> getAllTasks() {
        return trainingTaskRepository.findAllByOrderByStartTimeDesc();
    }

    public TrainingTaskEntity getTask(Long taskId) {
        return trainingTaskRepository.findById(taskId).orElse(null);
    }

    public boolean cancelTask(Long taskId) {
        Process process = activeProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            updateTaskStatus(taskId, "CANCELLED", null);
            activeProcesses.remove(taskId);
            return true;
        }
        return false;
    }

    public boolean deleteTask(Long taskId) {
        Process process = activeProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            return false;
        }
        if (trainingTaskRepository.existsById(taskId)) {
            trainingTaskRepository.deleteById(taskId);
            activeOutputBuffers.remove(taskId);
            return true;
        }
        return false;
    }

    public String getTaskOutput(Long taskId) {
        StringBuilder activeBuffer = activeOutputBuffers.get(taskId);
        if (activeBuffer != null) {
            return activeBuffer.toString();
        }
        TrainingTaskEntity task = trainingTaskRepository.findById(taskId).orElse(null);
        if (task != null && task.getOutputLog() != null) {
            return task.getOutputLog();
        }
        return "";
    }

    public List<ModelVersionEntity> getModelVersions() {
        return modelVersionRepository.findAllByOrderByCreatedAtDesc();
    }

    // ========================= 核心训练逻辑 =========================

    private void runTrainingTask(Long taskId, TrainingRequest request) {
        logger.info("Training task {} starting...", taskId);
        logger.info("Task {} params: epochs={}, batchSize={}, seqLength={}, predHorizon={}, lr={}, symbol={}, trainAll={}",
                taskId, request.getEpochs(), request.getBatchSize(), request.getSeqLength(),
                request.getPredictionHorizon(), request.getLearningRate(), request.getSymbol(), request.getTrainAll());
        Process process = null;

        try {
            updateTaskStatus(taskId, "RUNNING", null);

            List<String> command = buildCommand(request);
            String scriptDir = resolveScriptDir();
            logger.info("Training task {} command: {}", taskId, String.join(" ", command));
            logger.info("Working directory: {}", scriptDir);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(new File(scriptDir));

            process = pb.start();
            activeProcesses.put(taskId, process);
            logger.info("Training task {} process started", taskId);

            TaskOutputCollector collector = new TaskOutputCollector(process.getInputStream(), taskId);
            collector.start();

            activeOutputBuffers.put(taskId, collector.getSharedBuffer());

            // 带超时的等待
            boolean finished = process.waitFor(trainingTimeoutHours, TimeUnit.HOURS);
            collector.join(10_000);

            if (!finished) {
                logger.warn("Training task {} timed out after {} hours", taskId, trainingTimeoutHours);
                process.destroyForcibly();
                updateTaskStatus(taskId, "FAILED",
                        "Training timed out after " + trainingTimeoutHours + " hours");
                return;
            }

            int exitCode = process.exitValue();
            logger.info("Training task {} exited with code: {}", taskId, exitCode);

            if (exitCode == 0) {
                ModelVersionEntity version = new ModelVersionEntity();
                version.setModelName(request.getModelName() != null
                        ? request.getModelName() : "prediction_model");
                version.setVersion("v" + LocalDateTime.now().toString()
                        .replaceAll("[:T-]", "").substring(0, 14));
                version.setFilePath(modelOutputPath + "prediction_model.pt");
                version.setMetrics(collector.getMetrics());
                version.setCreatedAt(LocalDateTime.now());
                modelVersionRepository.save(version);

                TrainingTaskEntity task = trainingTaskRepository.findById(taskId).orElse(null);
                if (task != null) {
                    task.setStatus("COMPLETED");
                    task.setEndTime(LocalDateTime.now());
                    task.setProgressPct(100.0);
                    task.setOutputLog(collector.getOutput());
                    task.setModelVersionId(version.getId());
                    trainingTaskRepository.save(task);
                }
            } else {
                TrainingTaskEntity task = trainingTaskRepository.findById(taskId).orElse(null);
                if (task != null) {
                    task.setStatus("FAILED");
                    task.setOutputLog(collector.getOutput());
                    task.setErrorMessage("Training process exited with code: " + exitCode);
                    task.setEndTime(LocalDateTime.now());
                    trainingTaskRepository.save(task);
                }
            }

        } catch (Exception e) {
            logger.error("Training task {} failed: {}", taskId, e.getMessage(), e);
            updateTaskStatus(taskId, "FAILED", e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
                try {
                    if (!process.waitFor(3, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                    process.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
            }
            activeProcesses.remove(taskId);
            executorService.schedule(() -> activeOutputBuffers.remove(taskId), 30, TimeUnit.SECONDS);
        }
    }

    // ========================= Task 状态管理 =========================

    private void updateTaskStatus(Long taskId, String status, String errorMessage) {
        try {
            TrainingTaskEntity task = trainingTaskRepository.findById(taskId).orElse(null);
            if (task == null) return;

            task.setStatus(status);
            if (errorMessage != null) {
                task.setErrorMessage(errorMessage);
            }
            switch (status) {
                case "RUNNING" -> task.setStartTime(LocalDateTime.now());
                case "FAILED", "COMPLETED", "CANCELLED" -> task.setEndTime(LocalDateTime.now());
            }
            trainingTaskRepository.save(task);
        } catch (Exception e) {
            logger.error("Failed to update task {} status to {}: {}", taskId, status, e.getMessage());
        }
    }

    // ========================= 命令构建 =========================

    private List<String> buildCommand(TrainingRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPath);
        cmd.add(resolveScriptPath());

        cmd.add("--epochs");
        cmd.add(String.valueOf(request.getEpochs()));
        cmd.add("--batch_size");
        cmd.add(String.valueOf(request.getBatchSize()));
        cmd.add("--seq_length");
        cmd.add(String.valueOf(request.getSeqLength()));
        cmd.add("--pred_horizon");
        cmd.add(String.valueOf(request.getPredictionHorizon()));
        cmd.add("--lr");
        cmd.add(String.valueOf(request.getLearningRate()));
        cmd.add("--cpu_limit");
        cmd.add(String.valueOf(cpuLimit));

        // ========== 改动：--all 和 --symbol 互斥 ==========
        boolean isFullTraining = Boolean.TRUE.equals(request.getTrainAll());

        if (isFullTraining) {
            // 全量训练：不传 --symbol，传 --all
            cmd.add("--all");
        } else if (request.getSymbol() != null && !request.getSymbol().isBlank()) {
            // 单股训练：传 --symbol
            cmd.add("--symbol");
            cmd.add(request.getSymbol());
        }
        // ==================================================

        cmd.add("--db_host");
        cmd.add(extractDbHost());
        cmd.add("--db_port");
        cmd.add(String.valueOf(extractDbPort()));
        cmd.add("--db_user");
        cmd.add(datasourceUsername);
        cmd.add("--db_password");
        cmd.add(datasourcePassword);
        cmd.add("--db_name");
        cmd.add(extractDbName());

        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            cmd.add("--start_date");
            cmd.add(request.getStartDate());
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            cmd.add("--end_date");
            cmd.add(request.getEndDate());
        }

        return cmd;
    }


    // ========================= 路径解析 =========================

    private String resolveScriptPath() {
        Path path = Paths.get(trainScriptPath);
        if (Files.exists(path)) {
            return path.toAbsolutePath().toString();
        }

        String projectRoot = System.getProperty("user.dir");
        Path fromRoot = Paths.get(projectRoot, trainScriptPath);
        if (Files.exists(fromRoot)) {
            return fromRoot.toAbsolutePath().toString();
        }

        Path fromParent = Paths.get(projectRoot).getParent();
        if (fromParent != null) {
            Path fromParentRoot = Paths.get(fromParent.toString(), trainScriptPath);
            if (Files.exists(fromParentRoot)) {
                return fromParentRoot.toAbsolutePath().toString();
            }
        }

        return path.toAbsolutePath().toString();
    }

    private String resolveScriptDir() {
        String scriptPath = resolveScriptPath();
        File parent = new File(scriptPath).getParentFile();
        return (parent != null) ? parent.getAbsolutePath() : new File(".").getAbsolutePath();
    }

    // ========================= 数据库 URL 解析 =========================

    private String extractDbHost() {
        try {
            int start = datasourceUrl.indexOf("//") + 2;
            int end = datasourceUrl.indexOf(":", start);
            if (end == -1) end = datasourceUrl.indexOf("/", start);
            return datasourceUrl.substring(start, end);
        } catch (Exception e) {
            return "localhost";
        }
    }

    private int extractDbPort() {
        try {
            int hostStart = datasourceUrl.indexOf("//") + 2;
            int portStart = datasourceUrl.indexOf(":", hostStart) + 1;
            int portEnd = datasourceUrl.indexOf("/", portStart);
            return Integer.parseInt(datasourceUrl.substring(portStart, portEnd));
        } catch (Exception e) {
            return 3306;
        }
    }

    private String extractDbName() {
        try {
            int schemaStart = datasourceUrl.indexOf("/", datasourceUrl.indexOf("//") + 2) + 1;
            int schemaEnd = datasourceUrl.indexOf("?", schemaStart);
            if (schemaEnd == -1) schemaEnd = datasourceUrl.length();
            return datasourceUrl.substring(schemaStart, schemaEnd);
        } catch (Exception e) {
            return "tradingx";
        }
    }

    // ========================= 输出收集器 =========================

    private class TaskOutputCollector extends Thread {
        private final InputStream inputStream;
        private final StringBuilder output = new StringBuilder();
        private final StringBuilder sharedBuffer = new StringBuilder();
        private final StringBuilder metrics = new StringBuilder();
        private final Long taskId;

        private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

        private static final Pattern PROGRESS_PATTERN = Pattern.compile("PROGRESS:(\\d+\\.?\\d*)");
        private static final Pattern EPOCH_PATTERN = Pattern.compile("Epoch (\\d+)/(\\d+)");
        private static final Pattern TRAIN_LOSS_PATTERN = Pattern.compile("Train Loss: ([\\d.]+)");
        private static final Pattern VAL_LOSS_PATTERN = Pattern.compile("Val Loss: ([\\d.]+)");

        private double pendingProgress = -1;
        private int pendingEpoch = -1;
        private Double pendingTrainLoss = null;
        private Double pendingValidLoss = null;
        private long lastSaveTimeMs = 0;
        private static final long SAVE_INTERVAL_MS = 3000;

        public TaskOutputCollector(InputStream inputStream, Long taskId) {
            this.inputStream = inputStream;
            this.taskId = taskId;
            this.setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String timestamped = LocalDateTime.now().format(TS_FORMAT) + " " + line;
                    if (isImportantLine(line)) {
                        output.append(timestamped).append("\n");
                    }
                    sharedBuffer.append(timestamped).append("\n");
                    logger.info("[Task-{}] {}", taskId, line);

                    if (line.contains("Train Loss") || line.contains("Val Loss")
                            || line.contains("Epoch")) {
                        metrics.append(line).append("\n");
                    }

                    parseAndMaybeFlush(line);
                }
            } catch (IOException e) {
                output.append("Error reading output: ").append(e.getMessage());
                logger.error("[Task-{}] Error reading process output: {}", taskId, e.getMessage());
            }
        }

        private boolean isImportantLine(String line) {
            return line.contains("PROGRESS:")
                    || line.contains("Train Loss")
                    || line.contains("Val Loss")
                    || line.contains("Epoch")
                    || line.contains("Error")
                    || line.contains("Traceback")
                    || line.contains("Training complete")
                    || line.contains("Sequences shape")
                    || line.contains("Model exported")
                    || line.contains("Loaded")
                    // ====== 新增：全量训练相关日志 ======
                    || line.contains("Registry")
                    || line.contains("Feature computation")
                    || line.contains("Sequence pool")
                    || line.contains("Loading")
                    || line.contains("Batch")
                    || line.contains("direction distribution")
                    || line.contains("Memmap")
                    || line.contains("Early stopping");
        }

        private void parseAndMaybeFlush(String line) {
            boolean changed = false;

            Matcher progressMatcher = PROGRESS_PATTERN.matcher(line);
            if (progressMatcher.find()) {
                pendingProgress = Double.parseDouble(progressMatcher.group(1));
                changed = true;
            }

            Matcher epochMatcher = EPOCH_PATTERN.matcher(line);
            if (epochMatcher.find()) {
                pendingEpoch = Integer.parseInt(epochMatcher.group(1));
                changed = true;
            }

            Matcher trainLossMatcher = TRAIN_LOSS_PATTERN.matcher(line);
            if (trainLossMatcher.find()) {
                pendingTrainLoss = Double.parseDouble(trainLossMatcher.group(1));
                changed = true;
            }

            Matcher valLossMatcher = VAL_LOSS_PATTERN.matcher(line);
            if (valLossMatcher.find()) {
                pendingValidLoss = Double.parseDouble(valLossMatcher.group(1));
                changed = true;
            }

            long now = System.currentTimeMillis();
            if (changed && (now - lastSaveTimeMs > SAVE_INTERVAL_MS)) {
                try {
                    TrainingTaskEntity t = trainingTaskRepository.findById(taskId).orElse(null);
                    if (t != null) {
                        t.setProgressPct(Math.min(pendingProgress, 99.9));
                        if (pendingEpoch > 0) t.setCurrentEpoch(pendingEpoch);
                        if (pendingTrainLoss != null) t.setTrainLoss(pendingTrainLoss);
                        if (pendingValidLoss != null) t.setValidLoss(pendingValidLoss);
                        trainingTaskRepository.save(t);
                        lastSaveTimeMs = now;
                    }
                } catch (Exception e) {
                    logger.debug("[Task-{}] Failed to update progress: {}", taskId, e.getMessage());
                }
            }
        }

        public String getOutput() {
            return output.toString();
        }

        public StringBuilder getSharedBuffer() {
            return sharedBuffer;
        }

        public String getMetrics() {
            return metrics.toString();
        }
    }

    // ========================= 请求 DTO =========================

    public static class TrainingRequest {
        private String symbol;
        @JsonProperty("epochs")
        private Integer epochs = 100;
        @JsonProperty("batchSize")
        private Integer batchSize = 32;
        @JsonProperty("seqLength")
        private Integer seqLength = 60;
        @JsonProperty("predictionHorizon")
        private Integer predictionHorizon = 5;
        @JsonProperty("learningRate")
        private Double learningRate = 0.001;
        private String modelName;
        private String dbHost;
        private Integer dbPort;
        private String dbUser;
        private String dbPassword;
        private String dbName;
        private String startDate;
        private String endDate;
        @JsonProperty("trainAll")
        private Boolean trainAll = false;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public Integer getEpochs() { return epochs; }
        public void setEpochs(Integer epochs) { this.epochs = epochs; }

        public Integer getBatchSize() { return batchSize; }
        public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }

        public Integer getSeqLength() { return seqLength; }
        public void setSeqLength(Integer seqLength) { this.seqLength = seqLength; }

        public Integer getPredictionHorizon() { return predictionHorizon; }
        public void setPredictionHorizon(Integer predictionHorizon) { this.predictionHorizon = predictionHorizon; }

        public Double getLearningRate() { return learningRate; }
        public void setLearningRate(Double learningRate) { this.learningRate = learningRate; }

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }

        public String getDbHost() { return dbHost; }
        public void setDbHost(String dbHost) { this.dbHost = dbHost; }

        public Integer getDbPort() { return dbPort; }
        public void setDbPort(Integer dbPort) { this.dbPort = dbPort; }

        public String getDbUser() { return dbUser; }
        public void setDbUser(String dbUser) { this.dbUser = dbUser; }

        public String getDbPassword() { return dbPassword; }
        public void setDbPassword(String dbPassword) { this.dbPassword = dbPassword; }

        public String getDbName() { return dbName; }
        public void setDbName(String dbName) { this.dbName = dbName; }

        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }

        public Boolean getTrainAll() { return trainAll; }
        public void setTrainAll(Boolean trainAll) { this.trainAll = trainAll; }
    }
}
