package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FactorDefinition {

    @JsonProperty("factor_name")
    private String factorName;

    @JsonProperty("factor_label")
    private String factorLabel;

    @JsonProperty("factor_category")
    private String factorCategory;

    @JsonProperty("important")
    private boolean important;

    @JsonProperty("factor_name")
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }

    @JsonProperty("factor_label")
    public String getFactorLabel() { return factorLabel; }
    public void setFactorLabel(String factorLabel) { this.factorLabel = factorLabel; }

    @JsonProperty("factor_category")
    public String getFactorCategory() { return factorCategory; }
    public void setFactorCategory(String factorCategory) { this.factorCategory = factorCategory; }

    @JsonProperty("important")
    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}
