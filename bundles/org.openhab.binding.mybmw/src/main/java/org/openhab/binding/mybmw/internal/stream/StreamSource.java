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
package org.openhab.binding.mybmw.internal.stream;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A source of CarData stream messages. Today this is a local broker fed by the bmw-mqtt-bridge; a direct connection
 * to the BMW broker will be a second implementation.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public interface StreamSource {

    /**
     * Starts connecting. Returns immediately, progress is reported to the {@link StreamListener}.
     */
    void start();

    /**
     * Disconnects and releases all resources. No state callbacks are made after this returns; a data callback that was
     * already being delivered may still complete, so listeners must tolerate data after stopping the source.
     */
    void stop();
}
