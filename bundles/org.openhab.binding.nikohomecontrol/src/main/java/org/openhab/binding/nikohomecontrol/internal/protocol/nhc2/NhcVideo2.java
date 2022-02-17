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
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc2;

import static org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.NHCRINGING;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcVideo;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;

/**
 * The {@link NhcVideo2} class represents a Niko Home Control 2 video door station device. It is used in conjunction
 * with NhcAccess2 to capture the bell signal on a video door station for access control.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcVideo2 extends NhcVideo {
    private String deviceType;
    private String deviceTechnology;
    private String deviceModel;

    NhcVideo2(String id, String name, String deviceType, String deviceTechnology, String deviceModel,
            @Nullable String macAddress, @Nullable String ipAddress, @Nullable String mjpegUri, @Nullable String tnUri,
            NikoHomeControlCommunication nhcComm) {
        super(id, name, macAddress, ipAddress, mjpegUri, tnUri, nhcComm);
        this.deviceType = deviceType;
        this.deviceTechnology = deviceTechnology;
        this.deviceModel = deviceModel;

        if ("robinsip".equals(deviceModel)) {
            setSupportsVideoStream();
        }
    }

    /**
     * @return type as returned from Niko Home Control
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * @return technology as returned from Niko Home Control
     */
    public String getDeviceTechnology() {
        return deviceTechnology;
    }

    /**
     * @return model as returned from Niko Home Control
     */
    public String getDeviceModel() {
        return deviceModel;
    }

    @Override
    public void updateState(@Nullable String callStatus01, @Nullable String callStatus02, @Nullable String callStatus03,
            @Nullable String callStatus04) {
        callStatus.put(1, callStatus01);
        callStatus.put(2, callStatus02);
        callStatus.put(3, callStatus03);
        callStatus.put(4, callStatus04);

        nhcAccessMap.forEach((buttonIndex, access) -> {
            access.updateBellState(NHCRINGING.equals(callStatus.get(buttonIndex)) ? true : false);
        });
    }
}
