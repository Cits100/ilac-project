package com.ilac.controller;

import com.ilac.config.AuthInterceptor;
import com.ilac.model.*;
import com.ilac.service.SessionService;
import com.ilac.service.WorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para el sistema ILAC.
 * 
 * La autenticación se maneja automáticamente mediante AuthInterceptor.
 * Los endpoints protegidos reciben la sesión desde el request.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class IlacController {

    private static final Logger logger = LoggerFactory.getLogger(IlacController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private WorkOrderService workOrderService;

    // ==================== ENDPOINTS PÚBLICOS ====================

    /**
     * Iniciar sesión y obtener todas las órdenes de trabajo
     * POST /api/dashboard
     * Body: { "identity": "user@email.com", "credential": "password" }
     * Retorna: token de sesión + datos de órdenes
     */
    @PostMapping("/dashboard")
    public ResponseEntity<FullDashboardResponse> getFullDashboard(@RequestBody LoginRequest request,
                                                                   HttpServletRequest httpRequest) {
        logger.info("POST /api/dashboard - Usuario: {}", request.getIdentity());

        LoginResponse loginResult = sessionService.login(request);

        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido: {} - Razón: {}", request.getIdentity(), loginResult.getMessage());
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

            logger.info("Login exitoso: {} - Nuevas: {}, Equipo: {}, Propias: {}",
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
            logger.error("Error al obtener órdenes: {} - Error: {}", request.getIdentity(), e.getMessage(), e);
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
     * Refrescar datos usando el token de sesión actual
     * POST /api/refresh
     * Header: Authorization: Bearer <token>
     * Retorna: datos actualizados de órdenes de trabajo
     */
    @PostMapping("/refresh")
    public ResponseEntity<FullDashboardResponse> refreshData(HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/refresh - Usuario: {}", session.getIdentity());

        try {
            Map<String, List<WorkOrder>> workOrders = workOrderService.getFullWorkOrders(session.getIdentity());

            int newCount = workOrders.getOrDefault("newWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();
            int teamCount = workOrders.getOrDefault("teamWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();
            int myCount = workOrders.getOrDefault("myWorkOrders", List.of()).stream()
                    .mapToInt(wo -> wo.getTasks().size()).sum();

            logger.info("Refresh exitoso: {} - Nuevas: {}, Equipo: {}, Propias: {}",
                    session.getIdentity(), newCount, teamCount, myCount);

            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(true)
                    .message("Datos actualizados")
                    .sessionToken(session.getToken())
                    .userName(session.getIdentity())
                    .workOrders(workOrders)
                    .build());
        } catch (Exception e) {
            logger.error("Error al refrescar datos: {} - Error: {}", session.getIdentity(), e.getMessage(), e);
            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(false)
                    .message("Error al actualizar datos: " + e.getMessage())
                    .sessionToken(session.getToken())
                    .userName(session.getIdentity())
                    .workOrders(Map.of(
                            "newWorkOrders", List.of(),
                            "teamWorkOrders", List.of(),
                            "myWorkOrders", List.of()
                    ))
                    .build());
        }
    }

    // ==================== ENDPOINTS PROTEGIDOS ====================
    // La autenticación se maneja automáticamente por AuthInterceptor

    /**
     * Obtener comentarios de una tarea
     * POST /api/task-comments
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456" }
     */
    @PostMapping("/task-comments")
    public ResponseEntity<Map<String, Object>> getTaskComments(@RequestBody TokenRequest request,
                                                                HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/task-comments - Usuario: {} - Tarea: {}", session.getIdentity(), request.getTaskId());

        try {
            var comments = workOrderService.getTaskComments(request.getTaskId(), session.getIdentity());
            logger.info("Comentarios obtenidos: {} - Tarea: {}", comments.size(), request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "comments", comments
            ));
        } catch (Exception e) {
            logger.error("Error al obtener comentarios: {} - Error: {}", request.getTaskId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al obtener comentarios: " + e.getMessage()
            ));
        }
    }

    /**
     * Obtener detalle completo de tarea (comentarios + estado)
     * POST /api/task-detail
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456" }
     * Retorna: comentarios + estado de la tarea
     */
    @PostMapping("/task-detail")
    public ResponseEntity<Map<String, Object>> getTaskDetail(@RequestBody TokenRequest request,
                                                              HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/task-detail - Usuario: {} - Tarea: {}", session.getIdentity(), request.getTaskId());

        try {
            // Obtener comentarios
            var comments = workOrderService.getTaskComments(request.getTaskId(), session.getIdentity());
            
            // Obtener estado de la tarea
            var taskStatus = workOrderService.getTaskStatus(request.getTaskId(), session.getIdentity());

            logger.info("Detalle obtenido - Tarea: {} - Comentarios: {} - Estado: {}", 
                    request.getTaskId(), comments.size(), taskStatus.get("status"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "taskId", request.getTaskId(),
                    "comments", comments,
                    "status", taskStatus
            ));
        } catch (Exception e) {
            logger.error("Error al obtener detalle: {} - Error: {}", request.getTaskId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al obtener detalle: " + e.getMessage()
            ));
        }
    }

    /**
     * Agregar comentario a una tarea
     * POST /api/comment
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456", "comment": "texto", "imageBase64": "opcional", "imageName": "opcional" }
     */
    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody Map<String, String> request,
                                                           HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        String taskId = request.get("taskId");
        String commentText = request.get("comment");
        String imageBase64 = request.get("imageBase64");
        String imageName = request.get("imageName");

        logger.info("POST /api/comment - Usuario: {} - Tarea: {}", session.getIdentity(), taskId);

        // Obtener service ID
        String serviceId = workOrderService.getServiceId(taskId, session.getIdentity());
        if (serviceId == null) {
            logger.error("No se encontró serviceId para tarea: {}", taskId);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se pudo encontrar el ID de servicio para la tarea"
            ));
        }

        logger.debug("ServiceId: {} para tarea: {}", serviceId, taskId);

        // Decodificar imagen si se proporciona
        byte[] imageData = null;
        if (imageBase64 != null && !imageBase64.isEmpty()) {
            try {
                imageData = Base64.getDecoder().decode(imageBase64);
                logger.debug("Imagen decodificada: {} ({} bytes)", imageName, imageData.length);
            } catch (Exception e) {
                logger.error("Error decodificando imagen: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Datos de imagen base64 inválidos"
                ));
            }
        }

        boolean result = workOrderService.addCommentWithServiceId(
                serviceId, commentText, imageData, imageName, session.getIdentity());

        if (result) {
            logger.info("Comentario agregado: {} - Tarea: {}", session.getIdentity(), taskId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Comentario agregado exitosamente"));
        } else {
            logger.error("Error al agregar comentario: {} - Tarea: {}", session.getIdentity(), taskId);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error al agregar comentario"));
        }
    }

    /**
     * Editar un comentario
     * POST /api/edit-comment
     * Header: Authorization: Bearer <token>
     * Body: { "commentId": "123456", "comment": "nuevo texto" }
     */
    @PostMapping("/edit-comment")
    public ResponseEntity<Map<String, Object>> editComment(@RequestBody TokenRequest request,
                                                            HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/edit-comment - Usuario: {} - Comentario: {}",
                session.getIdentity(), request.getCommentId());

        boolean result = workOrderService.editComment(
                request.getCommentId(), request.getComment(), session.getIdentity());

        if (result) {
            logger.info("Comentario editado: {} - Comentario: {}", session.getIdentity(), request.getCommentId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Comentario editado exitosamente"));
        } else {
            logger.error("Error al editar comentario: {} - Comentario: {}", session.getIdentity(), request.getCommentId());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error al editar comentario"));
        }
    }

    /**
     * Marcar tarea como completada
     * POST /api/complete-task
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456" }
     */
    @PostMapping("/complete-task")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody TokenRequest request,
                                                             HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/complete-task - Usuario: {} - Tarea: {}", session.getIdentity(), request.getTaskId());

        boolean result = workOrderService.markTaskAsCompleted(request.getTaskId(), session.getIdentity());

        if (result) {
            logger.info("Tarea completada: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarea marcada como completada"));
        } else {
            logger.error("Error al completar tarea: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error al marcar tarea como completada"));
        }
    }

    /**
     * Rechazar tarea con razón
     * POST /api/reject-task
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456", "reason": "motivo" }
     */
    @PostMapping("/reject-task")
    public ResponseEntity<Map<String, Object>> rejectTask(@RequestBody TokenRequest request,
                                                           HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/reject-task - Usuario: {} - Tarea: {} - Razón: {}",
                session.getIdentity(), request.getTaskId(), request.getReason());

        boolean result = workOrderService.rejectTask(
                request.getTaskId(), request.getReason(), session.getIdentity());

        if (result) {
            logger.info("Tarea rechazada: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarea rechazada exitosamente"));
        } else {
            logger.error("Error al rechazar tarea: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error al rechazar tarea"));
        }
    }

    /**
     * Aceptar tarea
     * POST /api/accept-task
     * Header: Authorization: Bearer <token>
     * Body: { "taskId": "123456" }
     */
    @PostMapping("/accept-task")
    public ResponseEntity<Map<String, Object>> acceptTask(@RequestBody TokenRequest request,
                                                           HttpServletRequest httpRequest) {
        SessionService.UserSession session = getSessionFromRequest(httpRequest);
        logger.info("POST /api/accept-task - Usuario: {} - Tarea: {}", session.getIdentity(), request.getTaskId());

        boolean result = workOrderService.acceptTask(request.getTaskId(), session.getIdentity());

        if (result) {
            logger.info("Tarea aceptada: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Tarea aceptada exitosamente"));
        } else {
            logger.error("Error al aceptar tarea: {} - Tarea: {}", session.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error al aceptar tarea"));
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

    /**
     * Obtener sesión del request (inyectada por AuthInterceptor)
     */
    private SessionService.UserSession getSessionFromRequest(HttpServletRequest request) {
        SessionService.UserSession session = AuthInterceptor.getSessionFromRequest(request);
        if (session == null) {
            throw new RuntimeException("Sesión no encontrada en el request");
        }
        return session;
    }
}
