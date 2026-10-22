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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;

/**
 * Converts between the binding's {@link TokenSet} and the {@link AccessTokenResponse} kept by openHAB's OAuth client
 * service.
 * <p>
 * BMW returns two fields beyond standard OAuth: the {@code id_token}, which is the password for the CarData stream,
 * and the {@code gcid}, the stream username. Core keeps unknown token response fields as extra fields, both for the
 * imported response and for every refresh response.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public final class CarDataTokens {

    static final String ID_TOKEN = "id_token";
    static final String GCID = "gcid";

    private CarDataTokens() {
        // utility class
    }

    /**
     * @param tokens tokens obtained from the device authorization
     * @param now time the tokens were issued, used by core to determine expiry
     * @return the response to import into the OAuth client service
     */
    public static AccessTokenResponse toAccessTokenResponse(TokenSet tokens, Instant now) {
        AccessTokenResponse response = new AccessTokenResponse();
        response.setAccessToken(tokens.accessToken());
        response.setRefreshToken(tokens.refreshToken());
        response.setTokenType("Bearer");
        response.setScope(tokens.scope());
        // without a creation time core treats the token as expired and refreshes on every use
        response.setCreatedOn(now);
        response.setExpiresIn(Math.max(0, Duration.between(now, tokens.expiresAt()).toSeconds()));
        response.setExtraFields(Map.of(ID_TOKEN, tokens.idToken(), GCID, tokens.gcid()));
        return response;
    }

    /**
     * @param clientId client ID the service was created for
     * @param response response from the OAuth client service
     * @param previous the tokens before this response, whose ID token is kept if the response has none
     * @param knownGcid gcid to use when the response does not carry one, e.g. after a refresh
     * @return the tokens, or {@code null} when the response lacks a required token
     */
    public static @Nullable TokenSet fromAccessTokenResponse(String clientId, AccessTokenResponse response,
            @Nullable TokenSet previous, @Nullable String knownGcid) {
        String accessToken = response.getAccessToken();
        String refreshToken = response.getRefreshToken();
        String idToken = response.getExtraFields().get(ID_TOKEN);
        if ((idToken == null || idToken.isBlank()) && previous != null) {
            idToken = previous.idToken();
        }
        String gcid = response.getExtraFields().get(GCID);
        if (gcid == null || gcid.isBlank()) {
            gcid = knownGcid;
        }
        Instant createdOn = response.getCreatedOn();
        if (accessToken == null || refreshToken == null || idToken == null || gcid == null || createdOn == null) {
            return null;
        }
        String scope = response.getScope();
        return new TokenSet(clientId, gcid, accessToken, idToken, refreshToken,
                createdOn.plusSeconds(response.getExpiresIn()), scope != null ? scope : "");
    }

    /**
     * @param e error response from the token endpoint, relayed by the OAuth client service
     * @return the equivalent binding exception, so it is handled like errors of the device authorization
     */
    public static CarDataAuthException toAuthException(OAuthResponseException e) {
        // the fields are filled by Gson, so they can be null or empty despite their declaration
        @Nullable
        String error = e.getError();
        return new CarDataAuthException(error == null || error.isBlank() ? "unknown_error" : error,
                e.getErrorDescription());
    }

    /**
     * @param e failure of the OAuth client service itself, e.g. no stored token or a token that cannot be decrypted
     * @return an exception that requires a new authorization
     */
    public static CarDataAuthException toAuthException(OAuthException e) {
        return new CarDataAuthException(CarDataAuthException.INVALID_GRANT, e.getMessage());
    }
}
