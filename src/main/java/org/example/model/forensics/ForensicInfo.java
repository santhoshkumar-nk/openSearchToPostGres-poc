package org.example.model.forensics;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.migration.models.InsightInfo;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Document(indexName = ForensicInfo.INDEX, createIndex = false)
@Setting(settingPath = "elastic/settings.json")
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForensicInfo {
    public static final String ALL = "_custom_all";
    public static final String INDEX_PREFIX = "forensics";
    public static final String TYPE = "_doc";
    public static final String VERSION = "v1";
    public static final String PREVIOUS_VERSION = "v1";
    public static final String WILDCARD_INDICES = INDEX_PREFIX + "*";
    public static final String INDEX = INDEX_PREFIX + "-" + VERSION;

    @Id
    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String id = UUID.randomUUID().toString();

    private String accountId;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String message;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String outputLog;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String backupName;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String sysDiagnose;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String deviceType;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String osVersion;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String deviceId;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String zdeviceId;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String checksum;

    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date timeTriggerAnalysis;

    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date timeStartAnalysis;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String location;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    // Server generated information;
    private String deviceOwnerName;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String policyTriggerInfo;

    private String investigationFileSize;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String investigationLocation;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String devicePatchLevel;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String workstationOS;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String workstationUsage;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String collectorVersion;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String collectorUsage;

    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date earliestInsightTime;

    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date latestInsightTime;

    @Field(type = FieldType.Nested)
    private List<InsightInfo> insights;

    @Field(type = FieldType.Long)
    private Long suspiciousCount = 0L;

    @Field(type = FieldType.Long)
    private Long iocCount = 0L;

    @Field(type = FieldType.Long)
    private Long informationalCount = 0L;

    private Date created = new Date();

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

    @JsonIgnore
    private boolean checkIfPresent(List<String> type, String value) {
        for (String str : type) {
            if (value.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

}

