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
import static org.openhab.core.types.RefreshType.REFRESH;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAccess;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAccessEvent;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.AccessType;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcAccess2;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NikoHomeControlAccessHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlAccessHandler extends NikoHomeControlBaseHandler implements NhcAccessEvent {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlAccessHandler.class);

    private volatile @Nullable NhcAccess nhcAccess;

    public NikoHomeControlAccessHandler(Thing thing) {
        super(thing);
    }

    @Override
    void handleCommandSelection(ChannelUID channelUID, Command command) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug("access device with ID {} not initialized", deviceId);
            return;
        }

        logger.debug("handle command {} for {}", command, channelUID);

        switch (channelUID.getId()) {
            case CHANNEL_BELL_BUTTON:
                if (REFRESH.equals(command)) {
                    accessBellEvent(nhcAccess.getBellState());
                } else {
                    handleBellButtonCommand(command);
                }
                break;
            case CHANNEL_RING_AND_COME_IN:
                if (REFRESH.equals(command)) {
                    accessRingAndComeInEvent(nhcAccess.getRingAndComeInState());
                } else {
                    handleRingAndComeInCommand(command);
                }
                break;
            case CHANNEL_LOCK:
                if (REFRESH.equals(command)) {
                    accessDoorLockEvent(nhcAccess.getDoorLockState());
                } else {
                    handleDoorLockCommand(command);
                }
                break;
            default:
                logger.debug("unexpected command for channel {}", channelUID.getId());
        }

        updateStatus(ThingStatus.ONLINE);
    }

    private void handleBellButtonCommand(Command command) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        if (command instanceof OnOffType) {
            OnOffType s = (OnOffType) command;
            if (OnOffType.ON.equals(s)) {
                nhcAccess.executeBell();
            }
        }
    }

    private void handleRingAndComeInCommand(Command command) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        if (command instanceof OnOffType) {
            OnOffType s = (OnOffType) command;
            nhcAccess.executeRingAndComeIn(OnOffType.ON.equals(s));
        }
    }

    private void handleDoorLockCommand(Command command) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        if (command instanceof OnOffType) {
            OnOffType s = (OnOffType) command;
            if (OnOffType.OFF.equals(s)) {
                nhcAccess.executeUnlock();
            }
        }
    }

    @Override
    public void initialize() {
        NikoHomeControlAccessConfig config = getConfig().as(NikoHomeControlAccessConfig.class);
        deviceId = config.accessId;

        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED,
                    "@text/offline.bridge-unitialized");
            return;
        }

        // We need to do this in a separate thread because we may have to wait for the communication to become active
        scheduler.submit(() -> {
            if (!nhcComm.communicationActive()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "@text/offline.communication-error");
                return;
            }

            NhcAccess nhcAccess = nhcComm.getAccessDevices().get(deviceId);
            if (nhcAccess == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/offline.configuration-error.deviceId");
                return;
            }

            nhcAccess.setEventHandler(this);

            updateProperties(nhcAccess);

            String location = nhcAccess.getLocation();
            if (thing.getLocation() == null) {
                thing.setLocation(location);
            }

            this.nhcAccess = nhcAccess;

            logger.debug("access device initialized {}", deviceId);

            Bridge bridge = getBridge();
            if ((bridge != null) && (bridge.getStatus() == ThingStatus.ONLINE)) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }

            accessBellEvent(nhcAccess.getBellState());
            accessDoorLockEvent(nhcAccess.getDoorLockState());
            if (nhcAccess.getType().equals(AccessType.RINGANDCOMEIN)) {
                accessRingAndComeInEvent(nhcAccess.getRingAndComeInState());
            }
        });
    }

    @Override
    public void dispose() {
        NikoHomeControlCommunication nhcComm = getCommunication();
        if (nhcComm != null) {
            NhcAccess access = nhcComm.getAccessDevices().get(deviceId);
            if (access != null) {
                access.unsetEventHandler();
            }
        }
        nhcAccess = null;
        super.dispose();
    }

    private void updateProperties(NhcAccess nhcAccess) {
        Map<String, String> properties = new HashMap<>();

        if (nhcAccess instanceof NhcAccess2) {
            NhcAccess2 access = (NhcAccess2) nhcAccess;
            properties.put("deviceType", access.getDeviceType());
            properties.put("deviceTechnology", access.getDeviceTechnology());
            properties.put("deviceModel", access.getDeviceModel());
        }

        String buttonId = nhcAccess.getButtonId();
        if (buttonId != null) {
            properties.put("buttonId", buttonId);
        }

        if (nhcAccess.supportsVideoStream()) {
            properties.put("username", "admin");
            properties.put("password", "123qwe");
            String ipAddress = nhcAccess.getIpAddress();
            if (ipAddress != null) {
                properties.put("ipAddress", ipAddress);
            }
            String mjpegUri = nhcAccess.getMjpegUri();
            if (mjpegUri != null) {
                properties.put("mjpegUri", mjpegUri);
            }
            String tnUri = nhcAccess.getTnUri();
            if (tnUri != null) {
                properties.put("tnUri", tnUri);
            }
        }

        thing.setProperties(properties);
    }

    @Override
    public void accessBellEvent(boolean state) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        updateState(CHANNEL_BELL_BUTTON, state ? OnOffType.ON : OnOffType.OFF);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void accessRingAndComeInEvent(boolean state) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        updateState(CHANNEL_RING_AND_COME_IN, state ? OnOffType.ON : OnOffType.OFF);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void accessDoorLockEvent(boolean state) {
        NhcAccess nhcAccess = this.nhcAccess;
        if (nhcAccess == null) {
            logger.debug(" access device with ID {} not initialized", deviceId);
            return;
        }

        updateState(CHANNEL_LOCK, state ? OnOffType.ON : OnOffType.OFF);
        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void updateVideoDeviceProperties() {
        NhcAccess access = nhcAccess;
        if (access != null) {
            updateProperties(access);
        }
    }
}