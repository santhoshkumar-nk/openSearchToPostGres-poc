package org.example.migration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InsightAggregationResult {
        private String key_as_string;
        private String key;
        private Long doc_count;





    /*private String key_as_string;
    private String key;
    private Long doc_count;*/
}
