package com.ilac.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP para comunicarse con el sistema ILAC de Interflon.
 * Encapsula toda la lógica de conexión, cookies y parsing básico de HTML.
 */
@Component
public class IlacClient {

    private static final Logger logger = LoggerFactory.getLogger(IlacClient.class);

    @Value("${ilac.base-url}")
    private String baseUrl;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT = 15000;

    /**
     * Obtener página autenticada mediante GET
     */
    public Document getPage(String url, Map<String, String> cookies) throws IOException {
        logger.debug("GET: {}", url);
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .cookies(cookies)
                .timeout(TIMEOUT)
                .ignoreContentType(true)
                .get();
    }

    /**
     * Obtener página autenticada mediante GET (URL relativa)
     */
    public Document getPage(String path, Map<String, String> cookies, boolean isRelative) throws IOException {
        String url = isRelative ? baseUrl + path : path;
        return getPage(url, cookies);
    }

    /**
     * Enviar formulario POST
     */
    public Connection.Response postForm(String url, Map<String, String> cookies,
                                         Map<String, String> formData) throws IOException {
        logger.debug("POST: {}", url);
        return Jsoup.connect(url)
                .method(Connection.Method.POST)
                .userAgent(USER_AGENT)
                .cookies(cookies)
                .data(formData)
                .timeout(TIMEOUT)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .execute();
    }

    /**
     * Enviar formulario POST con archivo
     */
    public Connection.Response postFormWithFile(String url, Map<String, String> cookies,
                                                 Map<String, String> formData,
                                                 String fileFieldName, String fileName,
                                                 byte[] fileData) throws IOException {
        logger.debug("POST (multipart): {} - Archivo: {} ({} bytes)", url, fileName, fileData.length);

        Connection connection = Jsoup.connect(url)
                .method(Connection.Method.POST)
                .userAgent(USER_AGENT)
                .cookies(cookies)
                .timeout(30000)
                .ignoreContentType(true)
                .ignoreHttpErrors(true);

        // Agregar datos del formulario
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            connection.data(entry.getKey(), entry.getValue());
        }

        // Agregar archivo
        connection.data(fileFieldName, fileName, new ByteArrayInputStream(fileData));
        connection.header("Content-Type", "multipart/form-data");

        return connection.execute();
    }

    /**
     * Obtener página de login con token CSRF
     */
    public Connection.Response getLoginPage() throws IOException {
        logger.debug("Obteniendo página de login");
        return Jsoup.connect(baseUrl)
                .method(Connection.Method.GET)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT)
                .ignoreContentType(true)
                .execute();
    }

    /**
     * Enviar credenciales de login
     */
    public Connection.Response submitLogin(Map<String, String> cookies, String identity,
                                            String credential, String csrfToken) throws IOException {
        logger.debug("Enviando credenciales para: {}", identity);

        Map<String, String> formData = Map.of(
                "identity", identity,
                "credential", credential,
                "csrf", csrfToken,
                "submit", ""
        );

        return Jsoup.connect(baseUrl)
                .method(Connection.Method.POST)
                .userAgent(USER_AGENT)
                .cookies(cookies)
                .data(formData)
                .followRedirects(true)
                .timeout(TIMEOUT)
                .ignoreHttpErrors(true)
                .execute();
    }

    /**
     * Extraer token CSRF de una página
     */
    public String extractCsrfToken(Document page) {
        Element csrfElement = page.selectFirst("input[name=csrf]");
        if (csrfElement != null) {
            return csrfElement.val();
        }
        return null;
    }

    /**
     * Extraer token CSRF de respuesta JSON con HTML escapado
     * Soporta formatos Unicode (\u0022) y escapado (\")
     */
    public String extractCsrfTokenFromJson(String html) {
        // Patrón Unicode: name=\u0022csrf\u0022 value=\u0022...\u0022
        Pattern unicodePattern = Pattern.compile("name=\\\\u0022csrf\\\\u0022\\s+value=\\\\u0022([^\\\\u0022]+)");
        Matcher unicodeMatcher = unicodePattern.matcher(html);
        if (unicodeMatcher.find()) {
            return unicodeMatcher.group(1);
        }

        // Patrón escapado: name=\"csrf\" value=\"...\"
        Pattern escapedPattern = Pattern.compile("name=\\\\\"csrf\\\\\"\\s+value=\\\\\"([^\\\\\"]+)");
        Matcher escapedMatcher = escapedPattern.matcher(html);
        if (escapedMatcher.find()) {
            return escapedMatcher.group(1);
        }

        // Patrón normal: name="csrf" value="..."
        Pattern normalPattern = Pattern.compile("name=\"csrf\"\\s+value=\"([^\"]+)");
        Matcher normalMatcher = normalPattern.matcher(html);
        if (normalMatcher.find()) {
            return normalMatcher.group(1);
        }

        return null;
    }

    /**
     * Obtener URL base
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Construir URL completa
     */
    public String buildUrl(String path) {
        if (path.startsWith("http")) {
            return path;
        }
        return baseUrl + (path.startsWith("/") ? path : "/" + path);
    }
}
