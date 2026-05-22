package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FactorEvalRequest {

    private String username;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;

    @JsonProperty("factor_names")
    private java.util.List<String> factorNames;

    @JsonProperty("forward_days")
    private int forwardDays = 5;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    @JsonProperty("end_date")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    @JsonProperty("factor_names")
    public java.util.List<String> getFactorNames() { return factorNames; }
    public void setFactorNames(java.util.List<String> factorNames) { this.factorNames = factorNames; }

    @JsonProperty("forward_days")
    public int getForwardDays() { return forwardDays; }
    public void setForwardDays(int forwardDays) { this.forwardDays = forwardDays; }
}
