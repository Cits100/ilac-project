package com.ilac.controller;

import com.ilac.model.*;
import com.ilac.service.SessionService;
import com.ilac.service.WorkOrderService;
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
        LoginResponse loginResult = sessionService.login(request);
        
        if (!loginResult.isSuccess()) {
            int status = loginResult.getMessage() != null
                    && loginResult.getMessage().contains("Invalid credentials") ? 401 : 503;
            return ResponseEntity.status(status).body(FullDashboardResponse.builder()
                    .success(false)
                    .message(loginResult.getMessage())
                    .build());
        }

        try {
            Map<String, List<WorkOrder>> workOrders = workOrderService.getFullWorkOrders(request.getIdentity());

            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(true)
                    .message("Login successful")
                    .userName(loginResult.getUserData() != null ? 
                            loginResult.getUserData().getOrDefault("userInfo", request.getIdentity()) : 
                            request.getIdentity())
                    .workOrders(workOrders)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.ok(FullDashboardResponse.builder()
                    .success(true)
                    .message("Login successful but error fetching work orders: " + e.getMessage())
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
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
            ));
        }

        // Get service ID for the task
        String serviceId = workOrderService.getServiceId(request.getTaskId(), request.getIdentity());
        if (serviceId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Could not find service ID for task"
            ));
        }

        // Decode image if provided
        byte[] imageData = null;
        String imageName = null;
        if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            try {
                imageData = Base64.getDecoder().decode(request.getImageBase64());
                imageName = request.getImageName() != null ? request.getImageName() : "image.jpg";
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid base64 image data"
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
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Comment added successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to add comment"
            ));
        }
    }

    /**
     * Mark a task as completed
     * POST /api/complete-task
     */
    @PostMapping("/complete-task")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody TaskActionRequest request) {
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.markTaskAsCompleted(request.getTaskId(), request.getIdentity());

        if (result) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Task marked as completed"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to mark task as completed"
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
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.rejectTask(
                request.getTaskId(),
                request.getReason(),
                request.getIdentity()
        );

        if (result) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Task rejected successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to reject task"
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
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
            ));
        }

        boolean result = workOrderService.acceptTask(request.getTaskId(), request.getIdentity());

        if (result) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Task accepted successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to accept task"
            ));
        }
    }
}
