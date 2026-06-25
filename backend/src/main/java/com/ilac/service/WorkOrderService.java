package com.ilac.service;

import com.ilac.model.TaskDetail;
import com.ilac.model.WorkOrder;
import com.ilac.model.Task;
import com.ilac.model.TaskComment;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de lógica de negocio para órdenes de trabajo.
 * 
 * Responsabilidades:
 * - Obtener y parsear órdenes de trabajo (nuevas, equipo, propias)
 * - Obtener y parsear tareas
 * - Obtener y parsear comentarios
 * - Agregar/editar comentarios
 * - Marcar tareas como completadas
 * - Aceptar/rechazar tareas
 * 
 * NOTA: No maneja comunicación HTTP directamente - usa IlacClient
 */
@Service
public class WorkOrderService {

    private static final Logger logger = LoggerFactory.getLogger(WorkOrderService.class);

    @Autowired
    private IlacClient ilacClient;

    @Autowired
    private SessionService sessionService;

    // ==================== MÉTODOS PÚBLICOS ====================

    /**
     * Obtener todas las órdenes de trabajo (nuevas, equipo, propias)
     */
    public Map<String, List<WorkOrder>> getAllWorkOrders(String identity) {
        try {
            logger.info("Obteniendo todas las órdenes de trabajo para usuario: {}", identity);
            Map<String, String> cookies = getCookiesForUser(identity);
            Document doc = ilacClient.getPage(ilacClient.getBaseUrl(), cookies);

            Map<String, List<WorkOrder>> result = new HashMap<>();

            // Parsear órdenes nuevas (pending-accordion)
            List<WorkOrder> newOrders = parsePendingWorkOrders(doc);
            result.put("newWorkOrders", newOrders);
            logger.debug("Órdenes nuevas encontradas: {}", newOrders.size());

            // Parsear órdenes de equipo (team-accordion)
            List<WorkOrder> teamOrders = parseWorkOrderSection(doc, "#team-accordion");
            result.put("teamWorkOrders", teamOrders);
            logger.debug("Órdenes de equipo encontradas: {}", teamOrders.size());

            // Parsear órdenes propias (work-orders-accordion)
            List<WorkOrder> myOrders = parseWorkOrderSection(doc, "#work-orders-accordion");
            result.put("myWorkOrders", myOrders);
            logger.debug("Órdenes propias encontradas: {}", myOrders.size());

            int totalTasks = newOrders.stream().mapToInt(wo -> wo.getTasks().size()).sum()
                    + teamOrders.stream().mapToInt(wo -> wo.getTasks().size()).sum()
                    + myOrders.stream().mapToInt(wo -> wo.getTasks().size()).sum();
            logger.info("Total: nuevas={}, equipo={}, propias={}, tareas={}",
                    newOrders.size(), teamOrders.size(), myOrders.size(), totalTasks);

            return result;
        } catch (Exception e) {
            logger.error("Error al obtener órdenes: {} - Error: {}", identity, e.getMessage(), e);
            return Map.of(
                    "newWorkOrders", Collections.emptyList(),
                    "teamWorkOrders", Collections.emptyList(),
                    "myWorkOrders", Collections.emptyList()
            );
        }
    }

    /**
     * Obtener todas las órdenes con detalles de tareas
     */
    public Map<String, List<WorkOrder>> getFullWorkOrders(String identity) {
        Map<String, List<WorkOrder>> allOrders = getAllWorkOrders(identity);

        // Enriquecer órdenes de equipo
        for (WorkOrder wo : allOrders.getOrDefault("teamWorkOrders", Collections.emptyList())) {
            enrichWorkOrderWithDetails(wo, identity);
        }

        // Enriquecer órdenes propias
        for (WorkOrder wo : allOrders.getOrDefault("myWorkOrders", Collections.emptyList())) {
            enrichWorkOrderWithDetails(wo, identity);
        }

        return allOrders;
    }

