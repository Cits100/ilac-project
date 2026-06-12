package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentRequest {
    private String identity;
    private String credential;
    private String taskId;
    private String comment;
    // Image data as base64 encoded string (optional)
    private String imageBase64;
    private String imageName;
}
