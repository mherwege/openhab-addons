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
package org.openhab.binding.mybmw.internal.api;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.mybmw.internal.api.dto.Container;
import org.openhab.binding.mybmw.internal.api.dto.VehicleMapping;
import org.openhab.binding.mybmw.internal.auth.CarDataAuthException;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Client for the CarData customer REST API.
 * <p>
 * Every request is counted against the account's {@link RequestBudget}; a request that does not fit is not sent and
 * fails with {@link CarDataApiException#isBudgetExhausted()}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class CarDataApi {

    /**
     * Supplies a valid access token, refreshing it when needed.
     */
    @FunctionalInterface
    public interface AccessTokenProvider {
        String getAccessToken() throws IOException, CarDataAuthException;
    }

    private static final String BASE_URL = "https://api-cardata.bmwgroup.com";
    private static final String API_VERSION = "v1";
    private static final long TIMEOUT_SECONDS = 30;

    private final Logger logger = LoggerFactory.getLogger(CarDataApi.class);
    private final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final AccessTokenProvider tokenProvider;
    private final RequestBudget budget;

    public CarDataApi(HttpClient httpClient, AccessTokenProvider tokenProvider, RequestBudget budget) {
        this.httpClient = httpClient;
        this.tokenProvider = tokenProvider;
        this.budget = budget;
    }

    /**
     * @return the vehicles mapped to the account
     */
    public List<VehicleMapping> getMappings() throws IOException, CarDataAuthException, CarDataApiException {
        JsonElement json = request(HttpMethod.GET, "/customers/vehicles/mappings", null);
        List<VehicleMapping> mappings = new ArrayList<>();
        // the API specification documents an object, the live endpoint returns an array
        if (json.isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray()) {
                VehicleMapping mapping = fromJson(element, VehicleMapping.class);
                if (mapping != null) {
                    mappings.add(mapping);
                }
            }
        } else if (json.isJsonObject() && !json.getAsJsonObject().isEmpty()) {
            VehicleMapping mapping = fromJson(json, VehicleMapping.class);
            if (mapping != null) {
                mappings.add(mapping);
            }
        }
        return mappings;
    }

    /**
     * @param vin vehicle identification number
     * @return the vehicle's basic data as returned by BMW
     */
    public JsonObject getBasicData(String vin) throws IOException, CarDataAuthException, CarDataApiException {
        JsonElement json = request(HttpMethod.GET, "/customers/vehicles/" + encode(vin) + "/basicData", null);
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    /**
     * @return all containers of the account
     */
    public List<Container> listContainers() throws IOException, CarDataAuthException, CarDataApiException {
        Container.ContainerList list = fromJson(request(HttpMethod.GET, "/customers/containers", null),
                Container.ContainerList.class);
        List<Container> containers = list != null ? list.containers : null;
        return containers != null ? containers : List.of();
    }

    /**
     * @param create the container to create
     * @return the created container
     */
    public Container createContainer(Container.CreateRequest create)
            throws IOException, CarDataAuthException, CarDataApiException {
        Container container = fromJson(request(HttpMethod.POST, "/customers/containers", gson.toJson(create)),
                Container.class);
        if (container == null || container.containerId == null) {
            throw new IOException("Container creation returned no container id");
        }
        return container;
    }

    /**
     * @param vin vehicle identification number
     * @param containerId container to read
     * @return the current values of the container's descriptors
     */
    public List<TelematicValue> getTelematicData(String vin, String containerId)
            throws IOException, CarDataAuthException, CarDataApiException {
        JsonElement json = request(HttpMethod.GET,
                "/customers/vehicles/" + encode(vin) + "/telematicData?containerId=" + encode(containerId), null);
        List<TelematicValue> values = new ArrayList<>();
        if (!json.isJsonObject()) {
            return values;
        }
        JsonElement data = json.getAsJsonObject().get("telematicData");
        if (data == null || !data.isJsonObject()) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getValue().getAsJsonObject();
            JsonElement value = item.get("value");
            if (value == null) {
                continue;
            }
            values.add(new TelematicValue(entry.getKey(), value, string(item, "unit"),
                    instant(string(item, "timestamp"))));
        }
        return values;
    }

    private JsonElement request(HttpMethod method, String path, @Nullable String body)
            throws IOException, CarDataAuthException, CarDataApiException {
        String token = tokenProvider.getAccessToken();
        if (!budget.tryAcquire()) {
            throw CarDataApiException.budgetExhausted(budget.used(), budget.budget());
        }
        Request request = httpClient.newRequest(BASE_URL + path).method(method)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + token).header("x-version", API_VERSION)
                .header(HttpHeader.ACCEPT, "application/json").timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (body != null) {
            request.content(new StringContentProvider("application/json", body, StandardCharsets.UTF_8));
        }
        logger.debug("CarData request {} {} ({} of {} requests used today)", method, path, budget.used(),
                budget.budget());

        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling CarData API", e);
        } catch (TimeoutException | ExecutionException e) {
            throw new IOException("Failed to call CarData API: " + e.getMessage(), e);
        }

        int status = response.getStatus();
        if (logger.isTraceEnabled()) {
            logResponseHeaders(method, path, status, response.getHeaders());
        }
        String content = response.getContentAsString();
        if (status >= 200 && status < 300) {
            if (content == null || content.isBlank()) {
                return new JsonObject();
            }
            try {
                return JsonParser.parseString(content);
            } catch (JsonParseException e) {
                throw new IOException("Unreadable response from CarData API", e);
            }
        }

        CarDataApiException exception = toException(status, content);
        if (exception.isRateLimited()) {
            budget.markExhausted();
        }
        throw exception;
    }

    private void logResponseHeaders(HttpMethod method, String path, int status, HttpFields headers) {
        StringBuilder text = new StringBuilder();
        for (HttpField header : headers) {
            if (HttpHeader.SET_COOKIE.is(header.getName())) {
                continue;
            }
            text.append("\n  ").append(header.getName()).append(": ").append(header.getValue());
        }
        logger.trace("CarData response {} {} -> HTTP {}, headers:{}", method, path, status, text);
    }

    private CarDataApiException toException(int status, @Nullable String content) {
        String errorId = null;
        String message = "CarData API returned HTTP " + status;
        if (content != null && !content.isBlank()) {
            try {
                JsonElement json = JsonParser.parseString(content);
                if (json.isJsonObject()) {
                    errorId = string(json.getAsJsonObject(), "exveErrorId");
                    String errorMessage = string(json.getAsJsonObject(), "exveErrorMsg");
                    if (errorId != null) {
                        message = errorId + (errorMessage != null ? ": " + errorMessage : "");
                    }
                }
            } catch (JsonParseException e) {
                // keep the generic message
            }
        }
        return new CarDataApiException(status, errorId, message);
    }

    private <T> @Nullable T fromJson(JsonElement json, Class<T> type) throws IOException {
        try {
            return gson.fromJson(json, type);
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response structure from CarData API", e);
        }
    }

    private static @Nullable String string(JsonObject json, String member) {
        JsonElement element = json.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static @Nullable Instant instant(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
