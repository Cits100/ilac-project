package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    private String id;
    private String orderNumber;
    private String status;
    private String dueDate;
    private String dispatchType;
    private String location;
    private String department;
    private String machine;
    private String machinePart;
    private String title;
    private String description;
    private String product;
    private String assignedTo;
    private String detailUrl;
    private TaskDetail detail;
}
