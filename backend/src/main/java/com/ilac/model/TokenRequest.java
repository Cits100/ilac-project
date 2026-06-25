package com.ilac.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud que usa token de sesión en lugar de credenciales
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenRequest {
    private String token;
    private String taskId;
    private String comment;
    private String reason;
    private String commentId;
}
