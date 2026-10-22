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
package org.openhab.binding.mybmw.internal.data;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * A single telematic data point, as delivered by the CarData stream or the telematic data REST endpoint. Both sources
 * use the same shape: a value with an optional unit and timestamp, keyed by its technical descriptor.
 * <p>
 * The value is kept as a {@link JsonElement} because its JSON type differs per descriptor and per source: the stream
 * sends numbers, booleans, strings and occasionally objects, while the REST endpoint sends every value as a string.
 *
 * @param descriptor technical descriptor, e.g. {@code vehicle.vehicle.travelledDistance}
 * @param value the raw value
 * @param unit the unit as sent by BMW, if any
 * @param timestamp the time the vehicle reported the value, if known
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public record TelematicValue(String descriptor, JsonElement value, @Nullable String unit, @Nullable Instant timestamp) {
}
