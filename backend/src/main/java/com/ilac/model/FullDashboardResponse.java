package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FullDashboardResponse {
    private boolean success;
    private String message;
    private String sessionToken;
    private String userName;
    private Map<String, List<WorkOrder>> workOrders;
}
