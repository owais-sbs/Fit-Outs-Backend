package com.fitouts.shared.api;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

public abstract class BaseController {

    protected ResponseEntity<?> successResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("isSuccess", true);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    protected ResponseEntity<?> successResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("isSuccess", true);
        response.put("message", message);
        response.put("data", data);
        return ResponseEntity.ok(response);
    }

    protected ResponseEntity<?> failureResponse(String message, String error) {
        Map<String, Object> response = new HashMap<>();
        response.put("isSuccess", false);
        response.put("message", message);
        response.put("error", error);
        return ResponseEntity.badRequest().body(response);
    }
}
