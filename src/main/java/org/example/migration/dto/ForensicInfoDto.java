package org.example.migration.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForensicInfoDto {

    @JsonProperty("id")
    private String id = UUID.randomUUID().toString();

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("outputLog")
    private String outputLog;


    @JsonProperty("InvestigationBackupName")
    private String backupName;

    @JsonProperty("sysDiagnose")
    private String sysDiagnose;


    @JsonProperty("InvestigationDeviceType")
    private String deviceType;


    @JsonProperty("InvestigationOSVersion")
    private String osVersion;

    @JsonProperty("InvestigationDeviceID")
    private String deviceId;


    @JsonProperty("zdeviceID")
    private String zdeviceId;

    @JsonProperty("checksum")
    private String checksum;


    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    @JsonProperty("timeTriggerAnalysis")
    private Long timeTriggerAnalysis;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    @JsonProperty("InvestigationTimeStartAnalysis")
    private Long timeStartAnalysis;

    @JsonProperty("location")
    private String location;

    // Server generated information;
    @JsonProperty("deviceOwnerName")
    private String deviceOwnerName;

    @JsonProperty("policy_triggered")
    private String policyTriggerInfo;

    @JsonProperty("investigation_filesize")
    private String investigationFileSize;

    @JsonProperty("investigation_location")
    private String investigationLocation;

    @JsonProperty("device_patch_level")
    private String devicePatchLevel;

    @JsonProperty("workstation_OS")
    private String workstationOS;

    @JsonProperty("workspation_usage")
    private String workstationUsage;

    @JsonProperty("collector_version")
    private String collectorVersion;

    @JsonProperty("collector_usage")
    private String collectorUsage;

    @JsonProperty("InvestigationInsights")
    private List<Map<String, Object>> insights;
}

