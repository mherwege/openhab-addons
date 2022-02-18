/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.nikohomecontrol.internal.handler;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;
import static org.openhab.core.library.unit.Units.KILOWATT_HOUR;
import static org.openhab.core.types.RefreshType.REFRESH;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcMeter;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcMeterEvent;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.MeterType;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc1.NhcMeter1;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcMeter2;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NikoHomeControlMeterHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlMeterHandler extends NikoHomeControlBaseHandler implements NhcMeterEvent {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlMeterHandler.class);

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private volatile @Nullable NhcMeter nhcMeter;

    private String meterId = "";

    public NikoHomeControlMeterHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommandSelection(ChannelUID channelUID, Command command) {
        NhcMeter nhcMeter = this.nhcMeter;
        if (nhcMeter == null) {
            logger.debug("meter with ID {} not initialized", meterId);
            return;
        }

        if (REFRESH.equals(command)) {
            switch (channelUID.getId()) {
                case CHANNEL_POWER:
                    meterPowerEvent(nhcMeter.getPower());
                    break;
                case CHANNEL_ENERGY:
                case CHANNEL_GAS:
                case CHANNEL_WATER:
                case CHANNEL_ENERGY_DAY:
                case CHANNEL_GAS_DAY:
                case CHANNEL_WATER_DAY:
                case CHANNEL_ENERGY_LAST:
                case CHANNEL_GAS_LAST:
                case CHANNEL_WATER_LAST:
                    LocalDateTime lastReadingUTC = nhcMeter.getLastReading();
                    if (lastReadingUTC != null) {
                        meterReadingEvent(nhcMeter.getReading(), nhcMeter.getDayReading(), lastReadingUTC);
                    }
                    break;
                default:
                    logger.debug("unexpected command for channel {}", channelUID.getId());
            }
        }
    }

    @Override
    public void initialize() {
        NikoHomeControlMeterConfig config = getConfig().as(NikoHomeControlMeterConfig.class);
        meterId = config.energyMeterId;

        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "@text/offline.bridge-unitialized");
            return;
        }

        // We need to do this in a separate thread because we may have to wait for the
        // communication to become active
        scheduler.submit(() -> {
            if (!nhcComm.communicationActive()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/offline.communication-error");
                return;
            }

            NhcMeter meter = nhcComm.getMeters().get(meterId);
            if (meter == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/offline.configuration-error.meterId");
                return;
            }

            meter.setEventHandler(this);

            updateProperties(meter);

            String location = meter.getLocation();
            if (thing.getLocation() == null) {
                thing.setLocation(location);
            }

            nhcMeter = meter;

            logger.debug("meter intialized {}", meterId);

            Bridge bridge = getBridge();
            if ((bridge != null) && (bridge.getStatus() == ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }

            nhcComm.startMeter(meterId, config.refresh, config.filterLast, config.offsetStart, config.align);
            // Subscribing to power readings starts an intensive data flow, therefore only do it when there is an item
            // linked to the channel
            if (isLinked(CHANNEL_POWER)) {
                nhcComm.startMeterLive(meterId);
            }
        });
    }

    @Override
    public void dispose() {
        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm != null) {
            nhcComm.stopMeterLive(meterId);
            nhcComm.stopMeter(meterId);
            NhcMeter meter = nhcComm.getMeters().get(meterId);
            if (meter != null) {
                meter.unsetEventHandler();
            }
        }
        nhcMeter = null;
        super.dispose();
    }

    private void updateProperties(NhcMeter nhcMeter) {
        Map<String, String> properties = new HashMap<>();

        if (nhcMeter instanceof NhcMeter1) {
            NhcMeter1 meter = (NhcMeter1) nhcMeter;
            properties.put("type", meter.getMeterType());
            LocalDateTime referenceDate = meter.getReferenceDate();
            if (referenceDate != null) {
                properties.put("startdateUTC", referenceDate.format(DATE_TIME_FORMAT));
            }
        } else if (nhcMeter instanceof NhcMeter2) {
            NhcMeter2 meter = (NhcMeter2) nhcMeter;
            properties.put("deviceType", meter.getDeviceType());
            properties.put("deviceTechnology", meter.getDeviceTechnology());
            properties.put("deviceModel", meter.getDeviceModel());
        }

        thing.setProperties(properties);
    }

    @Override
    public void meterPowerEvent(@Nullable Integer power) {
        NhcMeter nhcMeter = this.nhcMeter;
        if (nhcMeter == null) {
            logger.debug("meter with ID {} not initialized", meterId);
            return;
        }

        MeterType meterType = nhcMeter.getType();
        if (meterType != MeterType.ENERGY_LIVE) {
            logger.debug("meter with ID {} does not support live readings", meterId);
            return;
        }

        if (power == null) {
            updateState(CHANNEL_POWER, UnDefType.UNDEF);
        } else {
            updateState(CHANNEL_POWER, new QuantityType<>(power, Units.WATT));
        }
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void meterReadingEvent(double meterReading, double meterReadingDay, LocalDateTime lastReadingUTC) {
        NhcMeter nhcMeter = this.nhcMeter;
        if (nhcMeter == null) {
            logger.debug("meter with ID {} not initialized", meterId);
            return;
        }

        NikoHomeControlBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler == null) {
            logger.debug("Cannot update meter channels, no bridge handler");
            return;
        }
        ZonedDateTime lastReading = lastReadingUTC.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(bridgeHandler.getTimeZone());

        MeterType meterType = nhcMeter.getType();
        switch (meterType) {
            case ENERGY_LIVE:
            case ENERGY:
                updateState(CHANNEL_ENERGY, new QuantityType<>(meterReading, KILOWATT_HOUR));
                updateState(CHANNEL_ENERGY_DAY, new QuantityType<>(meterReadingDay, KILOWATT_HOUR));
                updateState(CHANNEL_ENERGY_LAST, new DateTimeType(lastReading));
                updateStatus(ThingStatus.ONLINE);
            case GAS:
                updateState(CHANNEL_GAS, new QuantityType<>(meterReading, SIUnits.CUBIC_METRE));
                updateState(CHANNEL_GAS_DAY, new QuantityType<>(meterReadingDay, SIUnits.CUBIC_METRE));
                updateState(CHANNEL_GAS_LAST, new DateTimeType(lastReading));
                updateStatus(ThingStatus.ONLINE);
                break;
            case WATER:
                updateState(CHANNEL_WATER, new QuantityType<>(meterReading, SIUnits.CUBIC_METRE));
                updateState(CHANNEL_WATER_DAY, new QuantityType<>(meterReadingDay, SIUnits.CUBIC_METRE));
                updateState(CHANNEL_WATER_LAST, new DateTimeType(lastReading));
                updateStatus(ThingStatus.ONLINE);
                break;
            default:
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/offline.configuration-error.meterType");
        }
    }

    @Override
    public void meterInitialized() {
        Bridge bridge = getBridge();
        if ((bridge != null) && (bridge.getStatus() == ThingStatus.ONLINE)) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public void meterRemoved() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                "@text/offline.configuration-error.meterRemoved");
    }

    @Override
    // Subscribing to power readings starts an intensive data flow, therefore only do it when there is an item linked to
    // the channel
    public void channelLinked(ChannelUID channelUID) {
        if (!CHANNEL_POWER.equals(channelUID.getId())) {
            return;
        }
        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm != null) {
            // This can be expensive, therefore do it in a job.
            scheduler.submit(() -> {
                if (!nhcComm.communicationActive()) {
                    restartCommunication(nhcComm);
                }

                if (nhcComm.communicationActive()) {
                    nhcComm.startMeterLive(meterId);
                    updateStatus(ThingStatus.ONLINE);
                }
            });
        }
    }

    @Override
    public void channelUnlinked(ChannelUID channelUID) {
        if (!CHANNEL_POWER.equals(channelUID.getId())) {
            return;
        }
        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm != null) {
            // This can be expensive, therefore do it in a job.
            scheduler.submit(() -> {
                if (!nhcComm.communicationActive()) {
                    restartCommunication(nhcComm);
                }

                if (nhcComm.communicationActive()) {
                    nhcComm.stopMeterLive(meterId);
                    // as this is momentary power production/consumption, we set it UNDEF as we do not get readings
                    // anymore
                    updateState(CHANNEL_POWER, UnDefType.UNDEF);
                    updateStatus(ThingStatus.ONLINE);
                }
            });
        }
    }
}
