package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaskComment {
    private String id;
    private String date;
    private String author;
    private String text;
    private String imageUrl;
    private String fileName;
}
