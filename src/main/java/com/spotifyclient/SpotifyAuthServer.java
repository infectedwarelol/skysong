package com.spotifyclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class SpotifyAuthServer {
    static final int PORT = 43821;
    static final String REDIRECT_URI = "http://127.0.0.1:43821/callback";
    private static final long TIMEOUT_MS = 300000L;
    private final String expectedState;
    private final Consumer<String> onCode;
    private final Consumer<String> onError;
    private volatile boolean running = true;
    private ServerSocket socket;

    SpotifyAuthServer(String expectedState, Consumer<String> onCode, Consumer<String> onError) {
        this.expectedState = expectedState;
        this.onCode = onCode;
        this.onError = onError;
    }

    void start() throws IOException {
        this.socket = new ServerSocket(43821, 4, InetAddress.getLoopbackAddress());
        this.socket.setSoTimeout(1000);
        Thread t = new Thread(this::serve, "Versefall Spotify login");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        this.running = false;

        try {
            if (this.socket != null) {
                this.socket.close();
            }
        } catch (IOException var2) {
        }
    }

    private void serve() {
        long deadline = System.currentTimeMillis() + 300000L;

        while (this.running && System.currentTimeMillis() < deadline) {
            try (Socket client = this.socket.accept()) {
                client.setSoTimeout(5000);
                if (this.handle(client)) {
                    break;
                }
            } catch (SocketTimeoutException var8) {
            } catch (IOException e) {
                if (this.running) {
                    SpotifyModClient.LOGGER.warn("[Versefall] Login receiver error: " + e.getMessage());
                }
            }
        }

        this.stop();
    }

    private boolean handle(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = in.readLine();
        if (requestLine == null) {
            return false;
        } else {
            String[] parts = requestLine.split(" ");
            String target = parts.length > 1 ? parts[1] : "";
            if (!target.startsWith("/callback")) {
                respond(client, 404, "Not found", "");
                return false;
            } else {
                Map<String, String> query = parseQuery(target.contains("?") ? target.substring(target.indexOf(63) + 1) : "");
                if (!this.expectedState.equals(query.get("state"))) {
                    respond(client, 400, "This login link is out of date", "Go back to Minecraft and click Connect Spotify again.");
                    return false;
                } else if (query.containsKey("error")) {
                    respond(client, 200, "Login cancelled", "You can close this tab. Click Connect Spotify in Minecraft to try again.");
                    this.onError.accept(query.get("error"));
                    return true;
                } else {
                    String code = query.get("code");
                    if (code != null && !code.isEmpty()) {
                        respond(client, 200, "Versefall is connected", "You can close this tab and go back to Minecraft.");
                        this.onCode.accept(code);
                        return true;
                    } else {
                        respond(client, 400, "Something went wrong", "Spotify didn't send a login code. Try Connect Spotify again.");
                        return false;
                    }
                }
            }
        }
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> map = new HashMap<>();

        for (String pair : q.split("&")) {
            int eq = pair.indexOf(61);
            if (eq > 0) {
                map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }

        return map;
    }

    private static void respond(Socket client, int status, String title, String message) throws IOException {
        String html = "<!doctype html><html><head><meta charset=utf-8><title>"
            + title
            + "</title><style>body{margin:0;height:100vh;display:grid;place-items:center;background:#11163a;font-family:Segoe UI,system-ui,sans-serif;color:#f4f6ff}.card{padding:40px 48px;border-radius:16px;background:rgba(91,124,250,.14);border:1px solid rgba(200,212,255,.25);text-align:center;max-width:420px}h1{margin:0 0 10px;font-size:24px;letter-spacing:.04em}p{margin:0;color:#a9b1cc}</style></head><body><div class=card><h1>"
            + title
            + "</h1><p>"
            + message
            + "</p></div></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : (status == 404 ? "Not Found" : "Bad Request");
        String head = "HTTP/1.1 "
            + status
            + " "
            + reason
            + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
            + body.length
            + "\r\nConnection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }
}
