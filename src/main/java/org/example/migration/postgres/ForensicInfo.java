package org.example.migration.postgres;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "forensic_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForensicInfo implements Serializable {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "output_log", length = 1000)
    private String outputLog;

    @Column(name = "backup_name")
    private String backupName;

    @Column(name = "sys_diagnose")
    private String sysDiagnose;

    @Column(name = "device_type", length = 100)
    private String deviceType;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "zdevice_id", length = 100)
    private String zdeviceId;

    @Column(name = "checksum", length = 255)
    private String checksum;

    @Column(name = "time_trigger_analysis", nullable = false)
    private Date timeTriggerAnalysis;

    @Column(name = "time_start_analysis", nullable = false)
    private Date timeStartAnalysis;

    @Column(name = "uploaded_time", nullable = false)
    private Date uploadedTime;

    @Column(name = "location")
    private String location;

    @Column(name = "device_owner_name")
    private String deviceOwnerName;

    @Column(name = "policy_trigger_info", length = 1000)
    private String policyTriggerInfo;

    @Column(name = "investigation_file_size", length = 100)
    private String investigationFileSize;

    @Column(name = "investigation_location")
    private String investigationLocation;

    @Column(name = "device_patch_level", length = 100)
    private String devicePatchLevel;

    @Column(name = "workstation_os", length = 100)
    private String workstationOs;

    @Column(name = "workstation_usage")
    private String workstationUsage;

    @Column(name = "collector_version", length = 100)
    private String collectorVersion;

    @Column(name = "collector_usage")
    private String collectorUsage;

    @Column(name = "earliest_insight_time", nullable = false)
    private Date earliestInsightTime;

    @Column(name = "latest_insight_time", nullable = false)
    private Date latestInsightTime;

    @Column(name = "suspicious_count")
    private Long suspiciousCount;

    @Column(name = "ioc_count")
    private Long iocCount;

    @Column(name = "informational_count")
    private Long informationalCount;

    @Builder.Default
    @Column(name = "suspicious", nullable = false)
    private boolean suspicious = false;

    @Builder.Default
    @Column(name = "ioc", nullable = false)
    private boolean ioc = false;

    @Builder.Default
    @Column(name = "informational", nullable = false)
    private boolean informational = false;

    @EqualsAndHashCode.Exclude
    @JsonManagedReference
    //@JsonView(Views.WithInsights.class)
    @OneToMany(mappedBy = "forensicInfo", cascade = CascadeType.ALL, orphanRemoval = true,fetch = FetchType.LAZY)
    private List<InsightsInfo> insights;


    @JsonIgnore
    public void computeCount(){
        suspiciousCount = 0L;
        iocCount = 0L;
        informationalCount = 0L;

        if (this.insights == null || this.insights.isEmpty()) {
            return;
        }

        for (InsightsInfo insight : this.insights) {
            if(insight.getType().equalsIgnoreCase("IoC")){
                iocCount+=1;
            } else if (insight.getType().equalsIgnoreCase("Suspicious")) {
                suspiciousCount+=1;
            } else if (insight.getType().equalsIgnoreCase("informational")) {
                informationalCount+=1;
            }
        }
    }

    /*public static class Views {
        public static class Summary {}
        public static class WithInsights extends Summary {}
    }*/


}
