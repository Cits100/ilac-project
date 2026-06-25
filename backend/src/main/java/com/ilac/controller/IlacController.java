package com.ilac.controller;

import com.ilac.model.*;
import com.ilac.service.SessionService;
import com.ilac.service.WorkOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class IlacController {

    private static final Logger logger = LoggerFactory.getLogger(IlacController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private WorkOrderService workOrderService;

    /**
     * Iniciar sesión y obtener todas las órdenes de trabajo
     * POST /api/dashboard
     * Body: { "identity": "user@email.com", "credential": "password" }
     * Retorna: token de sesión + datos de órdenes
     */
    @PostMapping("/dashboard")
    public ResponseEntity<FullDashboardResponse> getFullDashboard(@RequestBody LoginRequest request) {
        logger.info("POST /api/dashboard - Usuario: {}", request.getIdentity());
        
        LoginResponse loginResult = sessionService.login(request);
        
        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido para usuario: {} - Razón: {}", request.getIdentity(), loginResult.getMessage());
            return ResponseEntity.status(401).body(FullDashboardResponse.builder()
                    .success(false)
                    .message(loginResult.getMessage())
                    .build());
        }

        try {
            Map<String, List<WorkOrder>> workOrders = workOrderService.getFullWorkOrders(request.getIdentity());
            
            int newCount = workOrders.getOrDefault("newWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();
            int teamCount = workOrders.getOrDefault("teamWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();
            int myCount = workOrders.getOrDefault("myWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();
            
            logger.info("Login exitoso - Usuario: {} - Nuevas: {}, Equipo: {}, Propias: {}", 
                    request.getIdentity(), newCount, teamCount, myCount);

            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(true)
                    .message("Inicio de sesión exitoso")
                    .sessionToken(loginResult.getSessionToken())
                    .userName(loginResult.getUserData() != null ? 
                            loginResult.getUserData().getOrDefault("userInfo", request.getIdentity()) : 
                            request.getIdentity())
                    .workOrders(workOrders)
                    .build());
        } catch (Exception e) {
            logger.error("Error al obtener órdenes para usuario: {} - Error: {}", 
                    request.getIdentity(), e.getMessage(), e);
            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(true)
                    .message("Inicio de sesión exitoso, pero error al obtener órdenes: " + e.getMessage())
                    .sessionToken(loginResult.getSessionToken())
                    .userName(request.getIdentity())
                    .workOrders(Map.of(
                        "newWorkOrders", List.of(),
                        "teamWorkOrders", List.of(),
                        "myWorkOrders", List.of()
                    ))
                    .build());
        }
    }

    /**
     * Obtener comentarios de una tarea
     * POST /api/task-comments
     * Body: { "token": "session-token", "taskId": "123456" }
     */
    @PostMapping("/task-comments")
    public ResponseEntity<Map<String, Object>> getTaskComments(@RequestBody TokenRequest request) {
        logger.info("POST /api/task-comments - Token: {}... - Tarea: {}", 
                request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                request.getTaskId());
        
        SessionService.UserSession session = sessionService.getSessionByToken(request.getToken());
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        try {
            var comments = workOrderService.getTaskComments(request.getTaskId(), session.getIdentity());
            logger.info("Comentarios obtenidos para tarea: {} - Cantidad: {}", request.getTaskId(), comments.size());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "comments", comments
            ));
        } catch (Exception e) {
            logger.error("Error al obtener comentarios para tarea: {} - Error: {}", 
                    request.getTaskId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al obtener comentarios: " + e.getMessage()
            ));
        }
    }

    /**
     * Agregar comentario a una tarea
     * POST /api/comment
     * Body: { "token": "session-token", "taskId": "123456", "comment": "texto", "imageBase64": "opcional", "imageName": "opcional" }
     */
    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        String taskId = request.get("taskId");
        String commentText = request.get("comment");
        String imageBase64 = request.get("imageBase64");
        String imageName = request.get("imageName");
        
        logger.info("POST /api/comment - Token: {}... - Tarea: {}", 
                token.substring(0, Math.min(8, token.length())), taskId);
        
        SessionService.UserSession session = sessionService.getSessionByToken(token);
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        // Obtener service ID para la tarea
        String serviceId = workOrderService.getServiceId(taskId, session.getIdentity());
        if (serviceId == null) {
            logger.error("No se encontró serviceId para tarea: {} - Usuario: {}", taskId, session.getIdentity());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se pudo encontrar el ID de servicio para la tarea"
            ));
        }

        logger.debug("ServiceId encontrado: {} para tarea: {}", serviceId, taskId);

        // Decodificar imagen si se proporciona
        byte[] imageData = null;
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            try {
                imageData = Base64.getDecoder().decode(imageBase64);
                logger.debug("Imagen decodificada: {} ({} bytes)", imageName, imageData.length);
            } catch (Exception e) {
                logger.error("Error decodificando imagen base64 - Error: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Datos de imagen base64 inválidos"
                ));
            }
        }

        boolean result = workOrderService.addCommentWithServiceId(
                serviceId,
                commentText,
                imageData,
                imageName,
                session.getIdentity()
        );

        if (result) {
            logger.info("Comentario agregado exitosamente - Token: {}... - Tarea: {}", 
                    token.substring(0, Math.min(8, token.length())), taskId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Comentario agregado exitosamente"
            ));
        } else {
            logger.error("Error al agregar comentario - Token: {}... - Tarea: {} - ServiceId: {}", 
                    token.substring(0, Math.min(8, token.length())), taskId, serviceId);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al agregar comentario"
            ));
        }
    }

    /**
     * Editar un comentario
     * POST /api/edit-comment
     * Body: { "token": "session-token", "commentId": "123456", "comment": "nuevo texto" }
     */
    @PostMapping("/edit-comment")
    public ResponseEntity<Map<String, Object>> editComment(@RequestBody TokenRequest request) {
        logger.info("POST /api/edit-comment - Token: {}... - Comentario: {}", 
                request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                request.getCommentId());
        
        SessionService.UserSession session = sessionService.getSessionByToken(request.getToken());
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        boolean result = workOrderService.editComment(request.getCommentId(), request.getComment(), session.getIdentity());

        if (result) {
            logger.info("Comentario editado exitosamente - Token: {}... - Comentario: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getCommentId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Comentario editado exitosamente"
            ));
        } else {
            logger.error("Error al editar comentario - Token: {}... - Comentario: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getCommentId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al editar comentario"
            ));
        }
    }

    /**
     * Marcar tarea como completada
     * POST /api/complete-task
     * Body: { "token": "session-token", "taskId": "123456" }
     */
    @PostMapping("/complete-task")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody TokenRequest request) {
        logger.info("POST /api/complete-task - Token: {}... - Tarea: {}", 
                request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                request.getTaskId());
        
        SessionService.UserSession session = sessionService.getSessionByToken(request.getToken());
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        boolean result = workOrderService.markTaskAsCompleted(request.getTaskId(), session.getIdentity());

        if (result) {
            logger.info("Tarea marcada como completada - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea marcada como completada"
            ));
        } else {
            logger.error("Error al marcar tarea como completada - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al marcar tarea como completada"
            ));
        }
    }

    /**
     * Rechazar tarea con razón
     * POST /api/reject-task
     * Body: { "token": "session-token", "taskId": "123456", "reason": "motivo" }
     */
    @PostMapping("/reject-task")
    public ResponseEntity<Map<String, Object>> rejectTask(@RequestBody TokenRequest request) {
        logger.info("POST /api/reject-task - Token: {}... - Tarea: {} - Razón: {}", 
                request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                request.getTaskId(), request.getReason());
        
        SessionService.UserSession session = sessionService.getSessionByToken(request.getToken());
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        boolean result = workOrderService.rejectTask(
                request.getTaskId(),
                request.getReason(),
                session.getIdentity()
        );

        if (result) {
            logger.info("Tarea rechazada exitosamente - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea rechazada exitosamente"
            ));
        } else {
            logger.error("Error al rechazar tarea - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al rechazar tarea"
            ));
        }
    }

    /**
     * Aceptar tarea
     * POST /api/accept-task
     * Body: { "token": "session-token", "taskId": "123456" }
     */
    @PostMapping("/accept-task")
    public ResponseEntity<Map<String, Object>> acceptTask(@RequestBody TokenRequest request) {
        logger.info("POST /api/accept-task - Token: {}... - Tarea: {}", 
                request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                request.getTaskId());
        
        SessionService.UserSession session = sessionService.getSessionByToken(request.getToken());
        if (session == null) {
            logger.warn("Token inválido o expirado");
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Sesión inválida o expirada"
            ));
        }

        boolean result = workOrderService.acceptTask(request.getTaskId(), session.getIdentity());

        if (result) {
            logger.info("Tarea aceptada exitosamente - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea aceptada exitosamente"
            ));
        } else {
            logger.error("Error al aceptar tarea - Token: {}... - Tarea: {}", 
                    request.getToken().substring(0, Math.min(8, request.getToken().length())), 
                    request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al aceptar tarea"
            ));
        }
    }
}
