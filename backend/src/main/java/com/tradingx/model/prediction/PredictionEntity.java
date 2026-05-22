
package com.tradingx.model.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ml_prediction", indexes = {
    @Index(name = "idx_ml_prediction_date", columnList = "predict_date"),
    @Index(name = "idx_ml_prediction_ts_code", columnList = "ts_code"),
    @Index(name = "idx_ml_prediction_model_version_id", columnList = "model_version_id")})
public class PredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_id", nullable = false)
    @JsonProperty("modelVersionId")
    private Long modelVersionId;

    @Column(name = "ts_code", nullable = false, length = 20)
    @JsonProperty("tsCode")
    private String tsCode;

    @Column(name = "predict_date", nullable = false, length = 8)
    @JsonProperty("predictDate")
    private String predictDate;

    @Column(name = "current_close", precision = 12, scale = 4)
    @JsonProperty("currentClose")
    private BigDecimal currentClose;

    @Column(name = "pred_close_chg_1")
    @JsonProperty("predCloseChg1")
    private Double predCloseChg1;

    @Column(name = "pred_close_chg_2")
    @JsonProperty("predCloseChg2")
    private Double predCloseChg2;

    @Column(name = "pred_close_chg_3")
    @JsonProperty("predCloseChg3")
    private Double predCloseChg3;

    @Column(name = "pred_close_chg_4")
    @JsonProperty("predCloseChg4")
    private Double predCloseChg4;

    @Column(name = "pred_close_chg_5")
    @JsonProperty("predCloseChg5")
    private Double predCloseChg5;

    @Column(name = "pred_high_chg_1")
    @JsonProperty("predHighChg1")
    private Double predHighChg1;

    @Column(name = "pred_high_chg_2")
    @JsonProperty("predHighChg2")
    private Double predHighChg2;

    @Column(name = "pred_high_chg_3")
    @JsonProperty("predHighChg3")
    private Double predHighChg3;

    @Column(name = "pred_low_chg_1")
    @JsonProperty("predLowChg1")
    private Double predLowChg1;

    @Column(name = "pred_low_chg_2")
    @JsonProperty("predLowChg2")
    private Double predLowChg2;

    @Column(name = "pred_low_chg_3")
    @JsonProperty("predLowChg3")
    private Double predLowChg3;

    @Column(name = "confidence")
    @JsonProperty("confidence")
    private Double confidence;

    @Column(name = "confidence_up")
    @JsonProperty("confidenceUp")
    private Double confidenceUp;

    @Column(name = "confidence_flat")
    @JsonProperty("confidenceFlat")
    private Double confidenceFlat;

    @Column(name = "confidence_down")
    @JsonProperty("confidenceDown")
    private Double confidenceDown;

    @Column(name = "signal_1", length = 16)
    @JsonProperty("signal")
    private String signal;

    @Column(name = "signal_reason", length = 256)
    @JsonProperty("signalReason")
    private String signalReason;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }

    public String getPredictDate() { return predictDate; }
    public void setPredictDate(String predictDate) { this.predictDate = predictDate; }

    public BigDecimal getCurrentClose() { return currentClose; }
    public void setCurrentClose(BigDecimal currentClose) { this.currentClose = currentClose; }

    public Double getPredCloseChg1() { return predCloseChg1; }
    public void setPredCloseChg1(Double predCloseChg1) { this.predCloseChg1 = predCloseChg1; }

    public Double getPredCloseChg2() { return predCloseChg2; }
    public void setPredCloseChg2(Double predCloseChg2) { this.predCloseChg2 = predCloseChg2; }

    public Double getPredCloseChg3() { return predCloseChg3; }
    public void setPredCloseChg3(Double predCloseChg3) { this.predCloseChg3 = predCloseChg3; }

    public Double getPredCloseChg4() { return predCloseChg4; }
    public void setPredCloseChg4(Double predCloseChg4) { this.predCloseChg4 = predCloseChg4; }

    public Double getPredCloseChg5() { return predCloseChg5; }
    public void setPredCloseChg5(Double predCloseChg5) { this.predCloseChg5 = predCloseChg5; }

    public Double getPredHighChg1() { return predHighChg1; }
    public void setPredHighChg1(Double predHighChg1) { this.predHighChg1 = predHighChg1; }

    public Double getPredHighChg2() { return predHighChg2; }
    public void setPredHighChg2(Double predHighChg2) { this.predHighChg2 = predHighChg2; }

    public Double getPredHighChg3() { return predHighChg3; }
    public void setPredHighChg3(Double predHighChg3) { this.predHighChg3 = predHighChg3; }

    public Double getPredLowChg1() { return predLowChg1; }
    public void setPredLowChg1(Double predLowChg1) { this.predLowChg1 = predLowChg1; }

    public Double getPredLowChg2() { return predLowChg2; }
    public void setPredLowChg2(Double predLowChg2) { this.predLowChg2 = predLowChg2; }

    public Double getPredLowChg3() { return predLowChg3; }
    public void setPredLowChg3(Double predLowChg3) { this.predLowChg3 = predLowChg3; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Double getConfidenceUp() { return confidenceUp; }
    public void setConfidenceUp(Double confidenceUp) { this.confidenceUp = confidenceUp; }

    public Double getConfidenceFlat() { return confidenceFlat; }
    public void setConfidenceFlat(Double confidenceFlat) { this.confidenceFlat = confidenceFlat; }

    public Double getConfidenceDown() { return confidenceDown; }
    public void setConfidenceDown(Double confidenceDown) { this.confidenceDown = confidenceDown; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public String getSignalReason() { return signalReason; }
    public void setSignalReason(String signalReason) { this.signalReason = signalReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

