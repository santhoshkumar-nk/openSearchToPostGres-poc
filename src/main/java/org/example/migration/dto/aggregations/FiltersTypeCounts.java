package org.example.migration.dto.aggregations;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiltersTypeCounts {
    private Buckets buckets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Buckets {
        @JsonProperty("Abnormal")
        @Builder.Default
        private DocCount abnormal = new DocCount(0L);
        @JsonProperty("Indicator of Compromise")
        @Builder.Default
        private DocCount indicatorOfCompromise = new DocCount(0L);
        @JsonProperty("Informational")
        @Builder.Default
        private DocCount informational = new DocCount(0L);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocCount {
        private Long doc_count;
    }
}

