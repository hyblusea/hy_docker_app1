
package com.tradingx.model.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ml_model_version", indexes = {
    @Index(name = "idx_ml_model_version_name", columnList = "model_name")
})
public class ModelVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 64)
    @JsonProperty("modelName")
    private String modelName;

    @Column(name = "version", nullable = false, length = 32)
    @JsonProperty("version")
    private String version;

    @Column(name = "status", length = 16)
    @JsonProperty("status")
    private String status;

    @Column(name = "file_path", length = 512)
    @JsonProperty("filePath")
    private String filePath;

    @Column(name = "metrics", columnDefinition = "LONGTEXT")
    @JsonProperty("metrics")
    private String metrics;

    @Column(name = "engine_type", length = 32)
    @JsonProperty("engineType")
    private String engineType;

    @Column(name = "config_json", columnDefinition = "TEXT")
    @JsonProperty("configJson")
    private String configJson;

    @Column(name = "train_stock_count")
    @JsonProperty("trainStockCount")
    private Integer trainStockCount;

    @Column(name = "train_sample_count")
    @JsonProperty("trainSampleCount")
    private Long trainSampleCount;

    @Column(name = "train_epochs")
    @JsonProperty("trainEpochs")
    private Integer trainEpochs;

    @Column(name = "best_valid_loss")
    @JsonProperty("bestValidLoss")
    private Double bestValidLoss;

    @Column(name = "direction_accuracy")
    @JsonProperty("directionAccuracy")
    private Double directionAccuracy;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }

    public String getEngineType() { return engineType; }
    public void setEngineType(String engineType) { this.engineType = engineType; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public Integer getTrainStockCount() { return trainStockCount; }
    public void setTrainStockCount(Integer trainStockCount) { this.trainStockCount = trainStockCount; }

    public Long getTrainSampleCount() { return trainSampleCount; }
    public void setTrainSampleCount(Long trainSampleCount) { this.trainSampleCount = trainSampleCount; }

    public Integer getTrainEpochs() { return trainEpochs; }
    public void setTrainEpochs(Integer trainEpochs) { this.trainEpochs = trainEpochs; }

    public Double getBestValidLoss() { return bestValidLoss; }
    public void setBestValidLoss(Double bestValidLoss) { this.bestValidLoss = bestValidLoss; }

    public Double getDirectionAccuracy() { return directionAccuracy; }
    public void setDirectionAccuracy(Double directionAccuracy) { this.directionAccuracy = directionAccuracy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
