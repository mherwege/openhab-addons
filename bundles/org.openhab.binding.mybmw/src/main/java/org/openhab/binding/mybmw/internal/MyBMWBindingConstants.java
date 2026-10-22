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
package org.openhab.binding.mybmw.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link MyBMWBindingConstants} class defines common constants, which are used across the whole binding.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class MyBMWBindingConstants {

    public static final String BINDING_ID = "mybmw";

    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_VEHICLE = new ThingTypeUID(BINDING_ID, "vehicle");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_VEHICLE);

    // Vehicle configuration and representation property
    public static final String CONFIG_VIN = "vin";

    // Vehicle properties, filled from the CarData basic data endpoint
    public static final String PROPERTY_BRAND = "brand";
    public static final String PROPERTY_MODEL_NAME = "modelName";
    public static final String PROPERTY_SERIES = "series";
    public static final String PROPERTY_BODY_TYPE = "bodyType";
    public static final String PROPERTY_PROPULSION_TYPE = "propulsionType";
    public static final String PROPERTY_DRIVE_TRAIN = "driveTrain";
    public static final String PROPERTY_CONSTRUCTION_DATE = "constructionDate";
    public static final String PROPERTY_COUNTRY = "countryCode";
    public static final String PROPERTY_MAPPING_TYPE = "mappingType";

    // Channel groups and channels that are not driven by a single telematic descriptor
    public static final String GROUP_STATUS = "status";
    public static final String GROUP_LOCATION = "location";
    public static final String CHANNEL_LAST_UPDATE = "last-update";
    public static final String CHANNEL_POSITION = "position";

    private MyBMWBindingConstants() {
        // constants only
    }
}
