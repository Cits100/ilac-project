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
     * Scrape all work orders (both Team and My work orders)
     */
    public Map<String, List<WorkOrder>> getAllWorkOrders(String identity) {
        try {
            Document doc = getPage(baseUrl, identity);
            Map<String, List<WorkOrder>> result = new HashMap<>();

            // Parse Team work orders (team-accordion)
            List<WorkOrder> teamOrders = parseWorkOrderSection(doc, "#team-accordion");
            result.put("teamWorkOrders", teamOrders);

            // Parse My work orders (work-orders-accordion)
            List<WorkOrder> myOrders = parseWorkOrderSection(doc, "#work-orders-accordion");
            result.put("myWorkOrders", myOrders);

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("teamWorkOrders", Collections.emptyList(), "myWorkOrders", Collections.emptyList());
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
}
