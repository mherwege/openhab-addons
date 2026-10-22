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
import org.eclipse.jdt.annotation.Nullable;

/**
 * A pending device authorization: the codes BMW handed out, and the PKCE verifier that has to accompany the token
 * request.
 *
 * @param deviceCode code the binding polls with
 * @param userCode code the user enters in the BMW portal
 * @param verificationUri page where the user enters the code
 * @param verificationUriComplete page with the user code already filled in, if BMW provides it
 * @param expiresAt when the codes expire
 * @param interval minimum time between polls
 * @param codeVerifier PKCE verifier matching the challenge sent with the device code request
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public record DeviceAuthorization(String deviceCode, String userCode, String verificationUri,
        @Nullable String verificationUriComplete, Instant expiresAt, Duration interval, String codeVerifier) {

    /**
     * @return the link to show the user: the one with the code filled in when available
     */
    public String userLink() {
        String complete = verificationUriComplete;
        if (complete != null && !complete.isBlank()) {
            return complete;
        }
        return verificationUri + (verificationUri.contains("?") ? "&" : "?") + "user_code=" + userCode;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        // the device code and verifier are secrets, keep them out of logs
        return "DeviceAuthorization[userCode=" + userCode + ", verificationUri=" + verificationUri + ", expiresAt="
                + expiresAt + ", interval=" + interval + "]";
    }
}
