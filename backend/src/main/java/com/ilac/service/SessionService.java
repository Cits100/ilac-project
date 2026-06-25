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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    @Value("${ilac.base-url}")
    private String baseUrl;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final long SESSION_DURATION = 2 * 60 * 60 * 1000; // 2 hours

    // Token -> UserSession mapping
    private final Map<String, UserSession> sessionsByToken = new ConcurrentHashMap<>();
    
    // Identity -> Token mapping (for quick lookup)
    private final Map<String, String> tokenByIdentity = new ConcurrentHashMap<>();

    /**
     * Clase interna para datos de sesión de usuario
     */
    public static class UserSession {
        private final String identity;
        private final String token;
        private final Map<String, String> cookies;
        private final long createdAt;

        public UserSession(String identity, String token, Map<String, String> cookies) {
            this.identity = identity;
            this.token = token;
            this.cookies = cookies;
            this.createdAt = System.currentTimeMillis();
        }

        public String getIdentity() { return identity; }
        public String getToken() { return token; }
        public Map<String, String> getCookies() { return cookies; }
        public long getCreatedAt() { return createdAt; }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SESSION_DURATION;
        }
    }

    /**
     * Iniciar sesión en el sistema ILAC Interflon y devolver token de sesión
     */
    public LoginResponse login(LoginRequest request) {
        try {
            String identity = request.getIdentity();
            logger.info("Iniciando login para usuario: {}", identity);

            // Paso 1: Obtener página de login para CSRF token
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

            // Extraer token CSRF
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

            // Paso 2: Enviar credenciales
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

            // Combinar cookies
            if (loginResponse != null) {
                cookies.putAll(loginResponse.cookies());
            }

            // Paso 3: Navegar a página principal para verificar login
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

            // Paso 4: Verificar si estamos logueados
            boolean hasLoginForm = homePage.select("#login-form").size() > 0;

            if (hasLoginForm) {
                logger.warn("Login fallido para usuario: {} - Credenciales inválidas", identity);
                return LoginResponse.builder()
                        .success(false)
                        .message("Falló inicio de sesión: Credenciales inválidas")
                        .build();
            }

            // Generar token de sesión
            String sessionToken = UUID.randomUUID().toString();
            
            // Eliminar sesión anterior si existe
            String oldToken = tokenByIdentity.get(identity);
            if (oldToken != null) {
                sessionsByToken.remove(oldToken);
                logger.debug("Sesión anterior eliminada para usuario: {}", identity);
            }
            
            // Almacenar nueva sesión
            UserSession session = new UserSession(identity, sessionToken, cookies);
            sessionsByToken.put(sessionToken, session);
            tokenByIdentity.put(identity, sessionToken);
            
            logger.info("Login exitoso para usuario: {} - Token generado: {}...", 
                    identity, sessionToken.substring(0, 8));

            // Extraer datos de usuario
            Map<String, String> userData = extractUserData(homePage);
            logger.debug("Datos de usuario extraídos: {}", userData.keySet());

            return LoginResponse.builder()
                    .success(true)
                    .message("Inicio de sesión exitoso")
                    .sessionToken(sessionToken)
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
     * Obtener sesión por token
     */
    public UserSession getSessionByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        UserSession session = sessionsByToken.get(token);
        if (session != null && session.isExpired()) {
            logger.info("Sesión expirada para token: {}...", token.substring(0, Math.min(8, token.length())));
            sessionsByToken.remove(token);
            tokenByIdentity.remove(session.getIdentity());
            return null;
        }
        return session;
    }

    /**
     * Obtener cookies por token
     */
    public Map<String, String> getCookiesByToken(String token) {
        UserSession session = getSessionByToken(token);
        if (session == null) {
            logger.debug("No se encontró sesión para token: {}...", 
                    token != null ? token.substring(0, Math.min(8, token.length())) : "null");
            return null;
        }
        return session.getCookies();
    }

    /**
     * Verificar si un token es válido
     */
    public boolean isTokenValid(String token) {
        return getSessionByToken(token) != null;
    }

    /**
     * Obtener cookies por identidad de usuario
     * Usado por WorkOrderService que ya tiene la identidad
     */
    public Map<String, String> getCookiesByIdentity(String identity) {
        String token = tokenByIdentity.get(identity);
        if (token == null) {
            return null;
        }
        UserSession session = sessionsByToken.get(token);
        if (session == null || session.isExpired()) {
            return null;
        }
        return session.getCookies();
    }

    /**
     * Cerrar sesión por token
     */
    public void logout(String token) {
        UserSession session = sessionsByToken.remove(token);
        if (session != null) {
            tokenByIdentity.remove(session.getIdentity());
            logger.info("Sesión cerrada para usuario: {}", session.getIdentity());
        }
    }

    /**
     * Extraer información del usuario de la página principal
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
