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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.LogRedaction;
import org.openhab.binding.mybmw.internal.data.TelematicChannel;
import org.openhab.binding.mybmw.internal.data.TelematicChannel.Drive;
import org.openhab.binding.mybmw.internal.data.TelematicStateConverter;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
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
    private static final Map<String, String> BASIC_DATA_PROPERTIES = Map.ofEntries( //
            Map.entry("brand", PROPERTY_BRAND), //
            Map.entry("modelName", PROPERTY_MODEL_NAME), //
            Map.entry("series", PROPERTY_SERIES), //
            Map.entry("bodyType", PROPERTY_BODY_TYPE), //
            Map.entry("propulsionType", PROPERTY_PROPULSION_TYPE), //
            Map.entry("driveTrain", PROPERTY_DRIVE_TRAIN), //
            Map.entry("constructionDate", PROPERTY_CONSTRUCTION_DATE), //
            Map.entry("countryCode", PROPERTY_COUNTRY), //
            Map.entry("colourDescription", PROPERTY_COLOR), //
            Map.entry("chargingModes", PROPERTY_CHARGING_MODES), //
            Map.entry("hasNavi", PROPERTY_HAS_NAVIGATION), //
            Map.entry("hasSunRoof", PROPERTY_HAS_SUNROOF), //
            Map.entry("modelKey", PROPERTY_MODEL_KEY), //
            Map.entry("seriesDevt", PROPERTY_DEVELOPMENT_SERIES), //
            Map.entry("headUnit", PROPERTY_HEAD_UNIT), //
            Map.entry("simStatus", PROPERTY_SIM_STATUS), //
            Map.entry("isTelematicsCapable", PROPERTY_TELEMATICS_CAPABLE));

    private final Logger logger = LoggerFactory.getLogger(VehicleHandler.class);

    private static final String SIM_ACTIVE = "ACTIVE";

    private String vin = "";

    /** Guards the fields below; separate from the handler's own monitor, which the framework uses */
    private final Object dataLock = new Object();
    private final Map<String, Instant> descriptorTimestamps = new HashMap<>();
    private final Map<String, JsonElement> descriptorValues = new HashMap<>();
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
        removeIrrelevantChannels();
        updateStatusFromBridge(getBridgeStatus());
    }

    @Override
    public void dispose() {
        synchronized (dataLock) {
            descriptorTimestamps.clear();
            descriptorValues.clear();
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
     * @param basicData the vehicle's basic data
     * @return whether the vehicle can send data to CarData; {@code true} when BMW does not say
     */
    public static boolean isTelematicsCapable(JsonObject basicData) {
        JsonElement capable = basicData.get("isTelematicsCapable");
        return capable == null || !capable.isJsonPrimitive() || !"false".equalsIgnoreCase(capable.getAsString());
    }

    /**
     * @return whether the thing has no properties from the basic data endpoint yet; things that were set up before the
     *         sunroof property was added take the basic data once more, which the account serves from its cache
     */
    public boolean needsBasicData() {
        return !getThing().getProperties().containsKey(PROPERTY_HAS_SUNROOF);
    }

    /**
     * Publishes the vehicle's basic data as thing properties.
     *
     * @param basicData response of the basic data endpoint
     */
    public void updateBasicData(JsonObject basicData) {
        Map<String, String> properties = editProperties();
        BASIC_DATA_PROPERTIES.forEach((member, property) -> {
            String value = propertyValue(basicData.get(member));
            if (value != null) {
                properties.put(property, value);
            }
        });
        // marks the basic data as fetched, even when BMW leaves the sunroof out; an empty value means unknown
        properties.putIfAbsent(PROPERTY_HAS_SUNROOF, "");
        updateProperties(properties);
        if (!vin.isEmpty()) {
            removeIrrelevantChannels();
            updateStatusFromBridge(getBridgeStatus());
        }
    }

    /**
     * Removes the channels of equipment the basic data says the vehicle does not have: the sunroof, and the location,
     * which the vehicle reports through its navigation system. Channels are only removed when BMW explicitly reports
     * the equipment is missing; recreate the thing to get them back.
     */
    private void removeIrrelevantChannels() {
        List<Channel> channels = irrelevantChannelIds(getThing().getProperties()).stream()
                .map(id -> getThing().getChannel(id)).filter(Objects::nonNull).map(Objects::requireNonNull).toList();
        if (!channels.isEmpty()) {
            logger.debug("Vehicle {}: removing {} channels for equipment it does not have", LogRedaction.vin(vin),
                    channels.size());
            updateThing(editThing().withoutChannels(channels).build());
        }
    }

    /**
     * @param properties the thing properties
     * @return the ids of the channels that do not apply to the vehicle
     */
    static List<String> irrelevantChannelIds(Map<String, String> properties) {
        List<String> ids = new ArrayList<>();
        if ("false".equalsIgnoreCase(properties.get(PROPERTY_HAS_SUNROOF))) {
            ids.add(TelematicChannel.SUNROOF.getChannelId());
            ids.add(Objects.requireNonNull(TelematicChannel.SUNROOF.getDetailChannelId()));
        }
        if ("false".equalsIgnoreCase(properties.get(PROPERTY_HAS_NAVIGATION))) {
            ids.add(GROUP_LOCATION + "#" + CHANNEL_POSITION);
            ids.add(TelematicChannel.HEADING.getChannelId());
        }
        Set<Drive> drives = drives(properties);
        for (TelematicChannel mapping : TelematicChannel.values()) {
            if (mapping.getDrive() != Drive.ANY && !drives.contains(mapping.getDrive())) {
                ids.add(mapping.getChannelId());
                String detailChannelId = mapping.getDetailChannelId();
                if (detailChannelId != null) {
                    ids.add(detailChannelId);
                }
            }
        }
        return ids;
    }

    /**
     * Derives the drives of the vehicle from its basic data: the drive train, or the propulsion type when the drive
     * train is not known. Only {@code BEV} and {@code EL} have been seen; other values are matched by the catalogue's
     * abbreviations, and a value that is not recognized keeps all channels.
     *
     * @param properties the thing properties
     * @return the drives the vehicle has
     */
    static Set<Drive> drives(Map<String, String> properties) {
        Set<Drive> drives = drives(properties.get(PROPERTY_DRIVE_TRAIN));
        return drives != null ? drives
                : Objects.requireNonNullElse(drives(properties.get(PROPERTY_PROPULSION_TYPE)),
                        EnumSet.of(Drive.ELECTRIC, Drive.COMBUSTION));
    }

    private static @Nullable Set<Drive> drives(@Nullable String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String upperCase = type.strip().toUpperCase(Locale.ROOT);
        if (upperCase.contains("PHEV") || upperCase.contains("PLUGIN") || upperCase.contains("PLUG_IN")) {
            return EnumSet.of(Drive.ELECTRIC, Drive.COMBUSTION);
        }
        if ("BEV".equals(upperCase) || "EL".equals(upperCase) || "ELECTRIC".equals(upperCase)) {
            return EnumSet.of(Drive.ELECTRIC);
        }
        if ("CE".equals(upperCase) || "ICE".equals(upperCase) || "COMBUSTION".equals(upperCase)
                || upperCase.contains("MHEV") || upperCase.contains("MILD") || "HEV".equals(upperCase)
                || "CONV".equals(upperCase) || "CONVENTIONAL".equals(upperCase)) {
            return EnumSet.of(Drive.COMBUSTION);
        }
        return null;
    }

    /**
     * @return the text of a basic data value, a list joined with commas, or {@code null} when it is empty
     */
    public static @Nullable String propertyValue(@Nullable JsonElement element) {
        String value = null;
        if (element != null && element.isJsonPrimitive()) {
            value = element.getAsString();
        } else if (element != null && element.isJsonArray()) {
            List<String> items = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    items.add(item.getAsString());
                }
            }
            value = String.join(", ", items);
        }
        return value == null || value.isBlank() ? null : value;
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
                    logger.trace("Vehicle {}: no channel for descriptor {}", LogRedaction.vin(vin), value.descriptor());
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
     * A value identical to the last one, with the same timestamp, is a copy and is not applied again: the stream
     * delivers many values twice.
     */
    private boolean isNewer(TelematicValue value) {
        Instant timestamp = value.timestamp();
        if (timestamp == null) {
            // without a timestamp the value cannot be ordered, treat it as current
            return true;
        }
        Instant previous = descriptorTimestamps.get(value.descriptor());
        if (previous != null && timestamp.isBefore(previous)) {
            logger.trace("Vehicle {}: ignoring {} from {}, already have {}", LogRedaction.vin(vin), value.descriptor(),
                    timestamp, previous);
            return false;
        }
        if (timestamp.equals(previous) && value.value().equals(descriptorValues.get(value.descriptor()))) {
            return false;
        }
        descriptorTimestamps.put(value.descriptor(), timestamp);
        descriptorValues.put(value.descriptor(), value.value());
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
        Map<String, String> properties = getThing().getProperties();
        String simStatus = properties.get(PROPERTY_SIM_STATUS);
        if ("false".equalsIgnoreCase(properties.get(PROPERTY_TELEMATICS_CAPABLE))) {
            // basic data from BMW, so it is known regardless of the bridge
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.not-telematics-capable");
        } else if (bridgeStatus == null) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.no-bridge");
        } else if (bridgeStatus == ThingStatus.ONLINE) {
            if (simStatus != null && !simStatus.isBlank() && !SIM_ACTIVE.equalsIgnoreCase(simStatus)) {
                updateStatus(ThingStatus.ONLINE,
                        "@text/online.sim-not-active [\"" + simStatus.replace("\"", "'") + "\"]");
            } else {
                updateStatus(ThingStatus.ONLINE);
            }
        } else {
            updateStatus(ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }
}
