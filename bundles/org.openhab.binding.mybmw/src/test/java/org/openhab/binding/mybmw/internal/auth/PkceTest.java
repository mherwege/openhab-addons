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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Pkce}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class PkceTest {

    @Test
    void challengeMatchesRfc7636Example() {
        // RFC 7636, appendix B
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void verifierUsesUnreservedCharacters() {
        String verifier = Pkce.generateVerifier();
        assertTrue(verifier.matches("[A-Za-z0-9\\-._~]{43,128}"), verifier);
        assertNotEquals(verifier, Pkce.generateVerifier());
    }
}
