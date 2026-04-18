package org.example.migration.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ForensicInfoJson implements Serializable {
    public static final String ALL = "_custom_all";
    public static final String INDEX_PREFIX = "forensics";
    public static final String TYPE = "_doc";
    public static final String VERSION = "v1";
    public static final String PREVIOUS_VERSION = "v1";
    public static final String WILDCARD_INDICES = INDEX_PREFIX + "*";
    public static final String INDEX = INDEX_PREFIX + "-" + VERSION;

    @JsonProperty("id")
    private String id;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("outputLog")
    private String outputLog;

    @JsonProperty("backupName")
    private String backupName;

    @JsonProperty("sysDiagnose")
    private String sysDiagnose;

    @JsonProperty("deviceType")
    private String deviceType;

    @JsonProperty("osVersion")
    private String osVersion;

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("zdeviceId")
    private String zdeviceId;

    @JsonProperty("checksum")
    private String checksum;

    @JsonProperty("suspiciousCount")
    private Long suspiciousCount;

    @JsonProperty("iocCount")
    private Long iocCount;

    @JsonProperty("informationalCount")
    private Long informationalCount;

    @JsonProperty("created")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date created;

    @JsonProperty("insights")
    @JsonView(Views.WithInsights.class)
    private List<InsightInfo> insights;

    @JsonProperty("timeTriggerAnalysis")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date timeTriggerAnalysis;

    @JsonProperty("timeStartAnalysis")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date timeStartAnalysis;

    @JsonProperty("earliestInsightTime")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date earliestInsightTime;

    @JsonProperty("latestInsightTime")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date latestInsightTime;

    @JsonProperty("location")
    private String location;

    @JsonProperty("deviceOwnerName")
    private String deviceOwnerName;

    @JsonProperty("policyTriggerInfo")
    private String policyTriggerInfo;

    @JsonProperty("investigationFileSize")
    private String investigationFileSize;

    @JsonProperty("investigationLocation")
    private String investigationLocation;

    @JsonProperty("devicePatchLevel")
    private String devicePatchLevel;

    @JsonProperty("workstationOS")
    private String workstationOS;

    @JsonProperty("workstationUsage")
    private String workstationUsage;

    @JsonProperty("collectorVersion")
    private String collectorVersion;

    @JsonProperty("collectorUsage")
    private String collectorUsage;

    @JsonProperty("uploadedTime")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date uploadedTime;

    @JsonIgnore
    public void computeCount(){
        suspiciousCount = 0L;
        iocCount = 0L;
        informationalCount = 0L;

        for (InsightInfo insight : this.insights) {
            if(insight.getType().equalsIgnoreCase("IoC")){
                iocCount+=1;
            } else if (insight.getType().equalsIgnoreCase("Suspicious")) {
                suspiciousCount+=1;
            } else if (insight.getType().equalsIgnoreCase("informational")) {
                informationalCount+=1;
            }
        }
    }


    public static class Views {
        public static class Summary {}
        public static class WithInsights {}
    }
}
