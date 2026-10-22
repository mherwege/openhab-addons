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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Proof Key for Code Exchange (RFC 7636) helpers for the S256 challenge method, which BMW requires for the device
 * code flow.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Pkce() {
        // utility class
    }

    /**
     * @return a new random code verifier, 43 characters from the unreserved URL alphabet
     */
    static String generateVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * @param verifier the code verifier
     * @return the S256 code challenge: BASE64URL(SHA256(ASCII(verifier))) without padding
     */
    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // every Java platform is required to support SHA-256
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
