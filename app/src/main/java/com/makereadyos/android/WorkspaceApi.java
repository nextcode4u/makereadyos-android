package com.makereadyos.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Instance-owned, memory-only cookie jar. No WebView bridge or global cookie handler. */
public final class WorkspaceApi {
    public static final class Failure extends IOException {
        public final String kind;
        Failure(String kind) { super(kind); this.kind = kind; }
    }
    interface ConnectionFactory { HttpURLConnection open(URI uri) throws IOException; }
    private final URI server;
    private final ConnectionFactory connections;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private String csrf;
    private String userId;
    private boolean verified;
    public volatile String displayName;

    public WorkspaceApi(URI server) {
        this(server, uri -> (HttpURLConnection) uri.toURL().openConnection());
    }
    WorkspaceApi(URI server, ConnectionFactory connections) {
        this.server = ServerAddress.parse(server.toString());
        this.connections = connections;
    }
    public String origin() { return server.toString(); }

    public synchronized void verify() throws Exception {
        verified = false;
        JSONObject document = request("GET", "/api/openapi.json", null);
        JSONObject info = document.optJSONObject("info");
        JSONObject paths = document.optJSONObject("paths");
        if (info == null || !"MakeReadyOS API".equals(info.optString("title"))
                || !"0.1.0-rc".equals(info.optString("version")) || paths == null
                || !hasMethod(paths, "/api/auth/login", "post")
                || !hasMethod(paths, "/api/auth/me", "get")
                || !hasMethod(paths, "/api/auth/logout", "post")
                || !hasMethod(paths, "/api/my-work", "get")) throw new Failure("incompatible");
        verified = true;
    }
    private boolean hasMethod(JSONObject paths, String path, String method) {
        JSONObject endpoint = paths.optJSONObject(path);
        return endpoint != null && endpoint.optJSONObject(method) != null;
    }
    public synchronized void login(String identifier, String password) throws Exception {
        if (!verified) throw new Failure("incompatible");
        clear();
        try {
            JSONObject body = new JSONObject().put("identifier", identifier).put("password", password);
            acceptSession(request("POST", "/api/auth/login", body), false);
            acceptSession(request("GET", "/api/auth/me", null), true);
        } catch (Exception failure) { clear(); throw failure; }
    }
    private void acceptSession(JSONObject response, boolean checkIdentity) throws Exception {
        JSONObject user = response.optJSONObject("user");
        if (user == null || user.optString("id").isBlank() || response.optString("csrfToken").isBlank())
            throw new Failure("incompatible");
        if (checkIdentity && !user.optString("id").equals(userId)) throw new Failure("session");
        userId = user.getString("id");
        displayName = user.optString("fullName", "");
        csrf = response.getString("csrfToken");
    }
    public synchronized Work loadWork() throws Exception {
        if (userId == null) throw new Failure("session");
        try {
            acceptSession(request("GET", "/api/auth/me", null), true);
            JSONObject body = request("GET", "/api/my-work", null);
            if (body.optJSONObject("target") == null || !userId.equals(body.getJSONObject("target").optString("id")))
                throw new Failure("session");
            return Work.parse(body);
        } catch (Failure failure) {
            if (failure.kind.equals("session") || failure.kind.equals("forbidden")) clear();
            throw failure;
        }
    }
    public synchronized void logout() throws Exception {
        try { if (userId != null) request("POST", "/api/auth/logout", new JSONObject()); }
        finally { clear(); }
    }
    private void clear() { cookies.getCookieStore().removeAll(); csrf = null; userId = null; displayName = null; }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        URI uri = server.resolve(path);
        HttpURLConnection connection = connections.open(uri);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", origin());
        connection.setRequestProperty("User-Agent", "MakeReadyOS-Android/0.1.0-beta.2");
        if (userId != null) connection.setRequestProperty("X-Mros-Expected-User", userId);
        for (Map.Entry<String, List<String>> header : cookies.get(uri, Map.of()).entrySet())
            connection.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
        if (csrf != null && method.equals("POST")) connection.setRequestProperty("X-CSRF-Token", csrf);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (var out = connection.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) throw new Failure("redirect");
            if (code == 401 || code == 409) throw new Failure("session");
            if (code == 403) throw new Failure("forbidden");
            if (code == 429) throw new Failure("rate_limit");
            if (code >= 500) throw new Failure("server");
            if (code != 200) throw new Failure("incompatible");
            String type = connection.getContentType();
            if (type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                throw new Failure("incompatible");
            cookies.put(uri, connection.getHeaderFields());
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    if (out.size() + count > 4 * 1024 * 1024) throw new Failure("too_large");
                    out.write(chunk, 0, count);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    /** Project only the fields used by the API projection; never retain raw codes or resident records. */
    public record Turn(String id, String unit, String property, String status, String moveIn,
                       String daysUntil, String technician, String preparation, String paint, String cleaning) {
        static Turn parse(JSONObject item) throws Exception {
            JSONObject property = item.optJSONObject("property");
            return new Turn(item.getString("id"), item.getString("unitNumber"),
                property == null ? "" : text(property, "name"), text(item, "status"),
                text(item, "moveInDate"), text(item, "daysUntilMoveIn"), text(item, "assignedTech"),
                text(item, "makeReadyStatus"), text(item, "paintStatus"), text(item, "cleaningStatus"));
        }
    }
    public record Upcoming(String title, String property, String date, boolean projected) {}
    public record Work(List<Turn> turns, List<Upcoming> upcoming, int otherCount) {
        static Work parse(JSONObject body) throws Exception {
            JSONArray items = body.getJSONArray("items");
            List<Turn> turns = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) turns.add(Turn.parse(items.getJSONObject(i)));
            List<Upcoming> upcoming = new ArrayList<>();
            JSONObject forecast = body.optJSONObject("forecast");
            JSONArray future = forecast == null ? null : forecast.optJSONArray("turns");
            if (future != null) for (int i = 0; i < future.length(); i++) {
                JSONObject row = future.getJSONObject(i);
                upcoming.add(new Upcoming(text(row, "title"), text(row, "propertyName"),
                    text(row, "expectedStartDate"), row.optBoolean("projectedStart")));
            }
            int other = 0;
            for (String key : List.of("projectItems", "pestItems", "leaseComplianceItems", "pmTasks")) {
                JSONArray rows = body.optJSONArray(key);
                if (rows != null) other += rows.length();
            }
            return new Work(List.copyOf(turns), List.copyOf(upcoming), other);
        }
    }
    private static String text(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }
}
