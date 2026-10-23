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
package org.openhab.binding.mybmw.internal.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.TestHttpServer;

/**
 * Tests {@link CarDataAuthenticator} against a local HTTP server, with a regular openHAB HTTP client.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class CarDataAuthenticatorTest {

    private static final String CLIENT_ID = "6f1a2b3c-0000-1111-2222-333344445555";
    private static final String DEVICE_CODE_RESPONSE = """
            {"user_code":"mJimW4Gm","device_code":"device-code","interval":5,
             "verification_uri":"https://customer.bmwgroup.com/oneid/link","expires_in":300}""";

    private static final TestHttpServer SERVER = new TestHttpServer();
    private static final HttpClient HTTP_CLIENT = new HttpClient();

    private @NonNullByDefault({}) CarDataAuthenticator authenticator;

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
        authenticator = new CarDataAuthenticator(HTTP_CLIENT, CLIENT_ID, SERVER.baseUrl());
    }

    private DeviceAuthorization authorization() throws Exception {
        SERVER.reply(200, DEVICE_CODE_RESPONSE);
        DeviceAuthorization authorization = authenticator.startDeviceAuthorization();
        SERVER.reset();
        return authorization;
    }

    @Test
    void deviceCodeRequestSendsPkceChallenge() throws Exception {
        SERVER.reply(200, DEVICE_CODE_RESPONSE);
        Instant before = Instant.now();
        DeviceAuthorization authorization = authenticator.startDeviceAuthorization();

        TestHttpServer.Request request = SERVER.requests().get(0);
        assertEquals("POST", request.method());
        assertEquals("/gcdm/oauth/device/code", request.path());
        Map<String, String> form = request.form();
        assertEquals(CLIENT_ID, form.get("client_id"));
        assertEquals("device_code", form.get("response_type"));
        assertEquals("authenticate_user openid cardata:api:read cardata:streaming:read", form.get("scope"));
        assertEquals("S256", form.get("code_challenge_method"));
        assertEquals(Pkce.challenge(authorization.codeVerifier()), form.get("code_challenge"));

        assertEquals("device-code", authorization.deviceCode());
        assertEquals("mJimW4Gm", authorization.userCode());
        assertEquals("https://customer.bmwgroup.com/oneid/link?user_code=mJimW4Gm", authorization.userLink());
        assertEquals(Duration.ofSeconds(5), authorization.interval());
        assertFalse(authorization.expiresAt().isBefore(before.plusSeconds(300)));
    }

    @Test
    void incompleteDeviceCodeResponseIsAnError() {
        SERVER.reply(200, "{\"user_code\":\"mJimW4Gm\"}");
        assertThrows(IOException.class, authenticator::startDeviceAuthorization);
    }

    @Test
    void unknownClientIsAConfigurationError() {
        SERVER.reply(400, "{\"error\":\"invalid_client\",\"error_description\":\"Unknown client\"}");
        CarDataAuthException e = assertThrows(CarDataAuthException.class, authenticator::startDeviceAuthorization);
        assertEquals(CarDataAuthException.INVALID_CLIENT, e.getError());
        assertTrue(e.requiresReauthorization());
    }

    @Test
    void pollingSendsVerifierAndReportsPending() throws Exception {
        DeviceAuthorization authorization = authorization();
        // BMW reports a pending authorization as 403
        SERVER.reply(403, "{\"error_description\":\"The user has not yet completed authorization\","
                + "\"error\":\"authorization_pending\"}");
        CarDataAuthException e = assertThrows(CarDataAuthException.class,
                () -> authenticator.pollForToken(authorization));
        assertTrue(e.isPending());
        assertFalse(e.requiresReauthorization());

        TestHttpServer.Request request = SERVER.requests().get(0);
        assertEquals("/gcdm/oauth/token", request.path());
        Map<String, String> form = request.form();
        assertEquals(CLIENT_ID, form.get("client_id"));
        assertEquals("device-code", form.get("device_code"));
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", form.get("grant_type"));
        assertEquals(authorization.codeVerifier(), form.get("code_verifier"));
    }

    @Test
    void pollingReturnsTokens() throws Exception {
        DeviceAuthorization authorization = authorization();
        SERVER.reply(200, """
                {"gcid":"gcid-1","token_type":"Bearer","access_token":"access","refresh_token":"refresh",
                 "scope":"cardata:api:read openid cardata:streaming:read authenticate_user",
                 "id_token":"id","expires_in":3599}""");
        Instant before = Instant.now();
        TokenSet tokens = authenticator.pollForToken(authorization);

        assertEquals(CLIENT_ID, tokens.clientId());
        assertEquals("gcid-1", tokens.gcid());
        assertEquals("access", tokens.accessToken());
        assertEquals("id", tokens.idToken());
        assertEquals("refresh", tokens.refreshToken());
        assertTrue(tokens.hasScope(CarDataAuthenticator.SCOPE_API));
        assertFalse(tokens.expiresAt().isBefore(before.plusSeconds(3599)));
    }

    @Test
    void tokenResponseWithoutIdTokenIsAnError() throws Exception {
        DeviceAuthorization authorization = authorization();
        SERVER.reply(200, "{\"gcid\":\"gcid-1\",\"access_token\":\"access\",\"refresh_token\":\"refresh\"}");
        assertThrows(IOException.class, () -> authenticator.pollForToken(authorization));
    }

    @Test
    void deniedAuthorizationRequiresANewOne() throws Exception {
        DeviceAuthorization authorization = authorization();
        SERVER.reply(400, "{\"error\":\"access_denied\"}");
        CarDataAuthException e = assertThrows(CarDataAuthException.class,
                () -> authenticator.pollForToken(authorization));
        assertTrue(e.requiresReauthorization());
    }

    @Test
    void unauthorizedWithoutChallengeIsAnInvalidClient() {
        // no WWW-Authenticate header: Jetty's authentication handler drops the body, only the status is known
        SERVER.reply(401, "{\"error\":\"invalid_client\"}");
        CarDataAuthException e = assertThrows(CarDataAuthException.class, authenticator::startDeviceAuthorization);
        assertEquals(CarDataAuthException.INVALID_CLIENT, e.getError());
        assertTrue(e.requiresReauthorization());
    }

    @Test
    void serverErrorIsACommunicationError() {
        SERVER.reply(500, "");
        assertThrows(IOException.class, authenticator::startDeviceAuthorization);
        SERVER.reply(502, "{\"message\":\"Bad gateway\"}");
        assertThrows(IOException.class, authenticator::startDeviceAuthorization);
    }
}
