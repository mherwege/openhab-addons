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
package org.openhab.binding.mybmw.internal.stream;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.data.TelematicValue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Parses CarData stream messages.
 * <p>
 * BMW publishes one JSON envelope per message on topic {@code <gcid>/<vin>}:
 *
 * <pre>
 * {
 *   "vin": "...",
 *   "entityId": "...",
 *   "topic": "...",
 *   "timestamp": "...",
 *   "data": { "vehicle.x.y": { "value": ..., "unit": "...", "timestamp": "..." }, ... }
 * }
 * </pre>
 *
 * The bmw-mqtt-bridge republishes this envelope unchanged on {@code <prefix>raw/<vin>}, so the same parser serves
 * both stream sources.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public final class StreamMessageParser {

    /**
     * A parsed message: the vehicle it concerns and its data points.
     *
     * @param vin vehicle identification number
     * @param values the data points
     */
    public record StreamMessage(String vin, List<TelematicValue> values) {
    }

    private StreamMessageParser() {
        // utility class
    }

    /**
     * @param topic topic the message arrived on, used for the VIN when the payload does not carry it
     * @param payload message payload
     * @return the parsed message, or {@code null} when the payload is not a CarData envelope
     */
    public static @Nullable StreamMessage parse(String topic, byte[] payload) {
        JsonObject envelope;
        try {
            JsonElement json = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                return null;
            }
            envelope = json.getAsJsonObject();
        } catch (JsonParseException e) {
            return null;
        }

        String vin = string(envelope, "vin");
        if (vin == null) {
            vin = lastTopicLevel(topic);
        }
        JsonElement data = envelope.get("data");
        if (vin == null || data == null || !data.isJsonObject()) {
            return null;
        }
        Instant envelopeTime = instant(string(envelope, "timestamp"));

        List<TelematicValue> values = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject item = entry.getValue().getAsJsonObject();
            JsonElement value = item.get("value");
            if (value == null) {
                continue;
            }
            Instant timestamp = instant(string(item, "timestamp"));
            values.add(new TelematicValue(entry.getKey(), value, string(item, "unit"),
                    timestamp != null ? timestamp : envelopeTime));
        }
        return new StreamMessage(vin, values);
    }

    private static @Nullable String lastTopicLevel(String topic) {
        int slash = topic.lastIndexOf('/');
        String level = slash >= 0 ? topic.substring(slash + 1) : topic;
        return level.isBlank() ? null : level;
    }

    private static @Nullable String string(JsonObject json, String member) {
        JsonElement element = json.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
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
}