    /**
     * Obtener comentarios de una tarea
     */
    public List<TaskComment> getTaskComments(String taskId, String identity) {
        try {
            logger.info("Obteniendo comentarios para tarea: {} - Usuario: {}", taskId, identity);
            Map<String, String> cookies = getCookiesForUser(identity);
            String taskUrl = ilacClient.buildUrl("/engineer-task-detail-" + taskId);
            Document doc = ilacClient.getPage(taskUrl, cookies);

            List<TaskComment> comments = new ArrayList<>();

            // Buscar sección de comentarios
            Element commentList = doc.selectFirst("#comment-list");
            if (commentList == null) {
                logger.debug("No se encontró lista de comentarios");
                return comments;
            }

            // Parsear cada comentario
            Elements commentElements = commentList.select(".well.well-sm");
            for (Element commentEl : commentElements) {
                TaskComment comment = parseCommentElement(commentEl);
                if (comment != null) {
                    comments.add(comment);
                }
            }

            logger.info("Comentarios encontrados: {} - Tarea: {}", comments.size(), taskId);
            return comments;
        } catch (Exception e) {
            logger.error("Error al obtener comentarios: {} - Error: {}", taskId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Obtener service ID para comentarios
     */
    public String getServiceId(String taskId, String identity) {
        try {
            Map<String, String> cookies = getCookiesForUser(identity);
            String taskUrl = ilacClient.buildUrl("/engineer-task-detail-" + taskId);
            Document doc = ilacClient.getPage(taskUrl, cookies);

            Element commentLink = doc.selectFirst("a[href*=service-remark-create]");
            if (commentLink != null) {
                String href = commentLink.attr("href");
                return href.replace("/service-remark-create-", "").trim();
            }

            return null;
        } catch (Exception e) {
            logger.error("Error al obtener serviceId: {} - Error: {}", taskId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Agregar comentario a una tarea
     */
    public boolean addCommentWithServiceId(String serviceId, String commentText,
                                            byte[] imageData, String imageName, String identity) {
        try {
            logger.info("Agregando comentario - ServiceId: {} - Usuario: {} - Imagen: {}",
                    serviceId, identity, imageData != null);

            Map<String, String> cookies = getCookiesForUser(identity);
            String formUrl = ilacClient.buildUrl("/service-remark-create-" + serviceId);

            // Obtener formulario para CSRF token
            Document doc = ilacClient.getPage(formUrl, cookies);
            String html = doc.html();
            String csrfToken = ilacClient.extractCsrfTokenFromJson(html);

            if (csrfToken == null || csrfToken.isEmpty()) {
                logger.warn("No se encontró token CSRF para comentario - ServiceId: {}", serviceId);
                csrfToken = "";
            }

            // Preparar datos del formulario
            Map<String, String> formData = new HashMap<>();
            formData.put("csrf", csrfToken);
            formData.put("serviceRemark[text]", commentText);
            formData.put("serviceRemark[id]", "");
            formData.put("submit", "Añadir comentario");

            // Enviar formulario
            Connection.Response response;
            if (imageData != null && imageName != null && !imageName.isEmpty()) {
                response = ilacClient.postFormWithFile(formUrl, cookies, formData,
                        "serviceRemark[file][fileInfo]", imageName, imageData);
            } else {
                response = ilacClient.postForm(formUrl, cookies, formData);
            }

            boolean success = response.statusCode() == 200 || response.statusCode() == 302;
            if (success) {
                logger.info("Comentario agregado - ServiceId: {} - Status: {}", serviceId, response.statusCode());
            } else {
                logger.error("Error al agregar comentario - ServiceId: {} - Status: {}", serviceId, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            logger.error("Excepción al agregar comentario: {} - Error: {}", serviceId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Editar un comentario existente
     */
    public boolean editComment(String commentId, String newText, String identity) {
        try {
            logger.info("Editando comentario: {} - Usuario: {}", commentId, identity);

            Map<String, String> cookies = getCookiesForUser(identity);
            String editUrl = ilacClient.buildUrl("/service-remark-edit-" + commentId);

            // Obtener página de edición para CSRF token
            Document doc = ilacClient.getPage(editUrl, cookies);
            String html = doc.html();
            String csrfToken = ilacClient.extractCsrfTokenFromJson(html);

            if (csrfToken == null || csrfToken.isEmpty()) {
                logger.warn("No se encontró token CSRF para edición: {}", commentId);
                csrfToken = "";
            }

            // Enviar formulario
            Map<String, String> formData = Map.of(
                    "csrf", csrfToken,
                    "serviceRemark[text]", newText,
                    "serviceRemark[id]", commentId,
                    "submit", "Guardar"
            );

            Connection.Response response = ilacClient.postForm(editUrl, cookies, formData);

            boolean success = response.statusCode() == 200 || response.statusCode() == 302;
            if (success) {
                logger.info("Comentario editado: {} - Status: {}", commentId, response.statusCode());
            } else {
                logger.error("Error al editar comentario: {} - Status: {}", commentId, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            logger.error("Excepción al editar comentario: {} - Error: {}", commentId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Marcar tarea como completada
     */
    public boolean markTaskAsCompleted(String taskId, String identity) {
        try {
            logger.info("Marcando tarea como completada: {} - Usuario: {}", taskId, identity);

            Map<String, String> cookies = getCookiesForUser(identity);
            String taskUrl = ilacClient.buildUrl("/engineer-task-detail-" + taskId);

            // Obtener página de tarea
            Document doc = ilacClient.getPage(taskUrl, cookies);

            // Buscar enlace "Marcar como realizada" con data-values
            Element markDoneLink = findMarkDoneLink(doc, taskId);

            if (markDoneLink == null) {
                logger.error("No se encontró enlace 'Marcar como realizada' para tarea: {}", taskId);
                return false;
            }

            String href = markDoneLink.attr("href");
            String fullUrl = ilacClient.buildUrl(href);

            // Obtener CSRF token
            String csrfToken = ilacClient.extractCsrfToken(doc);
            if (csrfToken == null) csrfToken = "";

            // Enviar POST con taskId
            Map<String, String> formData = Map.of(
                    "job", taskId,
                    "csrf", csrfToken
            );

            Connection.Response response = ilacClient.postForm(fullUrl, cookies, formData);

            boolean success = response.statusCode() == 200 || response.statusCode() == 302;
            if (success) {
                logger.info("Tarea completada: {} - Status: {}", taskId, response.statusCode());
            } else {
                logger.error("Error al completar tarea: {} - Status: {}", taskId, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            logger.error("Excepción al completar tarea: {} - Error: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Aceptar tarea
     */
    public boolean acceptTask(String taskId, String identity) {
        try {
            logger.info("Aceptando tarea: {} - Usuario: {}", taskId, identity);

            Map<String, String> cookies = getCookiesForUser(identity);
            String acceptUrl = ilacClient.buildUrl("/jobs-accept-" + taskId);

            Connection.Response response = ilacClient.postForm(acceptUrl, cookies, Map.of());

            boolean success = response.statusCode() == 200 || response.statusCode() == 302;
            if (success) {
                logger.info("Tarea aceptada: {} - Status: {}", taskId, response.statusCode());
            } else {
                logger.error("Error al aceptar tarea: {} - Status: {}", taskId, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            logger.error("Excepción al aceptar tarea: {} - Error: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Rechazar tarea con razón
     */
    public boolean rejectTask(String taskId, String reason, String identity) {
        try {
            logger.info("Rechazando tarea: {} - Usuario: {} - Razón: {}", taskId, identity, reason);

            Map<String, String> cookies = getCookiesForUser(identity);
            String rejectUrl = ilacClient.buildUrl("/jobs-reject-" + taskId);

            // Obtener página para CSRF token
            Document doc = ilacClient.getPage(rejectUrl, cookies);
            String html = doc.html();
            String csrfToken = ilacClient.extractCsrfTokenFromJson(html);

            if (csrfToken == null || csrfToken.isEmpty()) {
                logger.warn("No se encontró token CSRF para rechazo: {}", taskId);
                csrfToken = "";
            }

            // Enviar formulario
            Map<String, String> formData = Map.of(
                    "description", reason,
                    "csrf", csrfToken,
                    "submit", "Guardar"
            );

            Connection.Response response = ilacClient.postForm(rejectUrl, cookies, formData);

            boolean success = response.statusCode() == 200 || response.statusCode() == 302;
            if (success) {
                logger.info("Tarea rechazada: {} - Status: {}", taskId, response.statusCode());
            } else {
                logger.error("Error al rechazar tarea: {} - Status: {}", taskId, response.statusCode());
            }
            return success;
        } catch (Exception e) {
            logger.error("Excepción al rechazar tarea: {} - Error: {}", taskId, e.getMessage(), e);
            return false;
        }
    }

    // ==================== MÉTODOS PRIVADOS ====================

    /**
     * Obtener cookies para un usuario
     */
    private Map<String, String> getCookiesForUser(String identity) {
        Map<String, String> cookies = sessionService.getCookiesByIdentity(identity);
        if (cookies == null) {
            logger.error("No hay sesión activa para usuario: {}", identity);
            throw new RuntimeException("No hay sesión activa. Inicie sesión primero.");
        }
        return cookies;
    }

    /**
     * Enriquecer orden de trabajo con detalles de tareas
     */
    private void enrichWorkOrderWithDetails(WorkOrder wo, String identity) {
        if (wo.getTasks() != null) {
            for (Task task : wo.getTasks()) {
                if (task.getDetailUrl() != null && !task.getDetailUrl().isEmpty()) {
                    TaskDetail detail = getTaskDetail(task.getDetailUrl(), identity);
                    task.setDetail(detail);
                }
            }
        }
    }

    /**
     * Obtener detalle de una tarea
     */
    private TaskDetail getTaskDetail(String taskUrl, String identity) {
        try {
            Map<String, String> cookies = getCookiesForUser(identity);
            String fullUrl = ilacClient.buildUrl(taskUrl);
            Document doc = ilacClient.getPage(fullUrl, cookies);

            TaskDetail detail = new TaskDetail();

            // Parsear tabla de detalles
            Element table = doc.selectFirst("#task-detail-maintenance-point table");
            if (table != null) {
                parseDetailTable(table, detail);
            }

            // Parsear tipo de tarea y modo de aplicación
            Element taskTypeEl = doc.selectFirst(".task-detail-task-name p");
            detail.setTaskType(taskTypeEl != null ? taskTypeEl.text().trim() : "");

            Element appModeEl = doc.selectFirst(".task-detail-tool-info p");
            detail.setApplicationMode(appModeEl != null ? appModeEl.text().trim() : "");

            // Parsear producto
            Element productNameEl = doc.selectFirst(".task-detail-product-info span");
            detail.setProductName(productNameEl != null ? productNameEl.text().trim() : "");

            Element volumeEl = doc.selectFirst(".task-detail-product-info p:nth-child(3)");
            detail.setProductVolume(volumeEl != null ? volumeEl.text().trim() : "");

            // Listas vacías para imágenes (no se usan)
            detail.setImages(Collections.emptyList());
            detail.setToolImages(Collections.emptyList());
            detail.setProductImages(Collections.emptyList());
            detail.setSafetyIcons(Collections.emptyList());

            return detail;
        } catch (Exception e) {
            logger.error("Error al obtener detalle: {} - Error: {}", taskUrl, e.getMessage(), e);
            return TaskDetail.builder()
                    .images(Collections.emptyList())
                    .toolImages(Collections.emptyList())
                    .productImages(Collections.emptyList())
                    .safetyIcons(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Parsear tabla de detalles
     */
    private void parseDetailTable(Element table, TaskDetail detail) {
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th != null && td != null) {
                String label = th.text().trim().replace(":", "");
                String value = td.text().trim();

                switch (label) {
                    case "Departamento": detail.setDepartment(value); break;
                    case "Ubicación": detail.setLocation(value); break;
                    case "Máquina": detail.setMachine(value); break;
                    case "Parte de máquina": detail.setMachinePart(value); break;
                    case "Punto de mantenimiento": detail.setMaintenancePoint(value); break;
                    case "Cantidad de puntos": detail.setPointCount(value); break;
                }
            }
        }
    }

    /**
     * Buscar enlace "Marcar como realizada"
     */
    private Element findMarkDoneLink(Document doc, String taskId) {
        // Buscar por clase CSS y data-values
        Elements candidates = doc.select("a.job-set-status");
        for (Element link : candidates) {
            String dataValues = link.attr("data-values");
            if (dataValues.equals(taskId)) {
                return link;
            }
        }

        // Buscar por texto
        for (Element link : doc.select("a")) {
            String text = link.text().trim();
            String dataValues = link.attr("data-values");
            if (text.contains("Marcar como realizada") && dataValues.equals(taskId)) {
                return link;
            }
        }

        return null;
    }

    // ==================== PARSING DE ÓRDENES ====================

    /**
     * Parsear órdenes nuevas (pending-accordion)
     */
    private List<WorkOrder> parsePendingWorkOrders(Document doc) {
        List<WorkOrder> workOrders = new ArrayList<>();

        Element section = doc.selectFirst("#pending-accordion");
        if (section == null) {
            return workOrders;
        }

        Elements panels = section.select("> .panel.panel-default");
        for (Element panel : panels) {
            WorkOrder wo = parsePendingWorkOrderPanel(panel);
            if (wo != null) {
                workOrders.add(wo);
            }
        }

        return workOrders;
    }

    /**
     * Parsear panel de orden nueva
     */
    private WorkOrder parsePendingWorkOrderPanel(Element panel) {
        try {
            Element heading = panel.selectFirst(".panel-heading");
            if (heading == null) return null;

            Element tagEl = heading.selectFirst(".work-order-tag");
            String tag = tagEl != null ? tagEl.text().trim() : "";
            String id = tag.replace("#", "");

            if (tag.isEmpty() || id.isEmpty()) return null;

            Element titleEl = heading.selectFirst(".work-order-title");
            String title = titleEl != null ? titleEl.text().trim() : "";

            // Extraer fecha y duración
            String dueDate = "";
            String duration = "";
            Elements dateSpans = heading.select(".text-success");
            for (Element span : dateSpans) {
                String text = span.text();
                if (text.contains("vencimiento:")) {
                    Element strong = span.selectFirst("strong");
                    dueDate = strong != null ? strong.text().trim() : "";
                }
                if (text.contains("Duración total:")) {
                    Element strong = span.selectFirst("strong");
                    duration = strong != null ? strong.text().trim() : "";
                }
            }

            // Extraer conteo de tareas
            String taskCount = "";
            Element badge = heading.selectFirst(".label-info");
            if (badge != null) {
                taskCount = badge.text().replace("Tasks:", "").trim();
            }

            // URLs de aceptar/rechazar
            String acceptTagUrl = "";
            String rejectTagUrl = "";
            Element acceptLink = heading.selectFirst("a.accept[href*=accept-tag]");
            Element rejectLink = heading.selectFirst("a.reject[href*=reject-tag]");
            if (acceptLink != null) acceptTagUrl = acceptLink.attr("href");
            if (rejectLink != null) rejectTagUrl = rejectLink.attr("href");

            // Parsear tareas
            List<Task> tasks = new ArrayList<>();
            Element body = panel.selectFirst(".panel-body.work-orders-tasks");
            if (body != null) {
                Elements taskPanels = body.select(".task");
                for (Element taskPanel : taskPanels) {
                    Task task = parsePendingTaskPanel(taskPanel);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }

            return WorkOrder.builder()
                    .id(id)
                    .title(title)
                    .tag(tag)
                    .dueDate(dueDate)
                    .taskCount(taskCount)
                    .completionStatus(duration.isEmpty() ? "" : "Duración: " + duration)
                    .tasks(tasks)
                    .acceptTagUrl(acceptTagUrl)
                    .rejectTagUrl(rejectTagUrl)
                    .build();
        } catch (Exception e) {
            logger.error("Error al parsear orden nueva: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsear tarea nueva (con botones Aceptar/Rechazar)
     */
    private Task parsePendingTaskPanel(Element taskPanel) {
        try {
            String panelId = taskPanel.attr("id");
            String taskId = panelId.replace("-task", "");

            Element header = taskPanel.selectFirst(".panel-heading");
            String orderNumber = "";
            String dueDate = "";
            String dispatchType = "";

            if (header != null) {
                Element orderNum = header.selectFirst(".task-order-number");
                orderNumber = orderNum != null ? orderNum.text().trim() : "";

                Element dueDateEl = header.selectFirst(".task-order-due");
                dueDate = dueDateEl != null ? dueDateEl.text().replace("vencimiento:", "").trim() : "";

                Element dispatchEl = header.selectFirst(".task-order-auto");
                dispatchType = dispatchEl != null ? dispatchEl.text().trim() : "";
            }

            Element body = taskPanel.selectFirst(".panel-body");
            String location = "", department = "", machine = "", machinePart = "";
            String title = "", description = "", product = "";

            if (body != null) {
                Element locationEl = body.selectFirst(".task-location div");
                if (locationEl != null) {
                    String[] parts = locationEl.text().trim().split("/");
                    if (parts.length >= 1) location = parts[0].trim();
                    if (parts.length >= 2) department = parts[1].trim();
                }

                Element machineEl = body.selectFirst(".task-machine div");
                if (machineEl != null) {
                    String[] parts = machineEl.text().trim().split("/");
                    if (parts.length >= 1) machine = parts[0].trim();
                    if (parts.length >= 2) machinePart = parts[1].trim();
                }

                Element titleEl = body.selectFirst(".task-order-title");
                title = titleEl != null ? titleEl.text().trim() : "";

                Element descEl = body.selectFirst(".task-title-box p");
                if (descEl != null) {
                    String descText = descEl.text().trim();
                    if (descText.startsWith("(") && descText.endsWith(")")) {
                        description = descText.substring(1, descText.length() - 1);
                    }
                }

                Element productEl = body.selectFirst(".fa-oil-can + strong");
                product = productEl != null ? productEl.text().trim() : "";
            }

            // URLs de aceptar/rechazar
            String acceptUrl = "";
            String rejectUrl = "";
            Element footer = taskPanel.selectFirst(".panel-footer");
            if (footer != null) {
                Element acceptLink = footer.selectFirst("a.accept");
                Element rejectLink = footer.selectFirst("a.reject");
                if (acceptLink != null) acceptUrl = acceptLink.attr("href");
                if (rejectLink != null) rejectUrl = rejectLink.attr("href");
            }

            return Task.builder()
                    .id(taskId)
                    .orderNumber(orderNumber)
                    .status("pending")
                    .dueDate(dueDate)
                    .dispatchType(dispatchType)
                    .location(location)
                    .department(department)
                    .machine(machine)
                    .machinePart(machinePart)
                    .title(title)
                    .description(description)
                    .product(product)
                    .assignedTo("")
                    .detailUrl("")
                    .acceptUrl(acceptUrl)
                    .rejectUrl(rejectUrl)
                    .build();
        } catch (Exception e) {
            logger.error("Error al parsear tarea nueva: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsear sección de órdenes (team o work-orders)
     */
    private List<WorkOrder> parseWorkOrderSection(Document doc, String sectionSelector) {
        List<WorkOrder> workOrders = new ArrayList<>();

        Element section = doc.selectFirst(sectionSelector);
        if (section == null) return workOrders;

        // Verificar si hay mensaje "no hay tareas"
        Element noTasks = section.selectFirst(".text-center");
        if (noTasks != null && noTasks.text().contains("no hay tareas")) {
            return workOrders;
        }

        // Parsear paneles
        Elements panels = section.select(".panel.panel-default");
        for (Element panel : panels) {
            WorkOrder wo = parseWorkOrderPanel(panel);
            if (wo != null) {
                workOrders.add(wo);
            }
        }

        return workOrders;
    }

    /**
     * Parsear panel de orden de trabajo
     */
    private WorkOrder parseWorkOrderPanel(Element panel) {
        try {
            Element heading = panel.selectFirst(".panel-heading");
            if (heading == null) return null;

            Element tagEl = heading.selectFirst(".work-order-tag");
            String tag = tagEl != null ? tagEl.text().trim() : "";
            String id = tag.replace("#", "");

            if (tag.isEmpty() || id.isEmpty()) return null;

            Element titleEl = heading.selectFirst(".work-order-title");
            String title = titleEl != null ? titleEl.text().trim() : "";

            Element dueDateEl = heading.selectFirst(".text-success strong, .text-danger strong");
            String dueDate = dueDateEl != null ? dueDateEl.text().trim() : "";

            String taskCount = "";
            String completionStatus = "";
            Element buttonsDiv = heading.selectFirst(".work-order-buttons");
            if (buttonsDiv != null) {
                Element badge = buttonsDiv.selectFirst(".label-info");
                taskCount = badge != null ? badge.text().replace("Tasks:", "").trim() : "";

                String fullText = buttonsDiv.text();
                if (fullText.contains("hecho")) {
                    completionStatus = fullText.substring(0, fullText.indexOf("hecho")).trim() + " hecho";
                }
            }

            // Parsear tareas
            List<Task> tasks = new ArrayList<>();
            Element body = panel.selectFirst(".panel-body.work-orders-tasks");
            if (body != null) {
                Elements taskPanels = body.select(".task");
                for (Element taskPanel : taskPanels) {
                    Task task = parseTaskPanel(taskPanel);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }

            return WorkOrder.builder()
                    .id(id)
                    .title(title)
                    .tag(tag)
                    .dueDate(dueDate)
                    .taskCount(taskCount)
                    .completionStatus(completionStatus)
                    .tasks(tasks)
                    .build();
        } catch (Exception e) {
            logger.error("Error al parsear orden: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsear panel de tarea
     */
    private Task parseTaskPanel(Element taskPanel) {
        try {
            String panelId = taskPanel.attr("id");
            String taskId = panelId.replace("-task", "");

            Element dataHref = taskPanel.selectFirst("[data-href]");
            String detailUrl = dataHref != null ? dataHref.attr("data-href") : "";

            Element header = taskPanel.selectFirst(".panel-heading");
            String orderNumber = "", status = "", dueDate = "", dispatchType = "";

            if (header != null) {
                Element orderNum = header.selectFirst(".task-order-number");
                orderNumber = orderNum != null ? orderNum.text().trim() : "";
                status = "Completado".equals(orderNumber) ? "completed" : "pending";

                Element dueDateEl = header.selectFirst(".task-order-due");
                dueDate = dueDateEl != null ? dueDateEl.text().replace("vencimiento:", "").trim() : "";

                Element dispatchEl = header.selectFirst(".task-order-auto");
                dispatchType = dispatchEl != null ? dispatchEl.text().trim() : "";
            }

            Element body = taskPanel.selectFirst(".panel-body");
            String location = "", department = "", machine = "", machinePart = "";
            String title = "", description = "", product = "";

            if (body != null) {
                Element locationEl = body.selectFirst(".task-location div");
                if (locationEl != null) {
                    String[] parts = locationEl.text().trim().split("/");
                    if (parts.length >= 1) location = parts[0].trim();
                    if (parts.length >= 2) department = parts[1].trim();
                }

                Element machineEl = body.selectFirst(".task-machine div");
                if (machineEl != null) {
                    String[] parts = machineEl.text().trim().split("/");
                    if (parts.length >= 1) machine = parts[0].trim();
                    if (parts.length >= 2) machinePart = parts[1].trim();
                }

                Element titleEl = body.selectFirst(".task-order-title");
                title = titleEl != null ? titleEl.text().trim() : "";

                Element descEl = body.selectFirst(".task-title-box p");
                if (descEl != null) {
                    String descText = descEl.text().trim();
                    if (descText.startsWith("(") && descText.endsWith(")")) {
                        description = descText.substring(1, descText.length() - 1);
                    }
                }

                Element productEl = body.selectFirst(".fa-oil-can + strong");
                product = productEl != null ? productEl.text().trim() : "";
            }

            // Assigned user
            String assignedTo = "";
            Element footer = taskPanel.selectFirst(".panel-footer");
            if (footer != null) {
                Element userEl = footer.selectFirst(".fa-user");
                if (userEl != null && userEl.parent() != null) {
                    assignedTo = userEl.parent().text().replace(userEl.text(), "").trim();
                }
            }

            return Task.builder()
                    .id(taskId)
                    .orderNumber(orderNumber)
                    .status(status)
                    .dueDate(dueDate)
                    .dispatchType(dispatchType)
                    .location(location)
                    .department(department)
                    .machine(machine)
                    .machinePart(machinePart)
                    .title(title)
                    .description(description)
                    .product(product)
                    .assignedTo(assignedTo)
                    .detailUrl(detailUrl)
                    .build();
        } catch (Exception e) {
            logger.error("Error al parsear tarea: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parsear elemento de comentario
     */
    private TaskComment parseCommentElement(Element commentEl) {
        try {
            Element metaParagraph = commentEl.selectFirst("p");
            if (metaParagraph == null) return null;

            String metaText = metaParagraph.text();

            // Extraer fecha
            String date = "";
            Pattern datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
            Matcher dateMatcher = datePattern.matcher(metaText);
            if (dateMatcher.find()) date = dateMatcher.group(1);

            // Extraer hora
            String time = "";
            Pattern timePattern = Pattern.compile("(\\d{2}:\\d{2})");
            Matcher timeMatcher = timePattern.matcher(metaText);
            if (timeMatcher.find()) time = timeMatcher.group(1);

            String dateTime = date + (time.isEmpty() ? "" : " " + time);

            // Extraer autor
            String author = "";
            Element userIcon = metaParagraph.selectFirst(".glyphicon-user");
            if (userIcon != null) {
                String iconHtml = userIcon.outerHtml();
                int iconIdx = metaParagraph.html().indexOf(iconHtml);
                if (iconIdx >= 0) {
                    String afterIcon = metaParagraph.html().substring(iconIdx + iconHtml.length());
                    afterIcon = afterIcon.replaceAll("<[^>]+>", " ").trim();
                    int imageIdx = afterIcon.indexOf("Imagen");
                    if (imageIdx > 0) afterIcon = afterIcon.substring(0, imageIdx);
                    int pictureIdx = afterIcon.indexOf("glyphicon-picture");
                    if (pictureIdx > 0) afterIcon = afterIcon.substring(0, pictureIdx);
                    author = afterIcon.trim();
                }
            }

            // Extraer texto
            String text = "";
            Elements paragraphs = commentEl.select("p");
            if (paragraphs.size() > 1) {
                String fullText = paragraphs.get(1).text();
                if (fullText.contains("Comentarios del parte de mantenimiento:")) {
                    text = fullText.substring(fullText.indexOf("Comentarios del parte de mantenimiento:") +
                            "Comentarios del parte de mantenimiento:".length()).trim();
                } else if (fullText.contains("Comentario rápido:")) {
                    text = fullText.substring(fullText.indexOf("Comentario rápido:") +
                            "Comentario rápido:".length()).trim();
                } else {
                    text = fullText;
                }
            }

            // Extraer imagen
            String imageUrl = "";
            Element imgLink = commentEl.selectFirst("a[data-toggle=lightbox]");
            if (imgLink != null) {
                String href = imgLink.attr("href");
                if (!href.isEmpty()) {
                    imageUrl = href.startsWith("http") ? href : ilacClient.buildUrl(href);
                }
            }

            // Extraer ID del comentario
            String commentId = "";
            Element editEl = commentEl.selectFirst("a[href*=service-remark-edit]");
            if (editEl != null) {
                Pattern idPattern = Pattern.compile("service-remark-edit-(\\d+)");
                Matcher idMatcher = idPattern.matcher(editEl.attr("href"));
                if (idMatcher.find()) commentId = idMatcher.group(1);
            }

            if (!dateTime.isEmpty() || !author.isEmpty() || !text.isEmpty()) {
                return TaskComment.builder()
                        .id(commentId)
                        .date(dateTime)
                        .author(author)
                        .text(text)
                        .imageUrl(imageUrl)
                        .fileName(imageUrl.isEmpty() ? "" : "Imagen")
                        .build();
            }

            return null;
        } catch (Exception e) {
            logger.error("Error al parsear comentario: {}", e.getMessage());
            return null;
        }
    }
}
