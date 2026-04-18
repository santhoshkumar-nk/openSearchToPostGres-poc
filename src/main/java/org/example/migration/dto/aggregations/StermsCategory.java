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
public class StermsCategory {
    private List<Bucket> buckets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Bucket {
        private String key;
        private Long doc_count;
    }
}

