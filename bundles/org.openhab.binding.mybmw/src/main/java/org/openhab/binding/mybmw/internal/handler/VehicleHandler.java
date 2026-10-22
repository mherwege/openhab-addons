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

import static org.openhab.binding.mybmw.internal.MyBMWBindingConstants.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.data.TelematicChannel;
import org.openhab.binding.mybmw.internal.data.TelematicStateConverter;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Handler of a single vehicle. It receives telematic values from its {@link AccountHandler}, from the stream and from
 * REST refreshes, and turns them into channel states. CarData is read-only, so the vehicle accepts no commands.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class VehicleHandler extends BaseThingHandler {

    /** Basic data members published as thing properties */
    private static final Map<String, String> BASIC_DATA_PROPERTIES = Map.of( //
            "brand", PROPERTY_BRAND, //
            "modelName", PROPERTY_MODEL_NAME, //
            "series", PROPERTY_SERIES, //
            "bodyType", PROPERTY_BODY_TYPE, //
            "propulsionType", PROPERTY_PROPULSION_TYPE, //
            "driveTrain", PROPERTY_DRIVE_TRAIN, //
            "constructionDate", PROPERTY_CONSTRUCTION_DATE, //
            "countryCodeISO", PROPERTY_COUNTRY);

    private final Logger logger = LoggerFactory.getLogger(VehicleHandler.class);

    private String vin = "";

    /** Guards the fields below; separate from the handler's own monitor, which the framework uses */
    private final Object dataLock = new Object();
    private final Map<String, Instant> descriptorTimestamps = new HashMap<>();
    private @Nullable BigDecimal latitude;
    private @Nullable BigDecimal longitude;
    private @Nullable BigDecimal altitude;
    private @Nullable Instant lastUpdate;

    /** Last state per channel id, to answer REFRESH without waiting for the car */
    private final Map<String, State> stateCache = new ConcurrentHashMap<>();

    public VehicleHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        String configuredVin = getConfigAs(VehicleConfiguration.class).vin.strip().toUpperCase(Locale.ROOT);
        if (configuredVin.length() != 17) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.invalid-vin");
            return;
        }
        vin = configuredVin;
        updateStatusFromBridge(getBridgeStatus());
    }

    @Override
    public void dispose() {
        synchronized (dataLock) {
            descriptorTimestamps.clear();
            latitude = null;
            longitude = null;
            altitude = null;
            lastUpdate = null;
        }
        stateCache.clear();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (!vin.isEmpty()) {
            updateStatusFromBridge(bridgeStatusInfo.getStatus());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            State state = stateCache.get(channelUID.getId());
            if (state != null) {
                updateState(channelUID, state);
            }
        }
    }

    /**
     * @return the VIN of this vehicle, empty while the configuration is invalid
     */
    public String getVin() {
        return vin;
    }

    /**
     * @return whether the thing has no properties from the basic data endpoint yet
     */
    public boolean needsBasicData() {
        return !getThing().getProperties().containsKey(PROPERTY_MODEL_NAME);
    }

    /**
     * Publishes the vehicle's basic data as thing properties.
     *
     * @param basicData response of the basic data endpoint
     */
    public void updateBasicData(JsonObject basicData) {
        Map<String, String> properties = editProperties();
        BASIC_DATA_PROPERTIES.forEach((member, property) -> {
            JsonElement element = basicData.get(member);
            if (element != null && element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                properties.put(property, element.getAsString());
            }
        });
        // marks the basic data as fetched, even when BMW leaves the model name out
        properties.putIfAbsent(PROPERTY_MODEL_NAME, "");
        updateProperties(properties);
    }

    /**
     * Records how the vehicle is mapped to the account.
     *
     * @param mappingType {@code PRIMARY} or {@code SECONDARY}
     */
    public void updateMappingType(String mappingType) {
        updateProperty(PROPERTY_MAPPING_TYPE, mappingType);
    }

    /**
     * Applies new telematic values. Values older than what the channel already shows are ignored, so a REST refresh
     * that arrives after newer stream data cannot set a channel back.
     *
     * @param values the values, from the stream or the REST API
     */
    public void onTelematicData(List<TelematicValue> values) {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            return;
        }
        List<Map.Entry<String, State>> updates = new ArrayList<>();
        synchronized (dataLock) {
            boolean locationChanged = false;
            for (TelematicValue value : values) {
                TelematicChannel mapping = TelematicChannel.fromDescriptor(value.descriptor());
                if (mapping == null) {
                    logger.trace("Vehicle {}: no channel for descriptor {}", vin, value.descriptor());
                    continue;
                }
                if (!isNewer(value)) {
                    continue;
                }
                switch (mapping.getKind()) {
                    case LATITUDE -> {
                        latitude = toCoordinate(value);
                        locationChanged = true;
                    }
                    case LONGITUDE -> {
                        longitude = toCoordinate(value);
                        locationChanged = true;
                    }
                    case ALTITUDE -> {
                        altitude = toCoordinate(value);
                        locationChanged = true;
                    }
                    default -> {
                        updates.add(Map.entry(mapping.getChannelId(), TelematicStateConverter.toState(mapping, value)));
                        String detailChannelId = mapping.getDetailChannelId();
                        if (detailChannelId != null) {
                            updates.add(Map.entry(detailChannelId, TelematicStateConverter.toDetailState(value)));
                        }
                    }
                }
            }
            if (locationChanged) {
                State position = position();
                if (position != null) {
                    updates.add(Map.entry(GROUP_LOCATION + "#" + CHANNEL_POSITION, position));
                }
            }
            Instant newest = null;
            for (TelematicValue value : values) {
                Instant timestamp = value.timestamp();
                if (timestamp != null && (newest == null || timestamp.isAfter(newest))) {
                    newest = timestamp;
                }
            }
            Instant previous = lastUpdate;
            if (newest != null && (previous == null || newest.isAfter(previous))) {
                lastUpdate = newest;
                updates.add(Map.entry(GROUP_STATUS + "#" + CHANNEL_LAST_UPDATE, new DateTimeType(newest)));
            }
        }
        for (Map.Entry<String, State> update : updates) {
            stateCache.put(update.getKey(), update.getValue());
            updateState(update.getKey(), update.getValue());
        }
    }

    /**
     * Must be called holding the data lock. Records the value's timestamp when it is not older than the last one.
     */
    private boolean isNewer(TelematicValue value) {
        Instant timestamp = value.timestamp();
        if (timestamp == null) {
            // without a timestamp the value cannot be ordered, treat it as current
            return true;
        }
        Instant previous = descriptorTimestamps.get(value.descriptor());
        if (previous != null && timestamp.isBefore(previous)) {
            logger.trace("Vehicle {}: ignoring {} from {}, already have {}", vin, value.descriptor(), timestamp,
                    previous);
            return false;
        }
        descriptorTimestamps.put(value.descriptor(), timestamp);
        return true;
    }

    private static @Nullable BigDecimal toCoordinate(TelematicValue value) {
        JsonElement raw = value.value();
        return TelematicStateConverter.isInvalid(raw) ? null : TelematicStateConverter.toNumber(raw);
    }

    /**
     * Must be called holding the data lock.
     */
    private @Nullable State position() {
        BigDecimal lat = latitude;
        BigDecimal lon = longitude;
        if (lat == null || lon == null) {
            return null;
        }
        BigDecimal alt = altitude;
        return alt != null ? new PointType(new DecimalType(lat), new DecimalType(lon), new DecimalType(alt))
                : new PointType(new DecimalType(lat), new DecimalType(lon));
    }

    private @Nullable ThingStatus getBridgeStatus() {
        Bridge bridge = getBridge();
        return bridge != null ? bridge.getStatus() : null;
    }

    private void updateStatusFromBridge(@Nullable ThingStatus bridgeStatus) {
        if (bridgeStatus == null) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.no-bridge");
        } else if (bridgeStatus == ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
