package org.example.migration.dto.aggregations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateHistogramInsightsOverTime {
    private List<Bucket> buckets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Bucket {
        private String key_as_string;
        private Long key;
        private Long doc_count;
    }
}

