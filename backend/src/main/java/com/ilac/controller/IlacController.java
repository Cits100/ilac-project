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
     * Login and get all work orders with task details in one call
     * POST /api/dashboard
     * Returns: newWorkOrders, teamWorkOrders, myWorkOrders
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
     * Add a comment to a task
     * POST /api/comment
     */
    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody CommentRequest request) {
        logger.info("POST /api/comment - Usuario: {} - Tarea: {}", request.getIdentity(), request.getTaskId());
        
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido en /api/comment - Usuario: {} - Razón: {}", 
                    request.getIdentity(), loginResult.getMessage());
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Falló inicio de sesión: " + loginResult.getMessage()
            ));
        }

        // Get service ID for the task
        String serviceId = workOrderService.getServiceId(request.getTaskId(), request.getIdentity());
        if (serviceId == null) {
            logger.error("No se encontró serviceId para tarea: {} - Usuario: {}", 
                    request.getTaskId(), request.getIdentity());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No se pudo encontrar el ID de servicio para la tarea"
            ));
        }

        logger.debug("ServiceId encontrado: {} para tarea: {}", serviceId, request.getTaskId());

        // Decode image if provided
        byte[] imageData = null;
        String imageName = null;
        if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            try {
                imageData = Base64.getDecoder().decode(request.getImageBase64());
                imageName = request.getImageName() != null ? request.getImageName() : "imagen.jpg";
                logger.debug("Imagen decodificada: {} ({} bytes)", imageName, imageData.length);
            } catch (Exception e) {
                logger.error("Error decodificando imagen base64 - Usuario: {} - Error: {}", 
                        request.getIdentity(), e.getMessage());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Datos de imagen base64 inválidos"
                ));
            }
        }

        boolean result = workOrderService.addCommentWithServiceId(
                serviceId,
                request.getComment(),
                imageData,
                imageName,
                request.getIdentity()
        );

        if (result) {
            logger.info("Comentario agregado exitosamente - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Comentario agregado exitosamente"
            ));
        } else {
            logger.error("Error al agregar comentario - Usuario: {} - Tarea: {} - ServiceId: {}", 
                    request.getIdentity(), request.getTaskId(), serviceId);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al agregar comentario"
            ));
        }
    }

    /**
     * Mark a task as completed
     * POST /api/complete-task
     */
    @PostMapping("/complete-task")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody TaskActionRequest request) {
        logger.info("POST /api/complete-task - Usuario: {} - Tarea: {}", request.getIdentity(), request.getTaskId());
        
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido en /api/complete-task - Usuario: {}", request.getIdentity());
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Falló inicio de sesión: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.markTaskAsCompleted(request.getTaskId(), request.getIdentity());

        if (result) {
            logger.info("Tarea marcada como completada - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea marcada como completada"
            ));
        } else {
            logger.error("Error al marcar tarea como completada - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al marcar tarea como completada"
            ));
        }
    }

    /**
     * Reject a task with a reason
     * POST /api/reject-task
     * Body: { "identity", "credential", "taskId", "reason" }
     */
    @PostMapping("/reject-task")
    public ResponseEntity<Map<String, Object>> rejectTask(@RequestBody RejectTaskRequest request) {
        logger.info("POST /api/reject-task - Usuario: {} - Tarea: {} - Razón: {}", 
                request.getIdentity(), request.getTaskId(), request.getReason());
        
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido en /api/reject-task - Usuario: {}", request.getIdentity());
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Falló inicio de sesión: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.rejectTask(
                request.getTaskId(),
                request.getReason(),
                request.getIdentity()
        );

        if (result) {
            logger.info("Tarea rechazada exitosamente - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea rechazada exitosamente"
            ));
        } else {
            logger.error("Error al rechazar tarea - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al rechazar tarea"
            ));
        }
    }

    /**
     * Accept a task
     * POST /api/accept-task
     * Body: { "identity", "credential", "taskId" }
     */
    @PostMapping("/accept-task")
    public ResponseEntity<Map<String, Object>> acceptTask(@RequestBody TaskActionRequest request) {
        logger.info("POST /api/accept-task - Usuario: {} - Tarea: {}", request.getIdentity(), request.getTaskId());
        
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            logger.warn("Login fallido en /api/accept-task - Usuario: {}", request.getIdentity());
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Falló inicio de sesión: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.acceptTask(request.getTaskId(), request.getIdentity());

        if (result) {
            logger.info("Tarea aceptada exitosamente - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarea aceptada exitosamente"
            ));
        } else {
            logger.error("Error al aceptar tarea - Usuario: {} - Tarea: {}", 
                    request.getIdentity(), request.getTaskId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al aceptar tarea"
            ));
        }
    }
}
