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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The tokens obtained from the BMW customer identity service.
 * <p>
 * The access token authorizes REST calls and the ID token is the password for the CarData stream; both are valid for
 * one hour. The refresh token is valid for two weeks and is replaced on every refresh, so as long as the binding keeps
 * refreshing, the user only has to authorize once.
 *
 * @param clientId CarData client ID the tokens were issued for
 * @param gcid BMW customer ID, the username for the CarData stream
 * @param accessToken bearer token for the REST API
 * @param idToken token for the CarData stream
 * @param refreshToken token to obtain a new set
 * @param expiresAt expiry of the access and ID token
 * @param scope scopes granted, space separated
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public record TokenSet(String clientId, String gcid, String accessToken, String idToken, String refreshToken,
        Instant expiresAt, String scope) {

    /**
     * @param now current time
     * @param margin safety margin before the actual expiry
     * @return whether the access token can still be used for at least the margin
     */
    public boolean isValid(Instant now, Duration margin) {
        return now.plus(margin).isBefore(expiresAt);
    }

    /**
     * @param scopeName a scope, e.g. {@code cardata:api:read}
     * @return whether the scope was granted
     */
    public boolean hasScope(String scopeName) {
        for (String granted : scope.split(" ")) {
            if (granted.equals(scopeName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "TokenSet[clientId=" + clientId + ", expiresAt=" + expiresAt + ", scope=" + scope + "]";
    }
}
