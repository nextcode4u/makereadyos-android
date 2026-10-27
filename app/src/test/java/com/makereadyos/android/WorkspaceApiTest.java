package com.makereadyos.android;

import org.junit.Test;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class WorkspaceApiTest {
    private static final URI SERVER = URI.create("https://demo.example");
    private static String contract() throws Exception {
        JSONObject paths = new JSONObject();
        for (String path : List.of("/api/auth/login", "/api/auth/logout"))
            paths.put(path, new JSONObject().put("post", new JSONObject()));
        for (String path : List.of("/api/auth/me", "/api/my-work"))
            paths.put(path, new JSONObject().put("get", new JSONObject()));
        return new JSONObject().put("info", new JSONObject().put("title", "MakeReadyOS API")
            .put("version", "0.1.0-rc")).put("paths", paths).toString();
    }
    private static String session(String id) throws Exception {
        return new JSONObject().put("csrfToken", "test-csrf").put("user",
            new JSONObject().put("id", id).put("fullName", "Demo Technician")).toString();
    }
    static class Fake extends HttpURLConnection {
        int status = 200;
        String response;
        String contentType = "application/json; charset=utf-8";
        Map<String, List<String>> headers = Map.of();
        ByteArrayOutputStream sent = new ByteArrayOutputStream();
        boolean disconnected;
        Fake(String response) throws Exception { super(SERVER.toURL()); this.response = response; }
        @Override public int getResponseCode() { return status; }
        @Override public String getContentType() { return contentType; }
        @Override public Map<String,List<String>> getHeaderFields() { return headers; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() { return sent; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
    static class Harness {
        final List<Fake> calls = new ArrayList<>();
        final List<URI> urls = new ArrayList<>();
        final Deque<Fake> responses = new ArrayDeque<>();
        final WorkspaceApi api = new WorkspaceApi(SERVER, uri -> {
            urls.add(uri);
            Fake next = responses.removeFirst(); calls.add(next); return next;
        });
        Fake add(String response) throws Exception { Fake fake = new Fake(response); responses.add(fake); return fake; }
        void authenticate() throws Exception {
            add(contract()); api.verify();
            Fake login = add(session("u1"));
            login.headers = Map.of("Set-Cookie", List.of("session=test-cookie; Secure; HttpOnly; Path=/; SameSite=Lax"));
            add(session("u1")); api.login("demo", "test-password");
        }
    }
    @Test public void requiresCompatibilityBeforeSendingCredentials() throws Exception {
        Harness h = new Harness();
        assertEquals("incompatible", assertThrows(WorkspaceApi.Failure.class, () -> h.api.login("a", "b")).kind);
        assertTrue(h.calls.isEmpty());
    }
    @Test public void rejectsNonAppAndWrongVersion() throws Exception {
        for (String response : List.of("{}", contract().replace("0.1.0-rc", "9.0.0"))) {
            Harness h = new Harness(); h.add(response);
            assertEquals("incompatible", assertThrows(WorkspaceApi.Failure.class, h.api::verify).kind);
        }
    }
    @Test public void refusesRedirectWithoutFollowingOrSendingAnotherRequest() throws Exception {
        Harness h = new Harness(); Fake response = h.add("{}"); response.status = 302;
        response.headers = Map.of("Location", List.of("https://other.example"));
        assertEquals("redirect", assertThrows(WorkspaceApi.Failure.class, h.api::verify).kind);
        assertEquals(1, h.calls.size()); assertFalse(response.getInstanceFollowRedirects());
        assertTrue(response.disconnected);
    }
    @Test public void cookiesCsrfAndExpectedUserStayWithInstance() throws Exception {
        Harness h = new Harness(); h.authenticate();
        assertTrue(h.calls.get(2).getRequestProperty("Cookie").contains("test-cookie"));
        Fake logout = h.add("{}"); h.api.logout();
        assertEquals("test-csrf", logout.getRequestProperty("X-CSRF-Token"));
        assertEquals("u1", logout.getRequestProperty("X-Mros-Expected-User"));
        assertEquals("https://demo.example", logout.getRequestProperty("Origin"));
        assertNull(h.api.displayName);
        assertThrows(WorkspaceApi.Failure.class, h.api::loadWork);
        Harness isolated = new Harness(); Fake check = isolated.add(contract()); isolated.api.verify();
        assertTrue(check.getRequestProperty("Cookie") == null || check.getRequestProperty("Cookie").isEmpty());
        assertTrue(h.urls.stream().allMatch(uri -> uri.getHost().equals("demo.example")));
    }
    @Test public void failedLogoutStillClearsLocalSession() throws Exception {
        Harness h = new Harness(); h.authenticate(); h.add("{}").status = 503;
        assertThrows(WorkspaceApi.Failure.class, h.api::logout);
        assertNull(h.api.displayName);
        assertThrows(WorkspaceApi.Failure.class, h.api::loadWork);
    }
    @Test public void changedIdentityClearsSessionBeforeWorkRequest() throws Exception {
        Harness h = new Harness(); h.authenticate(); h.add(session("u2"));
        assertEquals("session", assertThrows(WorkspaceApi.Failure.class, h.api::loadWork).kind);
        assertNull(h.api.displayName); assertEquals(4, h.calls.size());
    }
    @Test public void parsesOnlyDisplayFieldsAndSeparatesUpcoming() throws Exception {
        Harness h = new Harness(); h.authenticate(); h.add(session("u1"));
        h.add("""
          {"target":{"id":"u1"},"items":[{"id":"t1","unitNumber":"101",
          "property":{"name":"Demo Property"},"status":"In Progress","moveInDate":null,
          "applicant":"PRIVATE RESIDENT","newDoorCode":"PRIVATE CODE","assignedTech":null}],
          "forecast":{"turns":[{"title":"Unit 102","propertyName":"Demo Property",
          "expectedStartDate":"2026-11-01","projectedStart":true}]},"pestItems":[{}]}
          """);
        WorkspaceApi.Work work = h.api.loadWork();
        assertEquals(1, work.turns().size()); assertEquals(1, work.upcoming().size());
        assertEquals(1, work.otherCount()); assertEquals("", work.turns().get(0).moveIn());
        assertFalse(work.toString().contains("PRIVATE"));
        assertEquals("GET", h.calls.get(h.calls.size()-1).getRequestMethod());
    }
    @Test public void rejectsWrongTarget() throws Exception {
        Harness h = new Harness(); h.authenticate(); h.add(session("u1"));
        h.add("{\"target\":{\"id\":\"u2\"},\"items\":[]}");
        assertEquals("session", assertThrows(WorkspaceApi.Failure.class, h.api::loadWork).kind);
        assertNull(h.api.displayName);
    }
    @Test public void htmlResponsesAreNotTreatedAsCompatible() throws Exception {
        Harness h = new Harness(); h.add("<html>Login</html>").contentType = "text/html";
        assertEquals("incompatible", assertThrows(WorkspaceApi.Failure.class, h.api::verify).kind);
    }
    @Test public void classifiesHttpFailures() throws Exception {
        Map<Integer,String> kinds = Map.of(401,"session",409,"session",403,"forbidden",429,"rate_limit",503,"server");
        for (var entry : kinds.entrySet()) {
            Harness h = new Harness(); h.add("{}").status = entry.getKey();
            assertEquals(entry.getValue(), assertThrows(WorkspaceApi.Failure.class, h.api::verify).kind);
        }
    }
    @Test public void limitsResponseSize() throws Exception {
        Harness h = new Harness(); h.add(" ".repeat(4 * 1024 * 1024 + 1));
        assertEquals("too_large", assertThrows(WorkspaceApi.Failure.class, h.api::verify).kind);
    }
}
