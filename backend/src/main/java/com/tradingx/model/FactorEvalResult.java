package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FactorEvalResult {

    private String factorName;
    private String factorLabel;
    private String factorCategory;
    private double icMean;
    private double icStd;
    private double icir;
    private double icWinRate;
    private double coverage;
    private String layerReturnsJson;
    private double pearsonIcMean;
    private double pearsonIcir;

    @JsonProperty("factor_name")
    public String getFactorName() { return factorName; }
    @JsonProperty("factor_name")
    public void setFactorName(String factorName) { this.factorName = factorName; }

    @JsonProperty("factor_label")
    public String getFactorLabel() { return factorLabel; }
    @JsonProperty("factor_label")
    public void setFactorLabel(String factorLabel) { this.factorLabel = factorLabel; }

    @JsonProperty("factor_category")
    public String getFactorCategory() { return factorCategory; }
    @JsonProperty("factor_category")
    public void setFactorCategory(String factorCategory) { this.factorCategory = factorCategory; }

    @JsonProperty("ic_mean")
    public double getIcMean() { return icMean; }
    @JsonProperty("ic_mean")
    public void setIcMean(double icMean) { this.icMean = icMean; }

    @JsonProperty("ic_std")
    public double getIcStd() { return icStd; }
    @JsonProperty("ic_std")
    public void setIcStd(double icStd) { this.icStd = icStd; }

    @JsonProperty("icir")
    public double getIcir() { return icir; }
    @JsonProperty("icir")
    public void setIcir(double icir) { this.icir = icir; }

    @JsonProperty("ic_win_rate")
    public double getIcWinRate() { return icWinRate; }
    @JsonProperty("ic_win_rate")
    public void setIcWinRate(double icWinRate) { this.icWinRate = icWinRate; }

    @JsonProperty("coverage")
    public double getCoverage() { return coverage; }
    @JsonProperty("coverage")
    public void setCoverage(double coverage) { this.coverage = coverage; }

    @JsonProperty("layer_returns_json")
    public String getLayerReturnsJson() { return layerReturnsJson; }
    @JsonProperty("layer_returns_json")
    public void setLayerReturnsJson(String layerReturnsJson) { this.layerReturnsJson = layerReturnsJson; }

    @JsonProperty("pearson_ic_mean")
    public double getPearsonIcMean() { return pearsonIcMean; }
    @JsonProperty("pearson_ic_mean")
    public void setPearsonIcMean(double pearsonIcMean) { this.pearsonIcMean = pearsonIcMean; }

    @JsonProperty("pearson_icir")
    public double getPearsonIcir() { return pearsonIcir; }
    @JsonProperty("pearson_icir")
    public void setPearsonIcir(double pearsonIcir) { this.pearsonIcir = pearsonIcir; }
}
