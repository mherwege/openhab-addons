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
 * State of the path from the CarData stream to the binding.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public enum StreamState {
    /** Not connected to the broker the binding subscribes to */
    DISCONNECTED,
    /** Connected to the local broker, but the bridge reports it is not connected to BMW */
    UPSTREAM_DISCONNECTED,
    /** Connected to the local broker, the bridge state is not known yet */
    CONNECTED_UPSTREAM_UNKNOWN,
    /** Data can flow from BMW to the binding */
    CONNECTED
}
