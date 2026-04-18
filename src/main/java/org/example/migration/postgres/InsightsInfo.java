package org.example.migration.postgres;

import jakarta.persistence.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonBackReference;
import org.hibernate.annotations.ColumnTransformer;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "insights_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsightsInfo implements Serializable {
    @Id
    @Column(name = "insight_id", nullable = false, length = 64)
    private String insightId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forensic_info_id", nullable = false)
    @JsonBackReference
    private ForensicInfo forensicInfo;

    @Column(name = "_custom_all", length = 255)
    private String customAll;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "rule_name", length = 255)
    private String ruleName;

    @Column(name = "rule_version", length = 100)
    private String ruleVersion;

    @Column(name = "intention", length = 255)
    private String intention;

    @Column(name = "generated_time", nullable = false)
    private Date generatedTime;

    @Column(name = "original_insight_time", nullable = false)
    private Date originalInsightTime;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "device_owner_name", length = 255)
    private String deviceOwnerName;

    @Column(name = "policy_trigger_info", length = 1000)
    private String policyTriggerInfo;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "related_investigations", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    @Schema(description = "Flexible JSON data stored as jsonb")
    private List<String> relatedInvestigations;

    @Column(name = "attributeInformation", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    @Schema(description = "Flexible JSON data stored as jsonb")
    private String attributeInformation;

    @Builder.Default
    @Column(name = "suspicious", nullable = false)
    private boolean suspicious = false;

    @Builder.Default
    @Column(name = "ioc", nullable = false)
    private boolean ioc = false;

    @Builder.Default
    @Column(name = "informational", nullable = false)
    private boolean informational = false;

    // Getters and setters
}

