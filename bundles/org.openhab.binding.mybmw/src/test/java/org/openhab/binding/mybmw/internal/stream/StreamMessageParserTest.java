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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.binding.mybmw.internal.stream.StreamMessageParser.StreamMessage;

/**
 * Tests for {@link StreamMessageParser}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class StreamMessageParserTest {

    private static final String VIN = "WBA00000000000001";

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = StreamMessageParserTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            return in.readAllBytes();
        }
    }

    @Test
    void parsesEnvelope() throws IOException {
        StreamMessage message = StreamMessageParser.parse("bmw/raw/" + VIN, resource("/stream-message.json"));
        assertNotNull(message);
        assertEquals(VIN, message.vin());

        Map<String, TelematicValue> values = message.values().stream()
                .collect(Collectors.toMap(TelematicValue::descriptor, Function.identity()));
        assertEquals(4, values.size());

        TelematicValue soc = values.get("vehicle.drivetrain.batteryManagement.header");
        assertNotNull(soc);
        assertEquals(62, soc.value().getAsInt());
        assertEquals("%", soc.unit());
        assertEquals(Instant.parse("2026-10-20T08:00:00Z"), soc.timestamp());

        TelematicValue status = values.get("vehicle.drivetrain.electricEngine.charging.status");
        assertNotNull(status);
        assertEquals("CHARGINGACTIVE", status.value().getAsString());
        assertNull(status.unit());

        // a data point without its own timestamp takes the envelope's
        TelematicValue moving = values.get("vehicle.isMoving");
        assertNotNull(moving);
        assertEquals(Instant.parse("2026-10-20T08:00:05Z"), moving.timestamp());

        TelematicValue lock = values.get("vehicle.cabin.door.status");
        assertNotNull(lock);
        assertTrue(lock.value().isJsonObject());
    }

    @Test
    void takesVinFromTopicWhenPayloadLacksIt() {
        String payload = "{\"data\":{\"vehicle.isMoving\":{\"value\":false}}}";
        StreamMessage message = StreamMessageParser.parse("bmw/raw/" + VIN, payload.getBytes(StandardCharsets.UTF_8));
        assertNotNull(message);
        assertEquals(VIN, message.vin());
        assertEquals(1, message.values().size());
    }

    @Test
    void rejectsOtherPayloads() {
        assertNull(StreamMessageParser.parse("bmw/raw/" + VIN, "not json".getBytes(StandardCharsets.UTF_8)));
        assertNull(StreamMessageParser.parse("bmw/raw/" + VIN, "[1,2]".getBytes(StandardCharsets.UTF_8)));
        assertNull(StreamMessageParser.parse("bmw/raw/" + VIN,
                "{\"vin\":\"x\",\"data\":\"none\"}".getBytes(StandardCharsets.UTF_8)));
    }
}
