
package com.tradingx.model.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ml_trade_signal", indexes = {
    @Index(name = "idx_ml_trade_signal_date", columnList = "signal_date"),
    @Index(name = "idx_ml_trade_signal_ts_code", columnList = "ts_code"),
    @Index(name = "uk_ml_signal_model_stock_date", columnList = "model_version_id, ts_code, signal_date", unique = true)
})
public class TradeSignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_id", nullable = false)
    @JsonProperty("modelVersionId")
    private Long modelVersionId;

    @Column(name = "ts_code", nullable = false, length = 20)
    @JsonProperty("tsCode")
    private String tsCode;

    @Column(name = "signal_date", nullable = false, length = 8)
    @JsonProperty("signalDate")
    private String signalDate;

    @Column(name = "signal_1", nullable = false, length = 16)
    @JsonProperty("signal")
    private String signal;

    @Column(name = "target_price", precision = 12, scale = 4)
    @JsonProperty("targetPrice")
    private BigDecimal targetPrice;

    @Column(name = "stop_loss_price", precision = 12, scale = 4)
    @JsonProperty("stopLossPrice")
    private BigDecimal stopLossPrice;

    @Column(name = "position_ratio")
    @JsonProperty("positionRatio")
    private Double positionRatio;

    @Column(name = "confidence")
    @JsonProperty("confidence")
    private Double confidence;

    @Column(name = "reason", length = 256)
    @JsonProperty("reason")
    private String reason;

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

    public String getSignalDate() { return signalDate; }
    public void setSignalDate(String signalDate) { this.signalDate = signalDate; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }

    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(BigDecimal stopLossPrice) { this.stopLossPrice = stopLossPrice; }

    public Double getPositionRatio() { return positionRatio; }
    public void setPositionRatio(Double positionRatio) { this.positionRatio = positionRatio; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

