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
     * Body: { "identity": "user@email.com", "credential": "password" }
     */
    @PostMapping("/dashboard")
    public ResponseEntity<FullDashboardResponse> getFullDashboard(@RequestBody LoginRequest request) {
        // Step 1: Login
        LoginResponse loginResult = sessionService.login(request);
        
        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(FullDashboardResponse.builder()
                    .success(false)
                    .message(loginResult.getMessage())
                    .build());
        }

        // Step 2: Get all work orders with details
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
                    .workOrders(Map.of("teamWorkOrders", List.of(), "myWorkOrders", List.of()))
                    .build());
        }
    }

    /**
     * Add a comment to a task
     * POST /api/comment
     * Body: { "identity": "user", "credential": "pass", "taskId": "4440612", "comment": "text", "imageBase64": "optional", "imageName": "optional" }
     */
    @PostMapping("/comment")
    public ResponseEntity<Map<String, Object>> addComment(@RequestBody CommentRequest request) {
        // Login first
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
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

        // Add comment
        boolean result = workOrderService.addComment(
                request.getTaskId(),
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
     * Body: { "identity": "user", "credential": "pass", "taskId": "4440612" }
     */
    @PostMapping("/complete-task")
    public ResponseEntity<Map<String, Object>> completeTask(@RequestBody TaskActionRequest request) {
        // Login first
        LoginRequest loginRequest = new LoginRequest(request.getIdentity(), request.getCredential());
        LoginResponse loginResult = sessionService.login(loginRequest);

        if (!loginResult.isSuccess()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Login failed: " + loginResult.getMessage()
            ));
        }

        // Mark task as completed
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
}
