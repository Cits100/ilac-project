package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskActionRequest {
    private String identity;
    private String credential;
    private String taskId;
}
