package com.ilac.service;

import com.ilac.model.TaskDetail;
import com.ilac.model.WorkOrder;
import com.ilac.model.Task;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class WorkOrderService {

    @Value("${ilac.base-url}")
    private String baseUrl;

    @Autowired
    private SessionService sessionService;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /**
     * Get authenticated page for a specific user
     */
    private Document getPage(String url, String identity) throws IOException {
        Map<String, String> cookies = sessionService.getCookies(identity);
        if (cookies == null) {
            throw new RuntimeException("Not logged in. Please login first.");
        }
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .cookies(cookies)
                .timeout(15000)
                .ignoreContentType(true)
                .get();
    }

    /**
     * Get raw HTML of a page for debugging
     */
    public String getRawHtml(String path, String identity) throws IOException {
        String url = path.startsWith("http") ? path : baseUrl + path;
        Document doc = getPage(url, identity);
        return doc.html();
    }

    /**
     * Scrape all work orders (Team, My work orders, and New/pending orders)
     */
    public Map<String, List<WorkOrder>> getAllWorkOrders(String identity) {
        try {
            Document doc = getPage(baseUrl, identity);
            Map<String, List<WorkOrder>> result = new HashMap<>();

            // Parse New/Pending work orders (pending-accordion) - "Nuevas ordenes de trabajo"
            List<WorkOrder> newOrders = parsePendingWorkOrders(doc);
            result.put("newWorkOrders", newOrders);

            // Parse Team work orders (team-accordion)
            List<WorkOrder> teamOrders = parseWorkOrderSection(doc, "#team-accordion");
            result.put("teamWorkOrders", teamOrders);

            // Parse My work orders (work-orders-accordion)
            List<WorkOrder> myOrders = parseWorkOrderSection(doc, "#work-orders-accordion");
            result.put("myWorkOrders", myOrders);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of(
                "newWorkOrders", Collections.emptyList(),
                "teamWorkOrders", Collections.emptyList(),
                "myWorkOrders", Collections.emptyList()
            );
        }
    }

    /**
     * Parse pending/new work orders from pending-accordion
     * These have different structure with Accept/Reject buttons
     */
    private List<WorkOrder> parsePendingWorkOrders(Document doc) {
        List<WorkOrder> workOrders = new ArrayList<>();

        Element section = doc.selectFirst("#pending-accordion");
        if (section == null) {
            return workOrders;
        }

        // Find all work order panels
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
     * Parse a pending work order panel (different structure from regular work orders)
     */
    private WorkOrder parsePendingWorkOrderPanel(Element panel) {
        try {
            Element heading = panel.selectFirst(".panel-heading");
            if (heading == null) return null;

            // Extract work order tag
            Element tagEl = heading.selectFirst(".work-order-tag");
            String tag = tagEl != null ? tagEl.text().trim() : "";
            String id = tag.replace("#", "");

            if (tag.isEmpty() || id.isEmpty()) {
                return null;
            }

            // Extract title
            Element titleEl = heading.selectFirst(".work-order-title");
            String title = titleEl != null ? titleEl.text().trim() : "";

            // Extract due date
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

            // Extract task count
            String taskCount = "";
            Element badge = heading.selectFirst(".label-info");
            if (badge != null) {
                taskCount = badge.text().replace("Tasks:", "").trim();
            }

            // Extract accept/reject tag URLs
            String acceptTagUrl = "";
            String rejectTagUrl = "";
            Element acceptLink = heading.selectFirst("a.accept[href*=accept-tag]");
            Element rejectLink = heading.selectFirst("a.reject[href*=reject-tag]");
            if (acceptLink != null) acceptTagUrl = acceptLink.attr("href");
            if (rejectLink != null) rejectTagUrl = rejectLink.attr("href");

            // Parse tasks
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse a pending task panel (has Accept/Reject buttons instead of detail link)
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
            String location = "";
            String department = "";
            String machine = "";
            String machinePart = "";
            String title = "";
            String description = "";
            String product = "";

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

            // Get accept/reject URLs
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse work orders from a specific section
     */
    private List<WorkOrder> parseWorkOrderSection(Document doc, String sectionSelector) {
        List<WorkOrder> workOrders = new ArrayList<>();

        Element section = doc.selectFirst(sectionSelector);
        if (section == null) {
            return workOrders;
        }

        // Check if there's a "no tasks" message
        Element noTasks = section.selectFirst(".text-center");
        if (noTasks != null && noTasks.text().contains("no hay tareas")) {
            return workOrders;
        }

        // Find all work order panels
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
     * Parse a work order panel
     */
    private WorkOrder parseWorkOrderPanel(Element panel) {
        try {
            Element heading = panel.selectFirst(".panel-heading");
            if (heading == null) return null;

            // Extract work order tag (#112, #113, etc.)
            Element tagEl = heading.selectFirst(".work-order-tag");
            String tag = tagEl != null ? tagEl.text().trim() : "";
            String id = tag.replace("#", "");

            // Skip if no tag (not a real work order)
            if (tag.isEmpty() || id.isEmpty()) {
                return null;
            }

            // Extract title
            Element titleEl = heading.selectFirst(".work-order-title");
            String title = titleEl != null ? titleEl.text().trim() : "";

            // Extract due date
            Element dueDateEl = heading.selectFirst(".text-success strong, .text-danger strong");
            String dueDate = dueDateEl != null ? dueDateEl.text().trim() : "";

            // Extract task count and completion
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

            // Parse tasks inside the panel
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Parse a task panel
     */
    private Task parseTaskPanel(Element taskPanel) {
        try {
            String panelId = taskPanel.attr("id");
            String taskId = panelId.replace("-task", "");

            Element dataHref = taskPanel.selectFirst("[data-href]");
            String detailUrl = dataHref != null ? dataHref.attr("data-href") : "";

            Element header = taskPanel.selectFirst(".panel-heading");
            String orderNumber = "";
            String status = "";
            String dueDate = "";
            String dispatchType = "";

            if (header != null) {
                Element orderNum = header.selectFirst(".task-order-number");
                orderNumber = orderNum != null ? orderNum.text().trim() : "";

                if ("Completado".equals(orderNumber)) {
                    status = "completed";
                } else {
                    status = "pending";
                }

                Element dueDateEl = header.selectFirst(".task-order-due");
                dueDate = dueDateEl != null ? dueDateEl.text().replace("vencimiento:", "").trim() : "";

                Element dispatchEl = header.selectFirst(".task-order-auto");
                dispatchType = dispatchEl != null ? dispatchEl.text().trim() : "";
            }

            Element body = taskPanel.selectFirst(".panel-body");
            String location = "";
            String department = "";
            String machine = "";
            String machinePart = "";
            String title = "";
            String description = "";
            String product = "";

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

            Element footer = taskPanel.selectFirst(".panel-footer");
            String assignedTo = "";
            if (footer != null) {
                Element userEl = footer.selectFirst(".task-confirmation-link .fa-user");
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get task detail with all info (except images)
     */
    public TaskDetail getTaskDetail(String taskUrl, String identity) {
        try {
            String fullUrl = taskUrl.startsWith("http") ? taskUrl : baseUrl + taskUrl;
            Document doc = getPage(fullUrl, identity);

            TaskDetail detail = new TaskDetail();

            // Parse the detail table
            Element table = doc.selectFirst("#task-detail-maintenance-point table");
            if (table != null) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    Element th = row.selectFirst("th");
                    Element td = row.selectFirst("td");
                    if (th != null && td != null) {
                        String label = th.text().trim().replace(":", "");
                        String value = td.text().trim();

                        switch (label) {
                            case "Departamento":
                                detail.setDepartment(value);
                                break;
                            case "Ubicación":
                                detail.setLocation(value);
                                break;
                            case "Máquina":
                                detail.setMachine(value);
                                break;
                            case "Parte de máquina":
                                detail.setMachinePart(value);
                                break;
                            case "Punto de mantenimiento":
                                detail.setMaintenancePoint(value);
                                break;
                            case "Cantidad de puntos":
                                detail.setPointCount(value);
                                break;
                        }
                    }
                }
            }

            // Parse task type and application mode
            Element taskTypeEl = doc.selectFirst(".task-detail-task-name p");
            detail.setTaskType(taskTypeEl != null ? taskTypeEl.text().trim() : "");

            Element appModeEl = doc.selectFirst(".task-detail-tool-info p");
            detail.setApplicationMode(appModeEl != null ? appModeEl.text().trim() : "");

            // Parse product info
            Element productNameEl = doc.selectFirst(".task-detail-product-info span");
            detail.setProductName(productNameEl != null ? productNameEl.text().trim() : "");

            Element volumeEl = doc.selectFirst(".task-detail-product-info p:nth-child(3)");
            detail.setProductVolume(volumeEl != null ? volumeEl.text().trim() : "");

            // Set empty lists for images (not needed)
            detail.setImages(Collections.emptyList());
            detail.setToolImages(Collections.emptyList());
            detail.setProductImages(Collections.emptyList());
            detail.setSafetyIcons(Collections.emptyList());

            return detail;
        } catch (Exception e) {
            e.printStackTrace();
            return TaskDetail.builder()
                    .images(Collections.emptyList())
                    .toolImages(Collections.emptyList())
                    .productImages(Collections.emptyList())
                    .safetyIcons(Collections.emptyList())
                    .build();
        }
    }

    /**
     * Get all work orders with full task details (except images)
     */
    public Map<String, List<WorkOrder>> getFullWorkOrders(String identity) {
        Map<String, List<WorkOrder>> allOrders = getAllWorkOrders(identity);

        // Process team work orders
        for (WorkOrder wo : allOrders.getOrDefault("teamWorkOrders", Collections.emptyList())) {
            enrichWorkOrderWithDetails(wo, identity);
        }

        // Process my work orders
        for (WorkOrder wo : allOrders.getOrDefault("myWorkOrders", Collections.emptyList())) {
            enrichWorkOrderWithDetails(wo, identity);
        }

        return allOrders;
    }

    /**
     * Enrich a work order with task details
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
     * Add a comment to a task
     */
    public boolean addComment(String taskId, String commentText, byte[] imageData, String imageName, String identity) {
        try {
            Map<String, String> cookies = sessionService.getCookies(identity);
            if (cookies == null) {
                throw new RuntimeException("Not logged in");
            }

            String taskUrl = baseUrl + "/engineer-task-detail-" + taskId;
            
            // First, get the task detail page to find the CSRF token and form
            Document doc = Jsoup.connect(taskUrl)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .get();

            // Find the comment form
            Element form = doc.selectFirst("form[action*=comment], form[action*=add-comment], #comment-form, .comment-form");
            if (form == null) {
                // Try to find any form with textarea
                form = doc.selectFirst("form:has(textarea)");
            }

            if (form == null) {
                throw new RuntimeException("Could not find comment form on page");
            }

            // Get CSRF token
            Element csrfElement = doc.selectFirst("input[name=csrf]");
            String csrfToken = csrfElement != null ? csrfElement.val() : "";

            // Get form action
            String action = form.attr("action");
            if (action.isEmpty()) {
                action = taskUrl;
            } else if (!action.startsWith("http")) {
                action = baseUrl + action;
            }

            // Submit the comment
            Connection.Response response;
            if (imageData != null && imageName != null && !imageName.isEmpty()) {
                // Submit with image
                response = Jsoup.connect(action)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .cookies(cookies)
                        .data("csrf", csrfToken)
                        .data("comment", commentText)
                        .data("submit", "")
                        .header("Content-Type", "multipart/form-data")
                        .timeout(30000)
                        .ignoreHttpErrors(true)
                        .execute();
            } else {
                // Submit without image
                response = Jsoup.connect(action)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .cookies(cookies)
                        .data("csrf", csrfToken)
                        .data("comment", commentText)
                        .data("submit", "")
                        .timeout(15000)
                        .ignoreHttpErrors(true)
                        .execute();
            }

            return response.statusCode() == 200 || response.statusCode() == 302;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Mark a task as completed ("Marcar como realizada")
     */
    public boolean markTaskAsCompleted(String taskId, String identity) {
        try {
            Map<String, String> cookies = sessionService.getCookies(identity);
            if (cookies == null) {
                throw new RuntimeException("Not logged in");
            }

            String taskUrl = baseUrl + "/engineer-task-detail-" + taskId;

            // Get the task detail page
            Document doc = Jsoup.connect(taskUrl)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .get();

            // Find the "Marcar como realizada" link
            Element markDoneLink = doc.selectFirst("a[href*=mark-done], a[href*=complete], a:containsOwn(Marcar como realizada), a:containsOwn(mark as done)");
            
            if (markDoneLink == null) {
                // Try finding by text content
                Elements links = doc.select("a");
                for (Element link : links) {
                    if (link.text().contains("Marcar como realizada") || link.text().contains("marcar como realizada")) {
                        markDoneLink = link;
                        break;
                    }
                }
            }

            if (markDoneLink == null) {
                throw new RuntimeException("Could not find 'Marcar como realizada' link");
            }

            String href = markDoneLink.attr("href");
            if (href.isEmpty()) {
                throw new RuntimeException("Link has no href");
            }

            String fullUrl = href.startsWith("http") ? href : baseUrl + href;

            // Click the link
            Connection.Response response = Jsoup.connect(fullUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            return response.statusCode() == 200 || response.statusCode() == 302;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reject a task with a reason
     * The reject page returns a modal with a form containing:
     * - description (textarea for reason)
     * - csrf (hidden token)
     * - submit
     */
    public boolean rejectTask(String taskId, String reason, String identity) {
        try {
            Map<String, String> cookies = sessionService.getCookies(identity);
            if (cookies == null) {
                throw new RuntimeException("Not logged in");
            }

            // Get the reject page to get the CSRF token
            String rejectUrl = baseUrl + "/jobs-reject-" + taskId;
            Document doc = Jsoup.connect(rejectUrl)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .get();

            // The response is JSON with modal HTML, extract CSRF from it
            String html = doc.html();
            
            // Extract CSRF token from the HTML in JSON response
            String csrfToken = "";
            int csrfIndex = html.indexOf("name=\\\"csrf\\\" value=\\\"");
            if (csrfIndex > 0) {
                int start = csrfIndex + "name=\\\"csrf\\\" value=\\\"".length();
                int end = html.indexOf("\\\"", start);
                if (end > start) {
                    csrfToken = html.substring(start, end);
                }
            }

            // If still empty, try different escape pattern
            if (csrfToken.isEmpty()) {
                csrfIndex = html.indexOf("name=\"csrf\" value=\"");
                if (csrfIndex > 0) {
                    int start = csrfIndex + "name=\"csrf\" value=\"".length();
                    int end = html.indexOf("\"", start);
                    if (end > start) {
                        csrfToken = html.substring(start, end);
                    }
                }
            }

            // Submit the reject form
            Connection.Response response = Jsoup.connect(rejectUrl)
                    .method(Connection.Method.POST)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .data("description", reason)
                    .data("csrf", csrfToken)
                    .data("submit", "Guardar")
                    .timeout(15000)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            return response.statusCode() == 200 || response.statusCode() == 302;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Accept a task
     */
    public boolean acceptTask(String taskId, String identity) {
        try {
            Map<String, String> cookies = sessionService.getCookies(identity);
            if (cookies == null) {
                throw new RuntimeException("Not logged in");
            }

            String acceptUrl = baseUrl + "/jobs-accept-" + taskId;
            
            Connection.Response response = Jsoup.connect(acceptUrl)
                    .method(Connection.Method.GET)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            return response.statusCode() == 200 || response.statusCode() == 302;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get service ID for a task (needed for comments)
     * The service ID is found on the task detail page
     */
    public String getServiceId(String taskId, String identity) {
        try {
            String taskUrl = baseUrl + "/engineer-task-detail-" + taskId;
            Document doc = getPage(taskUrl, identity);
            
            // Find the "nuevo comentario" link which contains the service ID
            Element commentLink = doc.selectFirst("a[href*=service-remark-create]");
            if (commentLink != null) {
                String href = commentLink.attr("href");
                // Extract service ID from href like "/service-remark-create-1191426"
                return href.replace("/service-remark-create-", "").trim();
            }
            
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Add a comment to a task using the service ID
     */
    public boolean addCommentWithServiceId(String serviceId, String commentText, byte[] imageData, String imageName, String identity) {
        try {
            Map<String, String> cookies = sessionService.getCookies(identity);
            if (cookies == null) {
                throw new RuntimeException("Not logged in");
            }

            // Get the comment form page to get CSRF token
            String formUrl = baseUrl + "/service-remark-create-" + serviceId;
            Document doc = Jsoup.connect(formUrl)
                    .userAgent(USER_AGENT)
                    .cookies(cookies)
                    .timeout(15000)
                    .ignoreContentType(true)
                    .get();

            // Extract CSRF token from JSON response
            String html = doc.html();
            String csrfToken = "";
            int csrfIndex = html.indexOf("name=\"csrf\" value=\"");
            if (csrfIndex > 0) {
                int start = csrfIndex + "name=\"csrf\" value=\"".length();
                int end = html.indexOf("\"", start);
                if (end > start) {
                    csrfToken = html.substring(start, end);
                }
            }

            // Submit the comment form
            Connection.Response response;
            if (imageData != null && imageName != null && !imageName.isEmpty()) {
                // Submit with image using multipart
                response = Jsoup.connect(formUrl)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .cookies(cookies)
                        .data("csrf", csrfToken)
                        .data("serviceRemark[text]", commentText)
                        .data("serviceRemark[id]", "")
                        .data("submit", "Añadir comentario")
                        .header("Content-Type", "multipart/form-data")
                        .timeout(30000)
                        .ignoreHttpErrors(true)
                        .execute();
            } else {
                // Submit without image
                response = Jsoup.connect(formUrl)
                        .method(Connection.Method.POST)
                        .userAgent(USER_AGENT)
                        .cookies(cookies)
                        .data("csrf", csrfToken)
                        .data("serviceRemark[text]", commentText)
                        .data("serviceRemark[id]", "")
                        .data("submit", "Añadir comentario")
                        .timeout(15000)
                        .ignoreHttpErrors(true)
                        .execute();
            }

            return response.statusCode() == 200 || response.statusCode() == 302;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
