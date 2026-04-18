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
public class Aggregations {
    @JsonProperty("date_histogram#insights_over_time")
    private DateHistogramInsightsOverTime dateHistogramInsightsOverTime;
    @JsonProperty("filters#type_counts")
    private FiltersTypeCounts filtersTypeCounts;
    @JsonProperty("sterms#category")
    private StermsCategory stermsCategory;
    @JsonProperty("simple_value#total_doc_count")
    private SimpleValueTotalDocCount simpleValueTotalDocCount;
}
