package org.example.migration.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InsightInfo implements Serializable {

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String insightId;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String category;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String type;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String description;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String ruleName;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String ruleVersion;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String intention;


    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date generatedTime;

    @Field(type = FieldType.Date, format = {})
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date originalInsightTime;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String location;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    // Server generated information;
    private String deviceOwnerName;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String policyTriggerInfo;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String sourceFileName;

    private List<String> relatedInvestigations;

    private Map<String, Object> attributeInformation;

    @Mapping(mappingPath = "elastic/text_mapping.json")
    private String accountId;

    private boolean suspicious;
    private boolean ioc;
    private boolean informational;
    private String customAll;

}
