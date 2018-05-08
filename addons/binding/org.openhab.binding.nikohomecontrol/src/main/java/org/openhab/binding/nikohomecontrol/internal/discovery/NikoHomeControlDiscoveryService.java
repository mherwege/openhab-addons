/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.discovery;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.util.Date;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAction;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcThermostat;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If a Niko Home Control bridge is added or if the user scans manually for things this
 * {@link NikoHomeControlDiscoveryService} is used to return Niko Home Control Actions as things to the framework.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlDiscoveryService extends AbstractDiscoveryService {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlDiscoveryService.class);

    private static final int TIMEOUT = 5;

    private ThingUID bridgeUID;
    @Nullable
    private NikoHomeControlBridgeHandler handler;

    public NikoHomeControlDiscoveryService(NikoHomeControlBridgeHandler handler) {
        super(SUPPORTED_THING_TYPES_UIDS, TIMEOUT, false);
        logger.debug("Niko Home Control: discovery service {}", handler);
        this.bridgeUID = handler.getThing().getUID();
        this.handler = handler;
    }

    public void activate() {
        this.handler.setNhcDiscovery(this);
    }

    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime());
        this.handler.setNhcDiscovery(null);
        this.handler = null;
    }

    /**
     * Discovers devices connected to a Niko Home Control controller
     */
    public void discoverDevices() {
        NikoHomeControlCommunication nhcComm = this.handler.getCommunication();

        if ((nhcComm == null) || !nhcComm.communicationActive()) {
            logger.warn("Niko Home Control: not connected.");
            return;
        }
        logger.debug("Niko Home Control: getting devices on {}", this.handler.getThing().getUID().getId());

        Map<String, NhcAction> actions = nhcComm.getActions();

        for (Map.Entry<String, NhcAction> action : actions.entrySet()) {

            String actionId = action.getKey();
            NhcAction nhcAction = action.getValue();
            String thingName = nhcAction.getName();
            String thingLocation = nhcAction.getLocation();

            switch (nhcAction.getType()) {
                case RELAY:
                    addActionDevice(new ThingUID(THING_TYPE_ON_OFF_LIGHT, this.handler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);

                    break;
                case DIMMER:
                    addActionDevice(new ThingUID(THING_TYPE_DIMMABLE_LIGHT, this.handler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                case ROLLERSHUTTER:
                    addActionDevice(new ThingUID(THING_TYPE_BLIND, this.handler.getThing().getUID(), actionId),
                            actionId, thingName, thingLocation);
                    break;
                default:
                    logger.debug("Niko Home Control: unrecognized action type {} for {} {}", nhcAction.getType(),
                            actionId, thingName);
            }
        }

        Map<String, NhcThermostat> thermostats = nhcComm.getThermostats();

        for (Map.Entry<String, NhcThermostat> thermostatEntry : thermostats.entrySet()) {

            String thermostatId = thermostatEntry.getKey();
            NhcThermostat nhcThermostat = thermostatEntry.getValue();
            String thingName = nhcThermostat.getName();
            String thingLocation = nhcThermostat.getLocation();
            addThermostatDevice(new ThingUID(THING_TYPE_THERMOSTAT, this.handler.getThing().getUID(), thermostatId),
                    thermostatId, thingName, thingLocation);
        }
    }

    private void addActionDevice(ThingUID uid, String actionId, String thingName, @Nullable String thingLocation) {
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID)
                .withLabel(thingName).withProperty(CONFIG_ACTION_ID, actionId);
        if (thingLocation != null) {
            discoveryResultBuilder.withProperty("Location", thingLocation);
        }
        thingDiscovered(discoveryResultBuilder.build());
    }

    private void addThermostatDevice(ThingUID uid, String thermostatId, String thingName,
            @Nullable String thingLocation) {
        DiscoveryResultBuilder discoveryResultBuilder = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID)
                .withLabel(thingName).withProperty(CONFIG_THERMOSTAT_ID, thermostatId);
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
}
