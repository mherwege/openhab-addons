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
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.client.api.Response;
import org.junit.jupiter.api.Test;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;

/**
 * Tests for {@link CarDataTokens} and {@link TokenSet}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class CarDataTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final TokenSet TOKENS = new TokenSet("client", "gcid", "access-secret", "id-secret",
            "refresh-secret", NOW.plus(Duration.ofHours(1)),
            "authenticate_user openid cardata:api:read cardata:streaming:read");

    @Test
    void roundTrip() {
        AccessTokenResponse response = CarDataTokens.toAccessTokenResponse(TOKENS, NOW);
        assertEquals(NOW, response.getCreatedOn());
        assertEquals(3600, response.getExpiresIn());
        assertFalse(response.isExpired(NOW, 10));
        assertEquals(TOKENS, CarDataTokens.fromAccessTokenResponse("client", response, null, null));
    }

    @Test
    void refreshResponseWithoutBmwFieldsKeepsKnownValues() {
        // a refresh response as core stores it, in case BMW leaves out the gcid or id_token
        AccessTokenResponse refreshed = new AccessTokenResponse();
        refreshed.setAccessToken("access-2");
        refreshed.setRefreshToken("refresh-2");
        refreshed.setCreatedOn(NOW);
        refreshed.setExpiresIn(3600);

        TokenSet tokens = CarDataTokens.fromAccessTokenResponse("client", refreshed, TOKENS, "gcid");
        assertNotNull(tokens);
        assertEquals("access-2", tokens.accessToken());
        assertEquals("id-secret", tokens.idToken());
        assertEquals("gcid", tokens.gcid());
        assertEquals("", tokens.scope());

        assertNull(CarDataTokens.fromAccessTokenResponse("client", refreshed, null, "gcid"));

        refreshed.setExtraFields(Map.of(CarDataTokens.ID_TOKEN, "id-2", CarDataTokens.GCID, "gcid-2"));
        tokens = CarDataTokens.fromAccessTokenResponse("client", refreshed, TOKENS, "gcid");
        assertNotNull(tokens);
        assertEquals("id-2", tokens.idToken());
        assertEquals("gcid-2", tokens.gcid());
    }

    @Test
    void responseWithoutCreationTimeIsRejected() {
        AccessTokenResponse response = CarDataTokens.toAccessTokenResponse(TOKENS, NOW);
        response.setCreatedOn(null);
        assertNull(CarDataTokens.fromAccessTokenResponse("client", response, TOKENS, "gcid"));
    }

    @Test
    void errorsMapToAuthExceptions() {
        OAuthResponseException invalidGrant = new OAuthResponseException();
        invalidGrant.setError("invalid_grant");
        assertTrue(CarDataTokens.toAuthException(invalidGrant).requiresReauthorization());

        OAuthResponseException serverError = new OAuthResponseException();
        serverError.setError("temporarily_unavailable");
        assertFalse(CarDataTokens.toAuthException(serverError).requiresReauthorization());

        assertFalse(CarDataTokens.toAuthException(new OAuthResponseException()).requiresReauthorization());
        assertTrue(CarDataTokens.toAuthException(new OAuthException("no token")).requiresReauthorization());
    }

    @Test
    void tokensStayOutOfToString() {
        String text = TOKENS.toString();
        assertFalse(text.contains("secret"), text);
    }

    @Test
    void validityAndScopes() {
        Instant expiry = TOKENS.expiresAt();
        assertTrue(TOKENS.isValid(expiry.minus(Duration.ofMinutes(10)), Duration.ofMinutes(5)));
        assertFalse(TOKENS.isValid(expiry.minus(Duration.ofMinutes(4)), Duration.ofMinutes(5)));
        assertTrue(TOKENS.hasScope("cardata:api:read"));
        assertFalse(TOKENS.hasScope("cardata:api"));
    }

    @Test
    void unauthorizedTokenResponseRequiresReauthorization() {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(401);
        // as core's OAuth client wraps the exception of Jetty's authentication handler
        IOException e = new IOException("Exception in oauth communication, grant type refresh_token",
                new ExecutionException(new HttpResponseException("HTTP protocol violation", response)));
        CarDataAuthException authException = CarDataTokens.fromUnauthorizedResponse(e);
        assertNotNull(authException);
        assertTrue(authException.requiresReauthorization());

        when(response.getStatus()).thenReturn(500);
        assertNull(CarDataTokens.fromUnauthorizedResponse(e));
        assertNull(CarDataTokens.fromUnauthorizedResponse(new IOException("timeout")));
    }
}
