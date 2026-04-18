package org.example.migration.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.migration.models.InsightInfo;
import org.example.migration.postgres.InsightsInfo;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DtoMapper {

    private static ObjectMapper objectMapper = new ObjectMapper();

    public static InsightInfo map(Map<String, Object> dto) {

        InsightInfo insightInfo = InsightInfo.builder()
                //.insightId((String) dto.get("InsightID"))
                .description((String) dto.get("InsightDescription"))
                .originalInsightTime(parseDate(dto.get("InsightTimestampOriginal")))
                .category((String) dto.get("InsightCategory"))
                .generatedTime(parseDate(dto.get("InsightTimestampGeneration")))
                .intention((String) dto.get("intention"))
                .location((String) dto.get("location"))
                .policyTriggerInfo((String) dto.get("policy_trigger_info"))
                .type((String) dto.get("InsightType"))
                .ruleName((String) dto.get("InsightRuleName"))
                .ruleVersion((String) dto.get("InsightRuleVersion"))
                .build();

        removeRedundantProperties(dto);
        insightInfo.setAttributeInformation(dto);
        return insightInfo;
    }

    public static InsightsInfo mapToPostgres(Map<String, Object> dto) {
        InsightsInfo.InsightsInfoBuilder ib = InsightsInfo.builder();
       // ib.insightId((String) dto.get("InsightID"));
        ib.description((String) dto.get("InsightDescription"));
        ib.originalInsightTime(parseDate(dto.get("InsightTimestampOriginal")));
        ib.category((String) dto.get("InsightCategory"));
        ib.generatedTime(parseDate(dto.get("InsightTimestampGeneration")));
        ib.intention((String) dto.get("intention"));
        ib.location((String) dto.get("location"));
        ib.policyTriggerInfo((String) dto.get("policy_trigger_info"));
        ib.type((String) dto.get("InsightType"));
        ib.ruleName((String) dto.get("InsightRuleName"));
        ib.ruleVersion((String) dto.get("InsightRuleVersion"));
        ib.deviceOwnerName((String) dto.get("deviceOwnerName"));
        ib.sourceFileName((String) dto.get("sourceFileName"));
        // relatedInvestigations and attributeInformation
        if (dto.get("relatedInvestigations") instanceof List) {
            ib.relatedInvestigations((List<String>) dto.get("relatedInvestigations"));
        }
        if (dto.get("attributeInformation") != null) {
            ib.attributeInformation(dto.get("attributeInformation").toString());
        } else {
            ib.attributeInformation(dto.toString()); // fallback to all fields as JSON string
        }
        return ib.build();
    }

    private static void removeRedundantProperties(Map<String, Object> dto) {
        dto.remove("InsightID");
        dto.remove("InsightDescription");
        dto.remove("InsightTimestampOriginal");
        dto.remove("InsightCategory");
        dto.remove("InsightTimestampGeneration");
        dto.remove("intention");
        dto.remove("location");
        dto.remove("policy_trigger_info");
        dto.remove("InsightType");
        dto.remove("InsightRuleName");
        dto.remove("InsightRuleVersion");
    }

    public static Date parseDate(Object key) {
        if (Objects.isNull(key)) {
            return null;
        }
        return new Date(Long.parseLong(key.toString()) * 1000);
    }
}
