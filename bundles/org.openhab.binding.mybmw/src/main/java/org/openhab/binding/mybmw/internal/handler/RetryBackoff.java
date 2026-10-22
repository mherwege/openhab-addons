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

import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Exponential backoff for retrying a failing operation: each consecutive failure doubles the delay, up to a maximum,
 * until a success resets it.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class RetryBackoff {

    private final Duration initial;
    private final Duration max;
    private Duration next;

    /**
     * @param initial delay after the first failure
     * @param max longest delay
     */
    RetryBackoff(Duration initial, Duration max) {
        this.initial = initial;
        this.max = max;
        this.next = initial;
    }

    /**
     * Records a failure.
     *
     * @return the delay before the next attempt
     */
    synchronized Duration next() {
        Duration delay = next;
        Duration doubled = next.multipliedBy(2);
        next = doubled.compareTo(max) > 0 ? max : doubled;
        return delay;
    }

    /**
     * Records a failure that should not be retried sooner than the given delay, e.g. a configuration error.
     *
     * @param minimum shortest delay to return
     * @return the delay before the next attempt
     */
    synchronized Duration next(Duration minimum) {
        Duration delay = next();
        return delay.compareTo(minimum) < 0 ? minimum : delay;
    }

    /**
     * Records a success: the next failure starts again at the initial delay.
     */
    synchronized void reset() {
        next = initial;
    }
}
