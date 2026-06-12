package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkOrder {
    private String id;
    private String title;
    private String tag;
    private String dueDate;
    private String taskCount;
    private String completionStatus;
    private List<Task> tasks;
}
