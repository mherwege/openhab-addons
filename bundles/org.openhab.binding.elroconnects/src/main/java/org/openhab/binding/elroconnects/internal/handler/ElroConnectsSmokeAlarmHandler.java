/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.elroconnects.internal.handler;

import static org.openhab.binding.elroconnects.internal.ElroConnectsBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.elroconnects.internal.devices.ElroConnectsDevice;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;

/**
 * The {@link ElroConnectsSmokeAlarmHandler} represents the thing handler for an Elro Connects smoke alarm device.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class ElroConnectsSmokeAlarmHandler extends ElroConnectsDeviceHandler {

    public ElroConnectsSmokeAlarmHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        Integer id = deviceId;
        if (id == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Device ID not set");
            return;
        }
        ElroConnectsBridgeHandler bridgeHandler = getBridgeHandler();
        if (bridgeHandler != null) {
            ElroConnectsDevice device = bridgeHandler.getDevice(id);
            if (device != null) {
                switch (channelUID.getId()) {
                    case MUTE_ALARM:
                        if (OnOffType.ON.equals(command)) {
                            device.muteAlarm();
                        }
                        break;
                    case TEST_ALARM:
                        if (OnOffType.ON.equals(command)) {
                            device.testAlarm();
                        }
                        break;
                }
            }
        }

        super.handleCommand(channelUID, command);
    }

    @Override
    public void triggerAlarm() {
        triggerChannel(SMOKE_ALARM);
    }
}
