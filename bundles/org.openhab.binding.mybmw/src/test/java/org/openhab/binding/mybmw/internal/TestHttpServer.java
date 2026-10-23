/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mybmw.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server for tests: it records every request and answers with the replies queued beforehand, in order.
 * Like BMW, it answers 401 without a WWW-Authenticate header.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class TestHttpServer implements AutoCloseable {

    /**
     * A request the server received.
     *
     * @param method HTTP method
     * @param path raw path
     * @param query raw query, if any
     * @param headers request headers with lower case names, first value only
     * @param body request body
     */
    public record Request(String method, String path, @Nullable String query, Map<String, String> headers,
            String body) {

        /**
         * @return the body parsed as form fields
         */
        public Map<String, String> form() {
            Map<String, String> fields = new HashMap<>();
            for (String pair : body.split("&")) {
                int separator = pair.indexOf('=');
                if (separator > 0) {
                    fields.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
                }
            }
            return fields;
        }
    }

    private record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    /**
     * Starts the server on a free local port. The constructor throws no checked exception, so the server can be a
     * {@code static final} field of a test, which the null analysis requires for fields set up once.
     */
    public TestHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    /**
     * @return the base URL of the server, without trailing slash
     */
    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    /**
     * Queues the reply to the next request that has none yet.
     *
     * @param status HTTP status
     * @param json response body, empty for none
     */
    public void reply(int status, String json) {
        replies.add(new Reply(status, json));
    }

    /**
     * @return the requests received so far
     */
    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /**
     * Forgets queued replies and received requests.
     */
    public void reset() {
        replies.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            URI uri = exchange.getRequestURI();
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
            });
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), uri.getRawPath(), uri.getRawQuery(), headers, body));

            Reply reply = replies.poll();
            if (reply == null) {
                reply = new Reply(500, "{\"error\":\"no reply queued\"}");
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 0) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(reply.status(), bytes.length > 0 ? bytes.length : -1);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
