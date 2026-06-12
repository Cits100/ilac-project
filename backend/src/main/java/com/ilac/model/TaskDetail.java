package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskDetail {
    private String department;
    private String location;
    private String machine;
    private String machinePart;
    private String maintenancePoint;
    private String pointCount;
    private String taskType;
    private String applicationMode;
    private String productName;
    private String productVolume;
    private java.util.List<String> images;
    private java.util.List<String> toolImages;
    private java.util.List<String> productImages;
    private java.util.List<String> safetyIcons;
}
