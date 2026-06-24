package com.ilac.service;

import com.ilac.model.LoginRequest;
import com.ilac.model.LoginResponse;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Value("${ilac.base-url}")
    private String baseUrl;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // Store sessions per user: key = identity, value = cookies
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    /**
     * Inner class to hold user session data
     */
    public static class UserSession {
        private final String identity;
        private final Map<String, String> cookies;
        private final long createdAt;

        public UserSession(String identity, Map<String, String> cookies) {
            this.identity = identity;
            this.cookies = cookies;
            this.createdAt = System.currentTimeMillis();
        }

        public String getIdentity() { return identity; }
        public Map<String, String> getCookies() { return cookies; }
        public long getCreatedAt() { return createdAt; }

        public boolean isExpired() {
            // Session expires after 2 hours
            return System.currentTimeMillis() - createdAt > 2 * 60 * 60 * 1000;
        }
    }

    /**
     * Login to ILAC Interflon system and store session for the user
     */
    public LoginResponse login(LoginRequest request) {
        try {
            String identity = request.getIdentity();
            logger.info("Iniciando login para usuario: {}", identity);

            // Step 1: Fetch login page to get CSRF token
            logger.debug("Obteniendo página de login de: {}", baseUrl);
            Connection.Response loginPageResponse = Jsoup.connect(baseUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .execute();

            Document loginPage = loginPageResponse.parse();
            Map<String, String> cookies = new ConcurrentHashMap<>(loginPageResponse.cookies());
            logger.debug("Cookies obtenidas de página de login: {}", cookies.keySet());

            // Extract CSRF token
            Element csrfElement = loginPage.select("input[name=csrf]").first();
            if (csrfElement == null) {
                logger.error("No se encontró token CSRF en la página de login");
                return LoginResponse.builder()
                        .success(false)
                        .message("No se pudo encontrar el token CSRF en la página de inicio de sesión")
                        .build();
            }
            String csrfToken = csrfElement.val();
            logger.debug("Token CSRF obtenido: {}...", csrfToken.substring(0, Math.min(20, csrfToken.length())));

            // Step 2: POST login credentials
            Map<String, String> formData = Map.of(
                    "identity", request.getIdentity(),
                    "credential", request.getCredential(),
                    "csrf", csrfToken,
                    "submit", ""
            );

            logger.debug("Enviando credenciales a: {}", baseUrl);
            Connection.Response loginResponse = null;
            try {
                loginResponse = Jsoup.connect(baseUrl)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .cookies(cookies)
                        .data(formData)
                        .followRedirects(true)
                        .timeout(15000)
                        .ignoreHttpErrors(true)
                        .execute();
                
                logger.debug("Respuesta de login - Status: {}", loginResponse.statusCode());
            } catch (IOException e) {
                logger.warn("Error en POST de login (continuando verificación): {}", e.getMessage());
            }

            // Merge cookies
            if (loginResponse != null) {
                cookies.putAll(loginResponse.cookies());
            }

            // Step 3: Navigate to home page to check login status
            logger.debug("Verificando estado de login en página principal");
            Connection.Response homeResponse = Jsoup.connect(baseUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .execute();

            Document homePage = homeResponse.parse();
            cookies.putAll(homeResponse.cookies());

            // Step 4: Check if we're logged in
            boolean hasLoginForm = homePage.select("#login-form").size() > 0;

            if (hasLoginForm) {
                logger.warn("Login fallido para usuario: {} - Credenciales inválidas", identity);
                return LoginResponse.builder()
                        .success(false)
                        .message("Falló inicio de sesión: Credenciales inválidas")
                        .build();
            }

            // Store session for this user
            UserSession session = new UserSession(identity, cookies);
            userSessions.put(identity, session);
            logger.info("Login exitoso para usuario: {} - Sesión almacenada", identity);

            // Extract user data
            Map<String, String> userData = extractUserData(homePage);
            logger.debug("Datos de usuario extraídos: {}", userData.keySet());

            return LoginResponse.builder()
                    .success(true)
                    .message("Inicio de sesión exitoso")
                    .userData(userData)
                    .build();

        } catch (IOException e) {
            logger.error("Error durante el inicio de sesión para usuario: {} - Error: {}", 
                    request.getIdentity(), e.getMessage(), e);
            return LoginResponse.builder()
                    .success(false)
                    .message("Error durante el inicio de sesión: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get session for a user
     */
    public UserSession getSession(String identity) {
        UserSession session = userSessions.get(identity);
        if (session != null && session.isExpired()) {
            logger.info("Sesión expirada para usuario: {}", identity);
            userSessions.remove(identity);
            return null;
        }
        return session;
    }

    /**
     * Get cookies for a user
     */
    public Map<String, String> getCookies(String identity) {
        UserSession session = getSession(identity);
        if (session == null) {
            logger.debug("No se encontró sesión activa para usuario: {}", identity);
        }
        return session != null ? session.getCookies() : null;
    }

    /**
     * Check if user has an active session
     */
    public boolean hasActiveSession(String identity) {
        return getSession(identity) != null;
    }

    /**
     * Remove session for a user
     */
    public void logout(String identity) {
        logger.info("Cerrando sesión para usuario: {}", identity);
        userSessions.remove(identity);
    }

    /**
     * Extract user information from the dashboard page
     */
    private Map<String, String> extractUserData(Document doc) {
        Map<String, String> userData = new java.util.HashMap<>();

        userData.put("pageTitle", doc.title());

        Element userInfo = doc.select(".user-info, .username, #user-name, .welcome, .dropdown-toggle").first();
        if (userInfo != null) {
            userData.put("userInfo", userInfo.text());
        }

        StringBuilder navItems = new StringBuilder();
        doc.select("nav a, .navbar a, .menu a, .sidebar a, .nav a").forEach(el -> {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                if (navItems.length() > 0) navItems.append(", ");
                navItems.append(text);
            }
        });
        if (navItems.length() > 0) {
            userData.put("navigationItems", navItems.toString());
        }

        return userData;
    }
}
