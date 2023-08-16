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
package org.openhab.binding.upnpcontrol.internal.services;

import static org.openhab.binding.upnpcontrol.internal.services.UpnpControlServiceConstants.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpConnectionManagerService} represents a UPnP Connection Manager service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpConnectionManagerService extends UpnpService {

    private final Logger logger = LoggerFactory.getLogger(UpnpConnectionManagerService.class);

    public UpnpConnectionManagerService(UpnpHandler handler, ScheduledExecutorService upnpScheduler) {
        super(handler, upnpScheduler);
    }

    /**
     * Invoke GetProtocolInfo on UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     */
    public void getProtocolInfo() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONNECTION_MANAGER, "GetProtocolInfo", inputs);
    }

    /**
     * Invoke PrepareForConnection on the UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     *
     * @param remoteProtocolInfo
     * @param peerConnectionManager
     * @param peerConnectionId
     * @param direction
     */
    public void prepareForConnection(String remoteProtocolInfo, String peerConnectionManager, int peerConnectionId,
            String direction) {
        HashMap<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put("RemoteProtocolInfo", remoteProtocolInfo);
        inputs.put("PeerConnectionManager", peerConnectionManager);
        inputs.put("PeerConnectionID", Integer.toString(peerConnectionId));
        inputs.put("Direction", direction);

        invokeAction(CONNECTION_MANAGER, "PrepareForConnection", inputs);
    }

    /**
     * Invoke ConnectionComplete on UPnP Connection Manager.
     *
     * @param connectionId
     */
    public void connectionComplete(int connectionId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(CONNECTION_ID,
                Integer.toString(connectionId));

        invokeAction(CONNECTION_MANAGER, "ConnectionComplete", inputs);
    }

    /**
     * Invoke GetCurrentConnectionIDs on the UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     */
    public void getCurrentConnectionIDs() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONNECTION_MANAGER, "GetCurrentConnectionIDs", inputs);
    }

    /**
     * Invoke GetCurrentConnectionInfo on the UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     *
     * @param connectionId
     */
    public void getCurrentConnectionInfo(int connectionId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(CONNECTION_ID,
                Integer.toString(connectionId));

        invokeAction(CONNECTION_MANAGER, "GetCurrentConnectionInfo", inputs);
    }

    /**
     * Invoke GetRendererItemInfo on the UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     */
    public void getRendererItemInfo(String itemInfoFilter, String itemMetaDataList) {
        HashMap<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put("ItemInfoFilter", itemInfoFilter);
        inputs.put("ItemMetaDataList", itemMetaDataList);

        invokeAction(CONNECTION_MANAGER, "GetRendererItemInfo", inputs);
    }

    /**
     * Invoke GetFeatureList on the UPnP Connection Manager.
     * Result is received in {@link #onValueReceived}.
     */
    public void getFeatureList() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONNECTION_MANAGER, "GetFeatureList", inputs);
    }

    @Override
    public void onValueReceived(String variable, String value) {
        switch (variable) {
            case CONNECTION_ID:
                onValueReceivedConnectionId(value);
                break;
            case AV_TRANSPORT_ID:
                onValueReceivedAVTransportId(value);
                break;
            case RCS_ID:
                onValueReceivedRcsId(value);
                break;
            case "Source":
            case "Sink":
                if (!value.isEmpty()) {
                    handler.updateProtocolInfo(value);
                }
                break;
            default:
                break;
        }
    }

    private void onValueReceivedConnectionId(@Nullable String value) {
        try {
            int connectionId = (value == null) ? 0 : Integer.parseInt(value);
            handler.setConnectionId(connectionId);
        } catch (NumberFormatException e) {
            logger.trace("Cannot interpret connectionId {} for device {} with udn {}", value,
                    handler.getThing().getLabel(), handler.getDeviceUDN());
        }
    }

    private void onValueReceivedAVTransportId(@Nullable String value) {
        try {
            int avTransportId = (value == null) ? 0 : Integer.parseInt(value);
            handler.setAvTransportId(avTransportId);
        } catch (NumberFormatException e) {
            logger.trace("Cannot interpret avTransportId {} for device {} with udn {}", value,
                    handler.getThing().getLabel(), handler.getDeviceUDN());
        }
    }

    private void onValueReceivedRcsId(@Nullable String value) {
        try {
            int rcsId = (value == null) ? 0 : Integer.parseInt(value);
            handler.setRcsId(rcsId);
        } catch (NumberFormatException e) {
            logger.trace("Cannot interpret rcsId {} for device {} with udn {}", value, handler.getThing().getLabel(),
                    handler.getDeviceUDN());
        }
    }
}
