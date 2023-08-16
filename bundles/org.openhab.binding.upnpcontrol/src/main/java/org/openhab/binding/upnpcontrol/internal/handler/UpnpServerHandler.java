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
package org.openhab.binding.upnpcontrol.internal.handler;

import static org.openhab.binding.upnpcontrol.internal.services.UpnpControlServiceConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlServerConfiguration;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.services.UpnpAVTransportService;
import org.openhab.binding.upnpcontrol.internal.services.UpnpConnectionManagerService;
import org.openhab.binding.upnpcontrol.internal.services.UpnpContentDirectoryService;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpServerHandler} is responsible for handling commands sent to the UPnP Server. It implements UPnP
 * ContentDirectory service actions.
 *
 * @author Mark Herwege - Initial contribution
 * @author Karel Goderis - Based on UPnP logic in Sonos binding
 */
@NonNullByDefault
public class UpnpServerHandler extends UpnpHandler {

    private final Logger logger = LoggerFactory.getLogger(UpnpServerHandler.class);

    private UpnpConnectionManagerService cmService;
    private UpnpContentDirectoryService cdsService;
    private UpnpAVTransportService avtService;

    protected @NonNullByDefault({}) UpnpControlServerConfiguration config;

    public UpnpServerHandler(Thing thing, UpnpIOService upnpIOService,
            UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider,
            UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider,
            UpnpControlBindingConfiguration configuration) {
        super(thing, upnpIOService, configuration, upnpStateDescriptionProvider, upnpCommandDescriptionProvider);

        cmService = new UpnpConnectionManagerService(this);
        cdsService = new UpnpContentDirectoryService(this);
        avtService = new UpnpAVTransportService(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(UpnpControlServerConfiguration.class);

        logger.debug("Initializing handler for media server device {} with udn {}", thing.getLabel(), getDeviceUDN());

        initDevice();
    }

    @Override
    public void dispose() {
        logger.debug("Disposing handler for media server device {} with udn {}", thing.getLabel(), getDeviceUDN());

        super.dispose();
    }

    @Override
    public synchronized void initJob() {
        if (!upnpIOService.isRegistered(this)) {
            String msg = String.format("@text/offline.device-not-registered [ \"%s\" ]", config.udn);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
            return;
        }

        if (!ThingStatus.ONLINE.equals(thing.getStatus())) {
            cmService.getProtocolInfo();
            cdsService.browse(currentEntry.getId(), "BrowseDirectChildren", "*", "0", "0", config.sortCriteria);
            playlistsListChanged();
            updateStatus(ThingStatus.ONLINE);
        }

        if (!upnpSubscribed) {
            addSubscriptions();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Nothing to do
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        logger.debug("UPnP device {} with udn {} received variable {} with value {} from service {}", thing.getLabel(),
                getDeviceUDN(), variable, value, service);
        switch (service) {
            case CONNECTION_MANAGER:
                cmService.onValueReceived(variable, value);
            case CONTENT_DIRECTORY:
                cdsService.onValueReceived(variable, value);
        }
    }

    private void onValueReceivedResult(@Nullable String value) {
        CompletableFuture<Boolean> browsing = isBrowsing;
        if (!((value == null) || (value.isEmpty()))) {
            List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
            if (config.browseDown && (list.size() == 1) && list.get(0).isContainer() && !browseUp) {
                // We only received one container entry, so we immediately browse to the next level if config.browsedown
                // = true
                if (browsing != null) {
                    browsing.complete(true); // Clear previous browse flag before starting new browse
                }
                currentEntry = list.get(0);
                String browseTarget = currentEntry.getId();
                parentMap.put(browseTarget, currentEntry);
                logger.debug("Server {}, browsing down one level to the unique container result {}", thing.getLabel(),
                        browseTarget);
                browse(browseTarget, "BrowseDirectChildren", "*", "0", "0", config.sortCriteria);
            } else {
                updateTitleSelection(removeDuplicates(list));
            }
        } else {
            updateTitleSelection(new ArrayList<UpnpEntry>());
        }
        browseUp = false;
        if (browsing != null) {
            browsing.complete(true); // We have received browse or search results, so can launch new browse or
                                     // search
        }
    }

    @Override
    protected void updateProtocolInfo(String value) {
    }

    public UpnpConnectionManagerService getConnectionManagerService() {
        return cmService;
    }

    public UpnpContentDirectoryService getContentDirectoryService() {
        return cdsService;
    }

    public UpnpAVTransportService getAVTransportService() {
        return avtService;
    }

    @Override
    public void setConnectionId(int connectionId) {
        // Nothing to do
    }

    @Override
    public void setAvTransportId(int avTransportId) {
        // Nothing to do
    }

    @Override
    public void setRcsId(int rcsId) {
        // Nothing to do
    }
}
