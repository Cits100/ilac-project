package com.ilac.config;

import com.ilac.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor para autenticación y logging de todas las peticiones.
 * 
 * Funcionalidades:
 * 1. Registra TODAS las peticiones entrantes (método, URI, IP, User-Agent)
 * 2. Valida token de sesión para endpoints protegidos
 * 3. Inyecta la sesión en el request para uso del controller
 * 4. Registra tiempo de respuesta
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);
    private static final String SESSION_ATTR = "userSession";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SessionService sessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String remoteAddr = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Log de TODAS las peticiones
        logger.info("REQUEST | {} {} | IP: {} | UA: {}",
                method, uri, remoteAddr,
                userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "N/A");

        // Endpoint público - no requiere autenticación
        if (isPublicEndpoint(uri)) {
            logger.debug("Endpoint público: {} {}", method, uri);
            return true;
        }

        // Extraer token del request
        String token = extractToken(request);

        if (token == null || token.isEmpty()) {
            logger.warn("AUTH FAIL | {} {} | Sin token de sesión", method, uri);
            sendUnauthorizedResponse(response, "Token de sesión requerido");
            return false;
        }

        // Validar token
        SessionService.UserSession session = sessionService.getSessionByToken(token);

        if (session == null) {
            logger.warn("AUTH FAIL | {} {} | Token inválido o expirado: {}...",
                    method, uri, token.substring(0, Math.min(8, token.length())));
            sendUnauthorizedResponse(response, "Sesión inválida o expirada");
            return false;
        }

        // Inyectar sesión en el request
        request.setAttribute(SESSION_ATTR, session);

        logger.info("AUTH OK | {} {} | Usuario: {} | Token: {}...",
                method, uri, session.getIdentity(),
                token.substring(0, Math.min(8, token.length())));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        long startTime = (Long) request.getAttribute("startTime");
        long duration = System.currentTimeMillis() - startTime;

        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        logger.info("RESPONSE | {} {} | Status: {} | Duración: {}ms",
                method, uri, status, duration);

        if (ex != null) {
            logger.error("ERROR | {} {} | Excepción: {}", method, uri, ex.getMessage());
        }
    }

    /**
     * Verificar si el endpoint es público (no requiere autenticación)
     */
    private boolean isPublicEndpoint(String uri) {
        return uri.equals("/api/dashboard") ||
               uri.equals("/api/auth/login") ||
               uri.startsWith("/h2-console") ||
               uri.equals("/error");
    }

    /**
     * Extraer token del request (header o body)
     */
    private String extractToken(HttpServletRequest request) {
        // Intentar del header primero
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }

        // Intentar del header X-Session-Token
        token = request.getHeader("X-Session-Token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // Nota: El token del body se extrae en el controller
        // porque el interceptor no puede leer el body múltiples veces
        return null;
    }

    /**
     * Obtener IP real del cliente (considerando proxies)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Si hay múltiples IPs, tomar la primera
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * Enviar respuesta 401 Unauthorized
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message)
            throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    /**
     * Obtener sesión inyectada en el request
     */
    public static SessionService.UserSession getSessionFromRequest(HttpServletRequest request) {
        return (SessionService.UserSession) request.getAttribute(SESSION_ATTR);
    }
}
