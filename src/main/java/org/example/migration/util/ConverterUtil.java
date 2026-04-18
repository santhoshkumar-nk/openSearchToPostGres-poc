package org.example.migration.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.example.DTO.forensics.ForensicInfoDto;
import org.example.migration.models.ForensicInfoQueryRequest;
import org.example.migration.postgres.ForensicInfo;
import org.example.migration.postgres.InsightsInfo;
import org.example.migration.models.ForensicInfoJson;
import org.example.migration.models.InsightInfo;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static org.example.migration.util.DtoMapper.parseDate;

@Service
@AllArgsConstructor
public class ConverterUtil {
    private static final Logger log = LoggerFactory.getLogger(ConverterUtil.class);

    private final ForensicsMapperPostGres forensicsMapperPostGres;


    public static ForensicInfo convertToPostgresEntity(ForensicInfoJson json) {
        ForensicInfo.ForensicInfoBuilder builder = ForensicInfo.builder();
        builder.id(json.getId());
        // builder.accountId(json.getAccountId());
        builder.accountId("09ddea50-2dda-4804-9b1c-906eacf41197");
        builder.message(json.getMessage());
        builder.outputLog(json.getOutputLog());
        builder.backupName(json.getBackupName());
        builder.sysDiagnose(json.getSysDiagnose());
        builder.deviceType(json.getDeviceType());
        builder.osVersion(json.getOsVersion());
        builder.deviceId(json.getDeviceId());
        builder.zdeviceId(json.getZdeviceId());
        builder.checksum(json.getChecksum());
        builder.suspiciousCount(json.getSuspiciousCount());
        builder.iocCount(json.getIocCount());
        builder.informationalCount(json.getInformationalCount());
        builder.timeTriggerAnalysis(json.getTimeTriggerAnalysis());
        builder.timeStartAnalysis(json.getTimeStartAnalysis());
        builder.earliestInsightTime(json.getEarliestInsightTime());
        builder.latestInsightTime(json.getLatestInsightTime());
        builder.location(json.getLocation());
        builder.deviceOwnerName(json.getDeviceOwnerName());
        builder.policyTriggerInfo(json.getPolicyTriggerInfo());
        builder.investigationFileSize(json.getInvestigationFileSize());
        builder.investigationLocation(json.getInvestigationLocation());
        builder.devicePatchLevel(json.getDevicePatchLevel());
        builder.workstationOs(json.getWorkstationOS());
        builder.workstationUsage(json.getWorkstationUsage());
        builder.collectorVersion(json.getCollectorVersion());
        builder.uploadedTime(Date.from(Instant.now()));
        builder.collectorUsage(json.getCollectorUsage());
        // Map insights
        if (json.getInsights() != null) {
            java.util.List<InsightsInfo> insightEntities = json.getInsights().stream().map(insight -> {
                InsightsInfo.InsightsInfoBuilder ib = InsightsInfo.builder();
                ib.accountId("09ddea50-2dda-4804-9b1c-906eacf41197");
                ib.category(insight.getCategory());
                ib.insightId(UUID.randomUUID().toString());
                ib.type(insight.getType());
                ib.description(insight.getDescription());
                ib.ruleName(insight.getRuleName());
                ib.ruleVersion(insight.getRuleVersion());
                ib.intention(insight.getIntention());
                ib.generatedTime(insight.getGeneratedTime());
                ib.originalInsightTime(insight.getOriginalInsightTime());
                ib.location(insight.getLocation());
                ib.deviceOwnerName(insight.getDeviceOwnerName());
                ib.policyTriggerInfo(insight.getPolicyTriggerInfo());
                ib.sourceFileName(insight.getSourceFileName());
                ib.relatedInvestigations(insight.getRelatedInvestigations());

                ObjectMapper objectMapper = new ObjectMapper();
                if (insight.getAttributeInformation() != null) {
                    String attJson = null;
                    try {
                        attJson = objectMapper.writeValueAsString(insight.getAttributeInformation());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    ib.attributeInformation(attJson); // This will be a valid JSON string
                }
                return ib.build();
            }).collect(java.util.stream.Collectors.toList());
            builder.insights(insightEntities);
        }
        return builder.build();
    }

    public ForensicInfoJson convertDto(ForensicInfoDto forensicInfoDto) {

        ForensicInfoJson forensicInfoJson = forensicsMapperPostGres.toForensicInfo(forensicInfoDto);

        forensicInfoJson.setInsights(forensicInfoDto.getInsights().stream()
                .map(DtoMapper::map)
                .collect(Collectors.toList()));

        forensicInfoJson.setEarliestInsightTime(forensicInfoJson.getInsights().stream().map(InsightInfo::getOriginalInsightTime).min(Date::compareTo).orElse(null));
        forensicInfoJson.setLatestInsightTime(forensicInfoJson.getInsights().stream().map(InsightInfo::getOriginalInsightTime).max(Date::compareTo).orElse(null));

        if(isNull(forensicInfoJson.getTimeTriggerAnalysis())){
            InsightInfo firstInfo = forensicInfoJson.getInsights().get(0);
            if(!isNull(firstInfo)){
                forensicInfoJson.setTimeTriggerAnalysis(firstInfo.getGeneratedTime());
            }
        }
        else {
            forensicInfoJson.setTimeTriggerAnalysis(parseDate(forensicInfoDto.getTimeTriggerAnalysis()));
        }

        forensicInfoJson.setTimeStartAnalysis(parseDate(forensicInfoDto.getTimeStartAnalysis()));
        return forensicInfoJson;
    }

    private Predicate filterAllSimple(Root<ForensicInfo> root, CriteriaBuilder cb, String value) {
        return cb.or(
                cb.like(root.get("id"), "%" + value + "%")
        );
    }

    Predicate filterAll(Root<ForensicInfo> root, CriteriaBuilder cb, String value) {
        String wildcardValue = "%" + value.replace("*", "%") + "%";
        return cb.or(
                cb.like(root.get("id"), wildcardValue),
                cb.like(root.get("accountId"), wildcardValue),
                cb.like(root.get("message"), wildcardValue),
                cb.like(root.get("outputLog"), wildcardValue),
                cb.like(root.get("backupName"), wildcardValue),
                cb.like(root.get("sysDiagnose"), wildcardValue),
                cb.like(root.get("deviceType"), wildcardValue),
                cb.like(root.get("osVersion"), wildcardValue),
                cb.like(root.get("deviceId"), wildcardValue),
                cb.like(root.get("zdeviceId"), wildcardValue),
                cb.like(root.get("checksum"), wildcardValue),
                cb.like(root.get("location"), wildcardValue),
                cb.like(root.get("deviceOwnerName"), wildcardValue)
           /* cb.like(root.get("policyTriggerInfo"), wildcardValue),
            cb.like(root.get("investigationFileSize").as(String.class), wildcardValue),
            cb.like(root.get("investigationLocation"), wildcardValue),
            cb.like(root.get("devicePatchLevel"), wildcardValue),
            cb.like(root.get("workstationOs"), wildcardValue),
            cb.like(root.get("workstationUsage"), wildcardValue),
            cb.like(root.get("collectorVersion"), wildcardValue),
            cb.like(root.get("collectorUsage"), wildcardValue)*/
        );
    }

    Predicate filterAllInsights(Root<InsightsInfo> root, CriteriaBuilder cb, String value) {
        String wildcardValue = "%" + value.replace("*", "%") + "%";
        return cb.or(
                cb.like(root.get("insightId"), wildcardValue),
                cb.like(root.get("customAll"), wildcardValue),
                cb.like(root.get("accountId"), wildcardValue),
                cb.like(root.get("category"), wildcardValue),
                cb.like(root.get("type"), wildcardValue),
                cb.like(root.get("description"), wildcardValue),
                cb.like(root.get("ruleName"), wildcardValue),
                cb.like(root.get("ruleVersion"), wildcardValue),
                cb.like(root.get("intention"), wildcardValue),
                cb.like(root.get("location"), wildcardValue),
                cb.like(root.get("deviceOwnerName"), wildcardValue),
                cb.like(root.get("policyTriggerInfo"), wildcardValue),
                cb.like(root.get("sourceFileName"), wildcardValue),
                //cb.like(root.get("attributeInformation"), wildcardValue)
                cb.like(root.get("attributeInformation").as(String.class), wildcardValue)

        );
    }

    public static Sort getSortFromQueryRequest(ForensicInfoQueryRequest queryRequest) {
        if (queryRequest == null || queryRequest.getSort() == null || queryRequest.getSort().isEmpty()) {
            log.info("Sort: UNSORTED (no sort values provided)");
            return Sort.unsorted();
        }
        String sortColumn = queryRequest.getSort().get(0);
        Sort.Direction direction = (queryRequest.getSort().size() > 1 && "desc".equalsIgnoreCase(queryRequest.getSort().get(1)))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        log.info("Sort values: {}", queryRequest.getSort());
        log.info("Sort column: {}", sortColumn);
        log.info("Sort direction: {}", direction);
        return Sort.by(direction, sortColumn);
    }
}
