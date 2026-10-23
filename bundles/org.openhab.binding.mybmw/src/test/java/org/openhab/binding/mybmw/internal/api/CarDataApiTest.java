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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.MapStorage;
import org.openhab.binding.mybmw.internal.TestHttpServer;
import org.openhab.binding.mybmw.internal.api.dto.Container;
import org.openhab.binding.mybmw.internal.api.dto.VehicleMapping;
import org.openhab.binding.mybmw.internal.data.TelematicValue;

import com.google.gson.JsonParser;

/**
 * Tests {@link CarDataApi} against a local HTTP server, with a regular openHAB HTTP client.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class CarDataApiTest {

    private static final String VIN = "WBY11CF020CM00000";

    private static final TestHttpServer SERVER = new TestHttpServer();
    private static final HttpClient HTTP_CLIENT = new HttpClient();

    private final AtomicInteger rejected = new AtomicInteger();
    private RequestBudget budget = new RequestBudget(new MapStorage(), "budget", 40, Clock.systemUTC());
    private @NonNullByDefault({}) CarDataApi api;

    @BeforeAll
    static void start() throws Exception {
        HTTP_CLIENT.start();
    }

    @AfterAll
    static void stop() throws Exception {
        HTTP_CLIENT.stop();
        SERVER.close();
    }

    @BeforeEach
    void setUp() {
        SERVER.reset();
        rejected.set(0);
        api = api(budget);
    }

    private CarDataApi api(RequestBudget budget) {
        return new CarDataApi(HTTP_CLIENT, () -> "access-token", rejected::incrementAndGet, budget, SERVER.baseUrl());
    }

    @Test
    void sendsHeadersAndCountsRequests() throws Exception {
        SERVER.reply(200, "[]");
        api.getMappings();

        TestHttpServer.Request request = SERVER.requests().get(0);
        assertEquals("GET", request.method());
        assertEquals("/customers/vehicles/mappings", request.path());
        assertEquals("Bearer access-token", request.headers().get("authorization"));
        assertEquals("v1", request.headers().get("x-version"));
        assertEquals("application/json", request.headers().get("accept"));
        assertEquals(1, budget.used());
    }

    @Test
    void mappingsAcceptArrayAndObject() throws Exception {
        SERVER.reply(200, "[{\"vin\":\"" + VIN + "\",\"mappingType\":\"PRIMARY\"},{\"vin\":\"X\"}]");
        List<VehicleMapping> mappings = api.getMappings();
        assertEquals(2, mappings.size());
        assertEquals(VIN, mappings.get(0).vin);
        assertEquals(VehicleMapping.PRIMARY, mappings.get(0).mappingType);

        // the documented shape
        SERVER.reply(200, "{\"vin\":\"" + VIN + "\",\"mappingType\":\"PRIMARY\"}");
        assertEquals(1, api.getMappings().size());

        SERVER.reply(200, "{}");
        assertEquals(List.of(), api.getMappings());
    }

    @Test
    void telematicDataSendsContainerAndParsesValues() throws Exception {
        SERVER.reply(200, """
                {"telematicData": {
                  "vehicle.vehicle.travelledDistance":
                    {"value": "76663", "unit": "km", "timestamp": "2026-10-23T09:32:23Z"},
                  "vehicle.cabin.door.status": {"value": "LOCKED"},
                  "vehicle.isMoving": {"timestamp": null, "unit": null, "value": null},
                  "vehicle.no.value": {"unit": "km"},
                  "vehicle.not.an.object": 5
                }}""");
        List<TelematicValue> values = api.getTelematicData(VIN, "container-1");

        TestHttpServer.Request request = SERVER.requests().get(0);
        assertEquals("/customers/vehicles/" + VIN + "/telematicData", request.path());
        assertEquals("containerId=container-1", request.query());

        assertEquals(2, values.size());
        TelematicValue mileage = values.stream()
                .filter(value -> "vehicle.vehicle.travelledDistance".equals(value.descriptor())).findFirst()
                .orElseThrow();
        assertEquals("76663", mileage.value().getAsString());
        assertEquals("km", mileage.unit());
        assertEquals(Instant.parse("2026-10-23T09:32:23Z"), mileage.timestamp());
    }

    @Test
    void createsAndDeletesContainers() throws Exception {
        SERVER.reply(201, "{\"containerId\":\"container-1\",\"name\":\"openHAB\"}");
        Container created = api.createContainer(
                new Container.CreateRequest("openHAB", "purpose", List.of("vehicle.vehicle.travelledDistance")));
        assertEquals("container-1", created.containerId);

        TestHttpServer.Request create = SERVER.requests().get(0);
        assertEquals("POST", create.method());
        assertEquals("/customers/containers", create.path());
        assertEquals(JsonParser.parseString("""
                {"name": "openHAB", "purpose": "purpose",
                 "technicalDescriptors": ["vehicle.vehicle.travelledDistance"]}"""),
                JsonParser.parseString(create.body()));

        SERVER.reply(204, "");
        api.deleteContainer("container-1");
        TestHttpServer.Request delete = SERVER.requests().get(1);
        assertEquals("DELETE", delete.method());
        assertEquals("/customers/containers/container-1", delete.path());
    }

    @Test
    void containerWithoutIdIsAnError() {
        SERVER.reply(201, "{}");
        assertThrows(IOException.class, () -> api.createContainer(new Container.CreateRequest("n", "p", List.of())));
    }

    @Test
    void errorIdsAreParsed() {
        SERVER.reply(400, "{\"exveErrorId\":\"CU-105\",\"exveErrorMsg\":\"Container not found\"}");
        CarDataApiException e = assertThrows(CarDataApiException.class, () -> api.getTelematicData(VIN, "container-1"));
        assertEquals(400, e.getHttpStatus());
        assertTrue(e.isUnknownContainer());
        assertFalse(e.isRateLimited());
        assertEquals("CU-105: Container not found", e.getMessage());
    }

    @Test
    void rateLimitExhaustsTheBudget() {
        // BMW reports its daily limit as 403 with an error id, not as 429
        SERVER.reply(403, "{\"exveErrorId\":\"CU-429\",\"exveErrorMsg\":\"Rate limit exceeded\"}");
        CarDataApiException e = assertThrows(CarDataApiException.class, api::getMappings);
        assertTrue(e.isRateLimited());

        CarDataApiException next = assertThrows(CarDataApiException.class, api::getMappings);
        assertTrue(next.isBudgetExhausted());
        assertEquals(1, SERVER.requests().size(), "no request may be sent once the limit is reached");
    }

    @Test
    void exhaustedBudgetSendsNothing() throws Exception {
        CarDataApi api = api(new RequestBudget(new MapStorage(), "budget", 1, Clock.systemUTC()));
        SERVER.reply(200, "[]");
        api.getMappings();

        CarDataApiException e = assertThrows(CarDataApiException.class, api::getMappings);
        assertTrue(e.isBudgetExhausted());
        assertEquals(1, SERVER.requests().size());
    }

    @Test
    void unauthorizedWithoutChallengeRejectsTheToken() {
        // BMW's 401 has no WWW-Authenticate header, which Jetty's authentication handler turns into an exception
        SERVER.reply(401, "{\"exveErrorId\":\"CU-102\",\"exveErrorMsg\":\"Token expired\"}");
        CarDataApiException e = assertThrows(CarDataApiException.class, api::getMappings);
        assertTrue(e.isUnauthorized());
        assertFalse(e.isRateLimited());
        assertEquals(1, rejected.get());
    }

    @Test
    void unreachableServerIsACommunicationError() throws Exception {
        String unreachable;
        try (TestHttpServer closed = new TestHttpServer()) {
            unreachable = closed.baseUrl();
        }
        CarDataApi api = new CarDataApi(HTTP_CLIENT, () -> "access-token", rejected::incrementAndGet, budget,
                unreachable);
        assertThrows(IOException.class, api::getMappings);
        assertEquals(0, rejected.get());
    }

    @Test
    void unreadableResponseIsACommunicationError() {
        SERVER.reply(200, "<html>maintenance</html>");
        assertThrows(IOException.class, api::getMappings);
    }
}
