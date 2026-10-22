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
package org.openhab.binding.mybmw.internal.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RetryBackoff}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class RetryBackoffTest {

    @Test
    void doublesUpToMaximumAndResets() {
        RetryBackoff backoff = new RetryBackoff(Duration.ofMinutes(1), Duration.ofMinutes(30));
        assertEquals(Duration.ofMinutes(1), backoff.next());
        assertEquals(Duration.ofMinutes(2), backoff.next());
        assertEquals(Duration.ofMinutes(4), backoff.next());
        assertEquals(Duration.ofMinutes(8), backoff.next());
        assertEquals(Duration.ofMinutes(16), backoff.next());
        assertEquals(Duration.ofMinutes(30), backoff.next());
        assertEquals(Duration.ofMinutes(30), backoff.next());

        backoff.reset();
        assertEquals(Duration.ofMinutes(1), backoff.next());
    }

    @Test
    void minimumRaisesShortDelays() {
        RetryBackoff backoff = new RetryBackoff(Duration.ofMinutes(1), Duration.ofMinutes(30));
        assertEquals(Duration.ofMinutes(5), backoff.next(Duration.ofMinutes(5)));
        assertEquals(Duration.ofMinutes(5), backoff.next(Duration.ofMinutes(5)));
        assertEquals(Duration.ofMinutes(5), backoff.next(Duration.ofMinutes(5)));
        assertEquals(Duration.ofMinutes(8), backoff.next(Duration.ofMinutes(5)));
    }
}
