package org.owner.orchestrator;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Minimal authenticated HTTPS API. No cookie extraction or ChatGPT session scraping. */
final class OrchestratorApi {
    private final String endpoint;
    private final String token;

    OrchestratorApi(String endpoint, String token) {
        this.endpoint = validateEndpoint(endpoint);
        this.token = token;
    }

    static String validateEndpoint(String input) {
        try {
            URI uri = new URI(input.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getRawUserInfo() != null || uri.getRawQuery() != null ||
                    uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443) ||
                    (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
                throw new IllegalArgumentException("Требуется HTTPS-адрес сервера без пути и параметров");
            }
            String host = uri.getHost();
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1"))
                throw new IllegalArgumentException("Используйте защищённое доменное имя сервера");
            return "https://" + host.toLowerCase(java.util.Locale.ROOT);
        } catch (java.net.URISyntaxException ex) {
            throw new IllegalArgumentException("Неверный адрес сервера");
        }
    }

    JSONObject get(String path) throws Exception { return request("GET", path, null); }
    JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body); }

    private JSONObject request(String method, String path, JSONObject body) throws Exception {
        if (!path.startsWith("/v1/") && !path.equals("/health"))
            throw new IllegalArgumentException("Недопустимый API-путь");
        if (!path.matches("[A-Za-z0-9/_-]+"))
            throw new IllegalArgumentException("Некорректный API-путь");
        URL url = new URL(endpoint + path);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cache-Control", "no-store");
        if (!path.equals("/health")) {
            if (token == null || token.length() < 32) throw new IllegalStateException("Нет сохранённого токена");
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 32_768) throw new IllegalArgumentException("Запрос слишком большой");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
        }
        try {
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) throw new IllegalStateException("Переадресация сервера запрещена");
            InputStream input = code < 400 ? connection.getInputStream() : connection.getErrorStream();
            if (input == null) throw new IllegalStateException("Сервер не вернул ответ (HTTP " + code + ")");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream stream = input) {
                byte[] part = new byte[4096];
                int read;
                while ((read = stream.read(part)) != -1) {
                    if (buffer.size() + read > 262_144) throw new IllegalStateException("Слишком большой ответ");
                    buffer.write(part, 0, read);
                }
            }
            JSONObject response = new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
            if (code >= 400) {
                String detail = response.optString("error", "Ошибка API");
                throw new IllegalStateException("HTTP " + code + ": " + detail);
            }
            return response;
        } finally { connection.disconnect(); }
    }
}
