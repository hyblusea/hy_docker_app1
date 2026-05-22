package com.tradingx.model.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 机器学习训练任务实体类
 * 记录每次模型训练的参数、状态、进度和结果
 */
@Entity
@Table(name = "ml_training_task", indexes = {
        @Index(name = "idx_ml_training_task_status", columnList = "status"),
        @Index(name = "idx_ml_training_task_symbol", columnList = "symbol")
})
public class TrainingTaskEntity {

    /** 主键ID，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 训练的股票代码，如 000001.SZ */
    @Column(name = "symbol", length = 16)
    @JsonProperty("symbol")
    private String symbol;

    /** 模型名称，如 prediction_model */
    @Column(name = "model_name", length = 64)
    @JsonProperty("modelName")
    private String modelName;

    /** 训练完成后关联的模型版本ID */
    @Column(name = "model_version_id")
    @JsonProperty("modelVersionId")
    private Long modelVersionId;

    /** 任务状态：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    @Column(name = "status", nullable = false, length = 16)
    @JsonProperty("status")
    private String status;

    /** 训练总轮数 */
    @Column(name = "epochs")
    @JsonProperty("epochs")
    private Integer epochs;

    /** 每批次样本数 */
    @Column(name = "batch_size")
    @JsonProperty("batchSize")
    private Integer batchSize;

    /** 输入序列长度（时间步数） */
    @Column(name = "seq_length")
    @JsonProperty("seqLength")
    private Integer seqLength;

    /** 预测天数（预测未来第N天的价格变动） */
    @Column(name = "prediction_horizon")
    @JsonProperty("predictionHorizon")
    private Integer predictionHorizon;

    /** 学习率 */
    @Column(name = "learning_rate")
    @JsonProperty("learningRate")
    private Double learningRate;

    /** 是否全A训练 */
    @Column(name = "train_all")
    @JsonProperty("trainAll")
    private Boolean trainAll;

    /** 当前训练到第几轮 */
    @Column(name = "current_epoch")
    @JsonProperty("currentEpoch")
    private Integer currentEpoch;

    /** 当前轮次的训练损失 */
    @Column(name = "train_loss")
    @JsonProperty("trainLoss")
    private Double trainLoss;

    /** 当前轮次的验证损失 */
    @Column(name = "valid_loss")
    @JsonProperty("validLoss")
    private Double validLoss;

    /** 历史最佳验证损失 */
    @Column(name = "best_valid_loss")
    @JsonProperty("bestValidLoss")
    private Double bestValidLoss;

    /** 训练进度百分比 0.0 ~ 100.0 */
    @Column(name = "progress_pct")
    @JsonProperty("progressPct")
    private Double progressPct;

    /** 训练过程的完整输出日志 */
    @Column(name = "output_log", columnDefinition = "LONGTEXT")
    @JsonProperty("outputLog")
    private String outputLog;

    /** 失败时的错误信息 */
    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    @JsonProperty("errorMessage")
    private String errorMessage;

    /** 任务实际开始执行时间 */
    @Column(name = "start_time")
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    /** 任务结束时间（成功/失败/取消） */
    @Column(name = "end_time")
    @JsonProperty("endTime")
    private LocalDateTime endTime;

    /** 记录创建时间，不可更新 */
    @Column(name = "created_at", updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    /** 初始化默认值 */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (progressPct == null) progressPct = 0.0;
        if (currentEpoch == null) currentEpoch = 0;
    }

    // ==================== getter/setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public Boolean getTrainAll() { return trainAll; }
    public void setTrainAll(Boolean trainAll) { this.trainAll = trainAll; }

    public Integer getCurrentEpoch() { return currentEpoch; }
    public void setCurrentEpoch(Integer currentEpoch) { this.currentEpoch = currentEpoch; }

    public Double getTrainLoss() { return trainLoss; }
    public void setTrainLoss(Double trainLoss) { this.trainLoss = trainLoss; }

    public Double getValidLoss() { return validLoss; }
    public void setValidLoss(Double validLoss) { this.validLoss = validLoss; }

    public Double getBestValidLoss() { return bestValidLoss; }
    public void setBestValidLoss(Double bestValidLoss) { this.bestValidLoss = bestValidLoss; }

    public Double getProgressPct() { return progressPct; }
    public void setProgressPct(Double progressPct) { this.progressPct = progressPct; }

    public String getOutputLog() { return outputLog; }
    public void setOutputLog(String outputLog) { this.outputLog = outputLog; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
