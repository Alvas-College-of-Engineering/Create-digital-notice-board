package digitalnoticeboard;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class DigitalNoticeBoardApp {
    private static final int DEFAULT_PORT = 8080;
    private static final String[] CATEGORIES = {"General", "Academic", "Exam", "Event", "Urgent"};

    private final NoticeStorage storage;
    private final NoticeBoard board;

    public DigitalNoticeBoardApp() {
        this.storage = new NoticeStorage(Paths.get("data", "notices.ser"));
        this.board = storage.load();
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        DigitalNoticeBoardApp app = new DigitalNoticeBoardApp();
        app.start(port);
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            return parsePort(args[0], DEFAULT_PORT);
        }

        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            return parsePort(envPort, DEFAULT_PORT);
        }

        return DEFAULT_PORT;
    }

    private static int parsePort(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new BoardHandler());
        server.createContext("/notice", new NoticeActionHandler());
        server.setExecutor(Executors.newFixedThreadPool(6));
        server.start();
        System.out.println("Digital Notice Board is running at http://localhost:" + port + "/");
        waitForShutdown();
    }

    private void waitForShutdown() {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private class BoardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            RequestState state = readState(exchange);
            Optional<Notice> selected = selectedNotice(state.selectedId);
            String html = renderPage(state, selected);
            sendHtml(exchange, 200, html);
        }
    }

    private class NoticeActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }

            Map<String, String> form = parseForm(exchange);
            String action = form.getOrDefault("action", "");
            String selectedId = form.getOrDefault("id", "");
            String redirectId = selectedId;
            String message;

            synchronized (board) {
                switch (action) {
                    case "create":
                        message = createNotice(form);
                        Notice latest = board.getAllNotices().isEmpty() ? null : board.getAllNotices().get(0);
                        redirectId = latest == null ? "" : latest.getId();
                        break;
                    case "update":
                        message = updateNotice(form);
                        break;
                    case "archive":
                        message = board.archiveNotice(selectedId) ? "Notice archived." : "Select a notice first.";
                        persist();
                        break;
                    case "restore":
                        message = board.restoreNotice(selectedId) ? "Notice restored." : "Select a notice first.";
                        persist();
                        break;
                    default:
                        message = "Choose a valid action.";
                        break;
                }
            }

            redirect(exchange, "/?tab=" + url(form.getOrDefault("tab", "current"))
                    + "&id=" + url(redirectId)
                    + "&message=" + url(message));
        }
    }

    private String createNotice(Map<String, String> form) throws IOException {
        ValidationResult result = validateNoticeForm(form);
        if (!result.valid) {
            return result.message;
        }

        board.addNotice(form.get("title").trim(), form.get("category").trim(),
                form.get("content").trim(), form.get("postedBy").trim());
        persist();
        return "Notice posted.";
    }

    private String updateNotice(Map<String, String> form) throws IOException {
        ValidationResult result = validateNoticeForm(form);
        if (!result.valid) {
            return result.message;
        }

        boolean updated = board.updateNotice(form.getOrDefault("id", ""),
                form.get("title").trim(), form.get("category").trim(),
                form.get("content").trim(), form.get("postedBy").trim());
        if (updated) {
            persist();
            return "Notice updated and history saved.";
        }

        return "Select a notice to update.";
    }

    private ValidationResult validateNoticeForm(Map<String, String> form) {
        if (form.getOrDefault("title", "").trim().isEmpty()
                || form.getOrDefault("content", "").trim().isEmpty()
                || form.getOrDefault("postedBy", "").trim().isEmpty()) {
            return new ValidationResult(false, "Title, content, and posted by are required.");
        }
        return new ValidationResult(true, "");
    }

    private void persist() throws IOException {
        storage.save(board);
    }

    private RequestState readState(HttpExchange exchange) {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String tab = query.getOrDefault("tab", "current");
        if (!tab.equals("current") && !tab.equals("past") && !tab.equals("all")) {
            tab = "current";
        }

        return new RequestState(tab, query.getOrDefault("id", ""), query.getOrDefault("message", ""));
    }

    private Optional<Notice> selectedNotice(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        synchronized (board) {
            return board.findNoticeById(id);
        }
    }

    private String renderPage(RequestState state, Optional<Notice> selected) {
        List<Notice> current;
        List<Notice> past;
        List<Notice> all;
        synchronized (board) {
            current = board.getCurrentNotices();
            past = board.getPastNotices();
            all = board.getAllNotices();
        }

        Notice editing = selected.orElse(null);
        String activeId = editing == null ? "" : editing.getId();

        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Digital Notice Board</title><style>" + styles() + "</style></head>"
                + "<body><main class=\"shell\">"
                + "<header class=\"topbar\"><div><p class=\"eyebrow\">Campus Admin Console</p>"
                + "<h1>Digital Notice Board</h1><p class=\"subtitle\">Create, update, archive, and restore notices from one clean local web dashboard.</p></div>"
                + "<div class=\"stats\"><span><strong>" + current.size() + "</strong> Current</span>"
                + "<span><strong>" + past.size() + "</strong> Past</span><span><strong>" + all.size() + "</strong> Total</span></div></header>"
                + renderMessage(state.message)
                + "<section class=\"workspace\">"
                + "<aside class=\"panel notice-panel\">"
                + renderTabs(state.tab)
                + renderList(state.tab, state.tab.equals("current") ? current : state.tab.equals("past") ? past : all, activeId)
                + "</aside>"
                + "<section class=\"panel detail-panel\">" + renderDetail(editing) + "</section>"
                + "</section>"
                + renderForm(state.tab, editing)
                + "</main></body></html>";
    }

    private String renderMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return "<div class=\"toast\">" + escape(message) + "</div>";
    }

    private String renderTabs(String activeTab) {
        return "<nav class=\"tabs\">"
                + tabLink("current", "Current", activeTab)
                + tabLink("past", "Past", activeTab)
                + tabLink("all", "All", activeTab)
                + "</nav>";
    }

    private String tabLink(String tab, String label, String activeTab) {
        String activeClass = tab.equals(activeTab) ? " active" : "";
        return "<a class=\"tab" + activeClass + "\" href=\"/?tab=" + tab + "\">" + label + "</a>";
    }

    private String renderList(String tab, List<Notice> notices, String activeId) {
        if (notices.isEmpty()) {
            return "<div class=\"empty\"><strong>No notices here yet.</strong><span>Use the editor below to post the first one.</span></div>";
        }

        StringBuilder builder = new StringBuilder("<div class=\"notice-list\">");
        for (Notice notice : notices) {
            String activeClass = notice.getId().equals(activeId) ? " selected" : "";
            builder.append("<a class=\"notice-card").append(activeClass).append("\" href=\"/?tab=")
                    .append(url(tab)).append("&id=").append(url(notice.getId())).append("\">")
                    .append("<span class=\"badge ").append(cssClass(notice.getCategory())).append("\">")
                    .append(escape(notice.getCategory())).append("</span>")
                    .append("<h2>").append(escape(notice.getTitle())).append("</h2>")
                    .append("<p>").append(escape(shorten(notice.getContent(), 115))).append("</p>")
                    .append("<footer><span>").append(escape(notice.getPostedBy())).append("</span><span>")
                    .append(escape(notice.getFormattedLastUpdatedAt())).append("</span></footer>")
                    .append("</a>");
        }
        return builder.append("</div>").toString();
    }

    private String renderDetail(Notice notice) {
        if (notice == null) {
            return "<div class=\"detail-empty\"><p class=\"eyebrow\">Notice Preview</p><h2>Select a notice</h2>"
                    + "<p>Details, status, timestamps, and update history will appear here.</p></div>";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<article class=\"detail\"><div class=\"detail-heading\"><span class=\"badge ")
                .append(cssClass(notice.getCategory())).append("\">").append(escape(notice.getCategory()))
                .append("</span><span class=\"status\">").append(escape(notice.getStatus())).append("</span></div>")
                .append("<h2>").append(escape(notice.getTitle())).append("</h2>")
                .append("<dl><div><dt>Posted By</dt><dd>").append(escape(notice.getPostedBy())).append("</dd></div>")
                .append("<div><dt>Posted At</dt><dd>").append(escape(notice.getFormattedPostedAt())).append("</dd></div>")
                .append("<div><dt>Last Updated</dt><dd>").append(escape(notice.getFormattedLastUpdatedAt())).append("</dd></div></dl>")
                .append("<div class=\"content-block\">").append(paragraphs(notice.getContent())).append("</div>")
                .append("<h3>Update History</h3>");

        if (notice.getHistory().isEmpty()) {
            builder.append("<p class=\"muted\">No updates yet.</p>");
        } else {
            builder.append("<div class=\"history\">");
            for (NoticeHistoryEntry entry : notice.getHistory()) {
                builder.append("<section><strong>").append(escape(entry.getFormattedChangedAt()))
                        .append(" by ").append(escape(entry.getChangedBy())).append("</strong>")
                        .append("<p><b>Old:</b> ").append(escape(shorten(entry.getOldContent(), 180))).append("</p>")
                        .append("<p><b>New:</b> ").append(escape(shorten(entry.getNewContent(), 180))).append("</p>")
                        .append("</section>");
            }
            builder.append("</div>");
        }

        return builder.append("</article>").toString();
    }

    private String renderForm(String tab, Notice notice) {
        String id = notice == null ? "" : notice.getId();
        String title = notice == null ? "" : notice.getTitle();
        String category = notice == null ? "General" : notice.getCategory();
        String postedBy = notice == null ? "Admin" : notice.getPostedBy();
        String content = notice == null ? "" : notice.getContent();
        boolean selected = notice != null;
        boolean archived = selected && notice.isArchived();

        return "<section class=\"editor panel\"><div class=\"editor-title\"><div><p class=\"eyebrow\">Notice Editor</p>"
                + "<h2>" + (selected ? "Edit selected notice" : "Post a new notice") + "</h2></div>"
                + "<a class=\"clear-link\" href=\"/?tab=" + url(tab) + "\">Clear selection</a></div>"
                + "<form method=\"post\" action=\"/notice\" class=\"notice-form\">"
                + hidden("id", id) + hidden("tab", tab)
                + "<label>Title<input name=\"title\" maxlength=\"90\" value=\"" + attr(title) + "\" required></label>"
                + "<label>Category<select name=\"category\">" + categoryOptions(category) + "</select></label>"
                + "<label>Posted By<input name=\"postedBy\" maxlength=\"60\" value=\"" + attr(postedBy) + "\" required></label>"
                + "<label class=\"wide\">Content<textarea name=\"content\" rows=\"5\" required>" + escape(content) + "</textarea></label>"
                + "<div class=\"actions\">"
                + "<button type=\"submit\" name=\"action\" value=\"create\">Post Notice</button>"
                + "<button type=\"submit\" name=\"action\" value=\"update\" class=\"secondary\"" + disabled(!selected) + ">Update</button>"
                + "<button type=\"submit\" name=\"action\" value=\"archive\" class=\"secondary danger\"" + disabled(!selected || archived) + ">Archive</button>"
                + "<button type=\"submit\" name=\"action\" value=\"restore\" class=\"secondary\"" + disabled(!selected || !archived) + ">Restore</button>"
                + "</div></form></section>";
    }

    private String categoryOptions(String selectedCategory) {
        StringBuilder builder = new StringBuilder();
        for (String category : CATEGORIES) {
            String selected = category.equals(selectedCategory) ? " selected" : "";
            builder.append("<option").append(selected).append(">").append(escape(category)).append("</option>");
        }
        return builder.toString();
    }

    private String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + attr(name) + "\" value=\"" + attr(value) + "\">";
    }

    private String disabled(boolean disabled) {
        return disabled ? " disabled" : "";
    }

    private String paragraphs(String value) {
        String[] blocks = value.split("\\R{2,}");
        StringBuilder builder = new StringBuilder();
        for (String block : blocks) {
            builder.append("<p>").append(escape(block).replace("\n", "<br>")).append("</p>");
        }
        return builder.toString();
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            return parseQuery(new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return values;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int equalsIndex = pair.indexOf('=');
            String key = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String value = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
            values.put(decode(key), decode(value));
        }
        return values;
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int statusCode, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private String shorten(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String cssClass(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String attr(String value) {
        return escape(value);
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String url(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String styles() {
        return "*{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:#f5f7fb;color:#152033}"
                + "a{color:inherit;text-decoration:none}.shell{width:min(1180px,calc(100% - 32px));margin:0 auto;padding:26px 0 40px}"
                + ".topbar{display:flex;align-items:flex-end;justify-content:space-between;gap:22px;margin-bottom:18px}"
                + ".eyebrow{margin:0 0 6px;color:#607087;font-size:12px;font-weight:800;letter-spacing:0;text-transform:uppercase}"
                + "h1,h2,h3,p{margin-top:0}h1{font-size:38px;line-height:1.05;margin-bottom:10px;color:#101828}h2{font-size:22px;margin-bottom:10px}h3{font-size:16px;margin:24px 0 10px}"
                + ".subtitle{margin:0;color:#536173;max-width:640px}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;min-width:330px}"
                + ".stats span{background:#fff;border:1px solid #e1e7f0;border-radius:8px;padding:12px 14px;color:#536173}.stats strong{display:block;color:#101828;font-size:24px}"
                + ".toast{background:#eaf8ef;border:1px solid #bfe8cb;color:#17633a;padding:12px 14px;border-radius:8px;margin:0 0 16px;font-weight:700}"
                + ".workspace{display:grid;grid-template-columns:minmax(310px,390px) 1fr;gap:16px;align-items:start}.panel{background:#fff;border:1px solid #e1e7f0;border-radius:8px;box-shadow:0 12px 30px rgba(21,32,51,.06)}"
                + ".notice-panel{overflow:hidden}.tabs{display:grid;grid-template-columns:repeat(3,1fr);border-bottom:1px solid #e1e7f0}.tab{text-align:center;padding:13px 8px;color:#617085;font-weight:800}.tab.active{background:#172033;color:#fff}"
                + ".notice-list{max-height:590px;overflow:auto;padding:10px}.notice-card{display:block;border:1px solid #e7edf5;border-radius:8px;padding:14px;margin-bottom:10px;background:#fff;transition:.16s ease}.notice-card:hover,.notice-card.selected{border-color:#2f6fed;box-shadow:0 8px 18px rgba(47,111,237,.12)}"
                + ".notice-card h2{font-size:17px;margin:9px 0 7px}.notice-card p{color:#536173;font-size:14px;line-height:1.45;margin-bottom:12px}.notice-card footer{display:flex;justify-content:space-between;gap:8px;color:#778397;font-size:12px}"
                + ".badge{display:inline-flex;align-items:center;border-radius:999px;padding:5px 9px;font-size:12px;font-weight:800;background:#ecf2ff;color:#2456b8}.urgent{background:#fff0ef;color:#bd2b20}.exam{background:#fff8df;color:#8b6400}.event{background:#ecfbf4;color:#11764a}.academic{background:#eef6ff;color:#1864a3}"
                + ".detail-panel{min-height:520px;padding:24px}.detail-empty{display:grid;place-content:center;min-height:470px;text-align:center;color:#68778c}.detail-empty h2{font-size:28px;color:#101828}.detail-heading{display:flex;justify-content:space-between;gap:12px;align-items:center}.status{font-size:13px;font-weight:800;color:#536173;border:1px solid #dbe3ee;border-radius:999px;padding:6px 10px}"
                + ".detail h2{font-size:30px;line-height:1.15;margin:16px 0}.detail dl{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:0 0 22px}.detail dl div{background:#f7f9fc;border:1px solid #e7edf5;border-radius:8px;padding:12px}.detail dt{font-size:12px;color:#6a788b;font-weight:800}.detail dd{margin:4px 0 0;color:#162134;font-weight:700}"
                + ".content-block{font-size:16px;line-height:1.7;color:#2d394c;border-top:1px solid #edf1f6;border-bottom:1px solid #edf1f6;padding:18px 0}.history section{border-left:3px solid #2f6fed;background:#f7f9fc;border-radius:0 8px 8px 0;padding:12px 14px;margin-bottom:10px}.history p,.muted{color:#607087}"
                + ".editor{margin-top:16px;padding:18px}.editor-title{display:flex;justify-content:space-between;gap:14px;align-items:flex-start;margin-bottom:12px}.editor-title h2{margin:0}.clear-link{color:#2f6fed;font-weight:800;font-size:14px}"
                + ".notice-form{display:grid;grid-template-columns:1.4fr .8fr .8fr;gap:12px}.notice-form label{display:grid;gap:7px;color:#465469;font-size:13px;font-weight:800}.notice-form .wide{grid-column:1/-1}"
                + "input,select,textarea{width:100%;border:1px solid #ccd6e3;border-radius:8px;padding:11px 12px;font:inherit;color:#152033;background:#fff}textarea{resize:vertical;line-height:1.45}"
                + ".actions{grid-column:1/-1;display:flex;gap:10px;flex-wrap:wrap}button{border:0;border-radius:8px;background:#172033;color:#fff;padding:11px 18px;font-weight:900;cursor:pointer}button.secondary{background:#eef2f7;color:#172033}button.danger{background:#fff0ef;color:#b42318}button:disabled{opacity:.45;cursor:not-allowed}"
                + ".empty{display:grid;gap:8px;padding:30px 18px;text-align:center;color:#607087}.empty strong{color:#172033}"
                + "@media(max-width:860px){.shell{width:min(100% - 20px,720px);padding-top:16px}.topbar,.editor-title{display:block}h1{font-size:31px}.stats{grid-template-columns:repeat(3,1fr);min-width:0;margin-top:16px}.workspace{grid-template-columns:1fr}.detail dl,.notice-form{grid-template-columns:1fr}.detail-panel{min-height:auto}.notice-list{max-height:none}.actions button{flex:1 1 140px}}";
    }

    private static class RequestState {
        private final String tab;
        private final String selectedId;
        private final String message;

        private RequestState(String tab, String selectedId, String message) {
            this.tab = tab;
            this.selectedId = selectedId;
            this.message = message;
        }
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }
}
