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
package org.openhab.binding.mybmw.internal.api.dto;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A vehicle mapped to the customer account, from {@code GET /customers/vehicles/mappings}.
 *
 * @author Mark Herwege - Initial contribution
 */
public class VehicleMapping {

    public static final String PRIMARY = "PRIMARY";

    public @Nullable String vin;
    public @Nullable String mappedSince;
    // PRIMARY or SECONDARY - CarData only serves data to the primary user
    public @Nullable String mappingType;
}
