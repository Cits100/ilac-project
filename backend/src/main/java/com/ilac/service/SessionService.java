package com.ilac.service;

import com.ilac.model.LoginRequest;
import com.ilac.model.LoginResponse;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

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

            // Step 1: Fetch login page to get CSRF token
            Connection.Response loginPageResponse = Jsoup.connect(baseUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .execute();

            Document loginPage = loginPageResponse.parse();
            Map<String, String> cookies = new ConcurrentHashMap<>(loginPageResponse.cookies());

            // Extract CSRF token
            Element csrfElement = loginPage.select("input[name=csrf]").first();
            if (csrfElement == null) {
                return LoginResponse.builder()
                        .success(false)
                        .message("Could not find CSRF token on login page")
                        .build();
            }
            String csrfToken = csrfElement.val();

            // Step 2: POST login credentials
            Map<String, String> formData = Map.of(
                    "identity", request.getIdentity(),
                    "credential", request.getCredential(),
                    "csrf", csrfToken,
                    "submit", ""
            );

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
            } catch (IOException e) {
                // Continue to verify login status
            }

            // Merge cookies
            if (loginResponse != null) {
                cookies.putAll(loginResponse.cookies());
            }

            // Step 3: Navigate to home page to check login status
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
                return LoginResponse.builder()
                        .success(false)
                        .message("Login failed: Invalid credentials")
                        .build();
            }

            // Store session for this user
            UserSession session = new UserSession(identity, cookies);
            userSessions.put(identity, session);

            // Extract user data
            Map<String, String> userData = extractUserData(homePage);

            return LoginResponse.builder()
                    .success(true)
                    .message("Login successful")
                    .userData(userData)
                    .build();

        } catch (IOException e) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Error during login: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get session for a user
     */
    public UserSession getSession(String identity) {
        UserSession session = userSessions.get(identity);
        if (session != null && session.isExpired()) {
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
