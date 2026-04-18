package org.example.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.DTO.forensics.ForensicInfoDto;
import org.example.migration.postgres.ForensicInfo;
import org.example.migration.postgres.InsightsInfo;

import org.example.model.forensics.ForensicInfoJson;
import org.example.model.forensics.InsightInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", imports = {HashSet.class})
public interface ForensicsMapper{

    @Mapping(ignore = true, target = "insights")
    @Mapping(ignore = true, target = "timeTriggerAnalysis")
    @Mapping(ignore = true, target = "timeStartAnalysis")
    ForensicInfoJson toForensicInfo(ForensicInfoDto dto);


    @Mapping(ignore = true, target = "insights")
    @Mapping(ignore = true, target = "timeTriggerAnalysis")
    @Mapping(ignore = true, target = "timeStartAnalysis")
    ForensicInfo toPostGresForensicInfo(ForensicInfoDto dto);

    @Mapping(target = "insights", qualifiedByName = "mapInsights")
    ForensicInfoJson toForensicInfoJson(ForensicInfo entity);


    @Mapping(target = "attributeInformation", expression = "java(mapAttributeInformation(i.getAttributeInformation()))")
    InsightInfo toInsightInfo(InsightsInfo i);

    @Named("mapInsights")
    default List<InsightInfo> mapInsights(List<InsightsInfo> insights) {
        if (insights == null) return null;
        return insights.stream().map(this::toInsightInfo).collect(Collectors.toList());
    }

    default Map<String, Object> mapAttributeInformation(String attributeInformation) {
        if (attributeInformation == null) return null;
        try {
            return new ObjectMapper().readValue(attributeInformation, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
