package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class FactorCombinationResult {

    @JsonProperty("factor_names")
    private java.util.List<String> factorNames;

    @JsonProperty("weights")
    private Map<String, Double> weights;

    @JsonProperty("intercept")
    private double intercept;

    @JsonProperty("r_squared")
    private double rSquared;

    @JsonProperty("adj_r_squared")
    private double adjRSquared;

    @JsonProperty("t_stats")
    private Map<String, Double> tStats;

    @JsonProperty("p_values")
    private Map<String, Double> pValues;

    @JsonProperty("sample_periods")
    private int samplePeriods;

    @JsonProperty("avg_sample_size")
    private int avgSampleSize;

    @JsonProperty("factor_means")
    private Map<String, Double> factorMeans;

    @JsonProperty("factor_stds")
    private Map<String, Double> factorStds;

    @JsonProperty("entry_threshold")
    private double entryThreshold;

    @JsonProperty("exit_threshold")
    private double exitThreshold;

    public java.util.List<String> getFactorNames() { return factorNames; }
    public void setFactorNames(java.util.List<String> factorNames) { this.factorNames = factorNames; }

    public Map<String, Double> getWeights() { return weights; }
    public void setWeights(Map<String, Double> weights) { this.weights = weights; }

    public double getIntercept() { return intercept; }
    public void setIntercept(double intercept) { this.intercept = intercept; }

    public double getRSquared() { return rSquared; }
    public void setRSquared(double rSquared) { this.rSquared = rSquared; }

    public double getAdjRSquared() { return adjRSquared; }
    public void setAdjRSquared(double adjRSquared) { this.adjRSquared = adjRSquared; }

    public Map<String, Double> getTStats() { return tStats; }
    public void setTStats(Map<String, Double> tStats) { this.tStats = tStats; }

    public Map<String, Double> getPValues() { return pValues; }
    public void setPValues(Map<String, Double> pValues) { this.pValues = pValues; }

    public int getSamplePeriods() { return samplePeriods; }
    public void setSamplePeriods(int samplePeriods) { this.samplePeriods = samplePeriods; }

    public int getAvgSampleSize() { return avgSampleSize; }
    public void setAvgSampleSize(int avgSampleSize) { this.avgSampleSize = avgSampleSize; }

    public Map<String, Double> getFactorMeans() { return factorMeans; }
    public void setFactorMeans(Map<String, Double> factorMeans) { this.factorMeans = factorMeans; }

    public Map<String, Double> getFactorStds() { return factorStds; }
    public void setFactorStds(Map<String, Double> factorStds) { this.factorStds = factorStds; }

    public double getEntryThreshold() { return entryThreshold; }
    public void setEntryThreshold(double entryThreshold) { this.entryThreshold = entryThreshold; }

    public double getExitThreshold() { return exitThreshold; }
    public void setExitThreshold(double exitThreshold) { this.exitThreshold = exitThreshold; }
}
