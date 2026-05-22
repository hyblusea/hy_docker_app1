package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class KlineDashboard {

    private MissingDataResult missingData;
    private Map<String, KlineRangeInfo> rangeMap;

    @JsonProperty("missing_data")
    public MissingDataResult getMissingData() { return missingData; }
    public void setMissingData(MissingDataResult missingData) { this.missingData = missingData; }

    @JsonProperty("range_map")
    public Map<String, KlineRangeInfo> getRangeMap() { return rangeMap; }
    public void setRangeMap(Map<String, KlineRangeInfo> rangeMap) { this.rangeMap = rangeMap; }
}
