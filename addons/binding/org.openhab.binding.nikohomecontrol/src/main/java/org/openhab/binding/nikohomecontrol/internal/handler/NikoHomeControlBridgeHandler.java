/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.handler;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.net.InetAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.nikohomecontrol.internal.discovery.NikoHomeControlDiscoveryService;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcController;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NikoHomeControlBridgeHandler} is an abstract class representing a handler to all different interfaces to the
 * Niko Home
 * Control System. {@link NhcIBridgeHandler} or {@link NhcIIBridgeHandler} should be used for
 * the respective version of Niko Home Control.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public abstract class NikoHomeControlBridgeHandler extends BaseBridgeHandler implements NhcController {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlBridgeHandler.class);

    @Nullable
    protected volatile NikoHomeControlCommunication nhcComm;

    @Nullable
    private ScheduledFuture<?> refreshTimer;

    @Nullable
    protected NikoHomeControlDiscoveryService nhcDiscovery;

    public NikoHomeControlBridgeHandler(Bridge nikoHomeControlBridge) {
        super(nikoHomeControlBridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // There is nothing to handle in the bridge handler
    }

    /**
     * Create communication object to Niko Home Control IP-interface and start communication.
     * Trigger discovery when communication setup is successful.
     */
    protected void startCommunication(NikoHomeControlCommunication comm) {
        Configuration config = this.getConfig();

        scheduler.submit(() -> {
            this.nhcComm = comm;

            // Set callback from NikoHomeControlCommunication object to this bridge to be able to take bridge
            // offline when non-resolvable communication error occurs.
            setNhcController();

            comm.startCommunication();
            if (!comm.communicationActive()) {
                nhcComm = null;
                bridgeOffline();
                return;
            }

            updateProperties();

            updateStatus(ThingStatus.ONLINE);

            Integer refreshInterval = ((Number) config.get(CONFIG_REFRESH)).intValue();
            setupRefreshTimer(refreshInterval);

            if (nhcDiscovery != null) {
                nhcDiscovery.discoverDevices();
            } else {
                logger.debug("Niko Home Control: cannot discover devices, discovery service not started");
            }
        });
    }

    private void setNhcController() {
        if (nhcComm != null) {
            nhcComm.setNhcController(this);
        }
    }

    /**
     * Schedule future communication refresh.
     *
     * @param interval_config Time before refresh in minutes.
     */
    private void setupRefreshTimer(@Nullable Integer refreshInterval) {
        if (this.refreshTimer != null) {
            this.refreshTimer.cancel(true);
            this.refreshTimer = null;
        }

        if ((refreshInterval == null) || (refreshInterval == 0)) {
            return;
        }

        // This timer will restart the bridge connection periodically
        logger.debug("Niko Home Control: restart bridge connection every {} min", refreshInterval);
        this.refreshTimer = scheduler.scheduleWithFixedDelay(() -> {
            logger.debug("Niko Home Control: restart communication at scheduled time");

            NikoHomeControlCommunication comm = nhcComm;
            if (comm != null) {
                comm.restartCommunication();
                if (!comm.communicationActive()) {
                    logger.debug("Niko Home Control: communication socket error");
                    bridgeOffline();
                    return;
                }

                updateProperties();

                updateStatus(ThingStatus.ONLINE);
            }
        }, refreshInterval, refreshInterval, TimeUnit.MINUTES);
    }

    /**
     * Take bridge offline when error in communication with Niko Home Control IP-interface. This method can also be
     * called directly from {@link NikoHomeControlCommunication} object.
     */
    public void bridgeOffline() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                "Niko Home Control: error starting bridge connection");
    }

    /**
     * Put bridge online when error in communication resolved.
     */
    public void bridgeOnline() {
        updateProperties();
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void controllerOffline() {
        bridgeOffline();

    }

    /**
     * Update bridge properties with properties returned from Niko Home Control Controller, so they can be made visible
     * in PaperUI.
     */
    abstract protected void updateProperties();

    @Override
    public void dispose() {
        if (this.refreshTimer != null) {
            this.refreshTimer.cancel(true);
        }
        this.refreshTimer = null;
        if (this.nhcComm != null) {
            nhcComm.stopCommunication();
        }
        this.nhcComm = null;
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters) {
        Configuration configuration = editConfiguration();
        for (Entry<String, Object> configurationParmeter : configurationParameters.entrySet()) {
            configuration.put(configurationParmeter.getKey(), configurationParmeter.getValue());
        }
        updateConfiguration(configuration);

        scheduler.submit(() -> {
            NikoHomeControlCommunication comm = nhcComm;

            if (comm != null) {
                comm.restartCommunication();
                if (!comm.communicationActive()) {
                    bridgeOffline();
                    return;
                }

                updateProperties();

                updateStatus(ThingStatus.ONLINE);

                Integer refreshInterval = ((Number) configuration.get(CONFIG_REFRESH)).intValue();
                setupRefreshTimer(refreshInterval);
            }
        });
    }

    /**
     * Set discovery service handler to be able to start discovery after bridge initialization.
     *
     * @param nhcDiscovery
     */
    public void setNhcDiscovery(@Nullable NikoHomeControlDiscoveryService nhcDiscovery) {
        this.nhcDiscovery = nhcDiscovery;
    }

    /**
     * Send a trigger from an alarm received from Niko Home Control.
     *
     * @param Niko Home Control alarm message
     */
    @Override
    public void alarmEvent(String alarmText) {
        triggerChannel(CHANNEL_ALARM, alarmText);
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Send a trigger from a notice received from Niko Home Control.
     *
     * @param Niko Home Control alarm message
     */
    @Override
    public void noticeEvent(String alarmText) {
        triggerChannel(CHANNEL_NOTICE, alarmText);
        updateStatus(ThingStatus.ONLINE);
    }

    /**
     * Get the Niko Home Control communication object.
     *
     * @return Niko Home Control communication object
     */
    public @Nullable NikoHomeControlCommunication getCommunication() {
        return this.nhcComm;
    }

    @Override
    public @Nullable InetAddress getAddr() {
        return null;
    }

    @Override
    public @Nullable Integer getPort() {
        return null;
    }
}
