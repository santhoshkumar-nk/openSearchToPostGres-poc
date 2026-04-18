package org.example.migration.dto.aggregations;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregationRoot {
    private Aggregations aggregations;
}
