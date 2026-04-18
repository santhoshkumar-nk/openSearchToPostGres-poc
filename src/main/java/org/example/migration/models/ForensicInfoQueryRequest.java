package org.example.migration.models;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ForensicInfoQueryRequest {
    private Integer page;
    private Integer size;
    private List<String> sort;
    // Add other fields as needed
}
