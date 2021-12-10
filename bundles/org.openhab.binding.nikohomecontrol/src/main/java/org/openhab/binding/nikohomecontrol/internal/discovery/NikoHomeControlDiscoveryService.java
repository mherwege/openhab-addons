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
package org.openhab.binding.nikohomecontrol.internal.discovery;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAction;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcEnergyMeter;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcThermostat;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If a Niko Home Control bridge is added or if the user scans manually for things this
 * {@link NikoHomeControlDiscoveryService} is used to return Niko Home Control Actions as things to the framework.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
@Component(service = ThingHandlerService.class)
public class NikoHomeControlDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlDiscoveryService.class);

    private volatile @Nullable ScheduledFuture<?> nhcDiscoveryJob;

    private static final int TIMEOUT_SECONDS = 5;
    private static final int REFRESH_INTERVAL_SECONDS = 60;

    private @Nullable ThingUID bridgeUID;
    private @Nullable NikoHomeControlBridgeHandler handler;

    public NikoHomeControlDiscoveryService() {
        super(SUPPORTED_THING_TYPES_UIDS, TIMEOUT_SECONDS, true);
        logger.debug("device discovery service started");
        super.activate(null); // Makes sure the background discovery for devices is enabled
    }

    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime());
        super.deactivate();
    }

    /**
     * Discovers devices connected to a Niko Home Control controller
     */
    public void discoverDevices() {
        NikoHomeControlBridgeHandler bridgeHandler = handler;
        if (bridgeHandler == null) {
            return;
        }

        NikoHomeControlCommunication nhcComm = bridgeHandler.getCommunication();

        if ((nhcComm == null) || !nhcComm.communicationActive()) {
            logger.warn("not connected");
            return;
        }
        logger.debug("getting devices on {}", bridgeHandler.getThing().getUID().getId());

        Map<String, NhcAction> actions = nhcComm.getActions();

        actions.forEach((actionId, nhcAction) -> {
            String thingName = nhcAction.getName();
            String thingLocation = nhcAction.getLocation();

            switch (nhcAction.getType()) {
                case TRIGGER:
                    addActionDevice(new ThingUID(THING_TYPE_PUSHBUTTON, bridgeHandler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                case RELAY:
                    addActionDevice(new ThingUID(THING_TYPE_ON_OFF_LIGHT, bridgeHandler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                case DIMMER:
                    addActionDevice(
                            new ThingUID(THING_TYPE_DIMMABLE_LIGHT, bridgeHandler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                case ROLLERSHUTTER:
                    addActionDevice(new ThingUID(THING_TYPE_BLIND, bridgeHandler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                default:
                    logger.debug("unrecognized action type {} for {} {}", nhcAction.getType(), actionId, thingName);
            }
        });

        Map<String, NhcThermostat> thermostats = nhcComm.getThermostats();

        thermostats.forEach((thermostatId, nhcThermostat) -> {
            String thingName = nhcThermostat.getName();
            String thingLocation = nhcThermostat.getLocation();
            addThermostatDevice(new ThingUID(THING_TYPE_THERMOSTAT, bridgeHandler.getThing().getUID(), thermostatId),
                    thermostatId, thingName, thingLocation);
        });

        Map<String, NhcEnergyMeter> energyMeters = nhcComm.getEnergyMeters();

        energyMeters.forEach((energyMeterId, nhcEnergyMeter) -> {
            String thingName = nhcEnergyMeter.getName();
            String thingLocation = nhcEnergyMeter.getLocation();
            addEnergyMeterDevice(new ThingUID(THING_TYPE_ENERGYMETER, bridgeHandler.getThing().getUID(), energyMeterId),
                    energyMeterId, thingName, thingLocation);
        });
    }

    private void addActionDevice(ThingUID uid, String actionId, String thingName, @Nullable String thingLocation) {
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID)
                .withLabel(thingName).withProperty(CONFIG_ACTION_ID, actionId)
                .withRepresentationProperty(CONFIG_ACTION_ID);
        if (thingLocation != null) {
            discoveryResultBuilder.withProperty("Location", thingLocation);
        }
        thingDiscovered(discoveryResultBuilder.build());
    }

    private void addThermostatDevice(ThingUID uid, String thermostatId, String thingName,
            @Nullable String thingLocation) {
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID)
                .withLabel(thingName).withProperty(CONFIG_THERMOSTAT_ID, thermostatId)
                .withRepresentationProperty(CONFIG_THERMOSTAT_ID);
        if (thingLocation != null) {
            discoveryResultBuilder.withProperty("Location", thingLocation);
        }
        thingDiscovered(discoveryResultBuilder.build());
    }

    private void addEnergyMeterDevice(ThingUID uid, String energyMeterId, String thingName,
            @Nullable String thingLocation) {
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID)
                .withLabel(thingName).withProperty(CONFIG_ENERGYMETER_ID, energyMeterId)
                .withRepresentationProperty(CONFIG_ENERGYMETER_ID);
        if (thingLocation != null) {
            discoveryResultBuilder.withProperty("Location", thingLocation);
        }
        thingDiscovered(discoveryResultBuilder.build());
    }

    @Override
    protected void startScan() {
        discoverDevices();
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Start device background discovery");
        ScheduledFuture<?> job = nhcDiscoveryJob;
        if (job == null || job.isCancelled()) {
            nhcDiscoveryJob = scheduler.scheduleWithFixedDelay(this::discoverDevices, 0, REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stop device background discovery");
        ScheduledFuture<?> job = nhcDiscoveryJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
            nhcDiscoveryJob = null;
        }
    }

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof NikoHomeControlBridgeHandler) {
            this.handler = (NikoHomeControlBridgeHandler) handler;
            bridgeUID = handler.getThing().getUID();
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }
}
