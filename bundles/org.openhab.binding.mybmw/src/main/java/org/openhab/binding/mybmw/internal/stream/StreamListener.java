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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.data.TelematicValue;

/**
 * Receives what a {@link StreamSource} produces. Callbacks arrive on the MQTT client's threads and must return quickly.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public interface StreamListener {

    /**
     * New data points for a vehicle.
     *
     * @param vin vehicle identification number
     * @param values data points
     */
    void onTelematicData(String vin, List<TelematicValue> values);

    /**
     * The stream state changed.
     *
     * @param state new state
     * @param detail human readable reason, e.g. the connection error
     */
    void onStreamStateChanged(StreamState state, @Nullable String detail);
}
