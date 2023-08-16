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

import static org.openhab.binding.upnpcontrol.internal.services.UpnpControlServiceConstants.CONTENT_DIRECTORY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpHandler;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpContentDirectoryService} represents a UPnP Content Directory service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpContentDirectoryService extends UpnpService {

    private final Logger logger = LoggerFactory.getLogger(UpnpContentDirectoryService.class);

    public UpnpContentDirectoryService(UpnpHandler handler, ScheduledExecutorService upnpScheduler) {
        super(handler, upnpScheduler);
    }

    /**
     * Invoke GetSearchCapabilities on UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getSearchCapabilities() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetSearchCapabilities", inputs);
    }

    /**
     * Invoke GetSortCapabilities on UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getSortCapabilities() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetSortCapabilities", inputs);
    }

    /**
     * Invoke GetSortExtensionCapabilities on UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getSortExtensionCapabilities() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetSortExtensionCapabilities", inputs);
    }

    /**
     * Invoke GetFeatureList on the UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getFeatureList() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetFeatureList", inputs);
    }

    /**
     * Invoke GetSystemUpdateID on the UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getSystemUpdateID() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetSystemUpdateID", inputs);
    }

    /**
     * Invoke GetServiceResetToken on the UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     */
    public void getServiceResetToken() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(CONTENT_DIRECTORY, "GetServiceResetToken", inputs);
    }

    /**
     * Invoke Browse on UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     *
     * @param objectID content directory object
     * @param browseFlag BrowseMetaData or BrowseDirectChildren
     * @param filter properties to be returned
     * @param startingIndex starting index of objects to return
     * @param requestedCount number of objects to return, 0 for all
     * @param sortCriteria sort criteria, example: +dc:title
     */
    public void browse(String objectID, String browseFlag, String filter, String startingIndex, String requestedCount,
            String sortCriteria) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put("ObjectID", objectID);
        inputs.put("BrowseFlag", browseFlag);
        inputs.put("Filter", filter);
        inputs.put("StartingIndex", startingIndex);
        inputs.put("RequestedCount", requestedCount);
        inputs.put("SortCriteria", sortCriteria);

        invokeAction(CONTENT_DIRECTORY, "Browse", inputs);
    }

    /**
     * Invoke Search on UPnP Content Directory.
     * Result is received in {@link #onValueReceived}.
     *
     * @param containerID content directory container
     * @param searchCriteria search criteria, examples:
     *            dc:title contains "song"
     *            dc:creator contains "Springsteen"
     *            upnp:class = "object.item.audioItem"
     *            upnp:album contains "Born in"
     * @param filter properties to be returned
     * @param startingIndex starting index of objects to return
     * @param requestedCount number of objects to return, 0 for all
     * @param sortCriteria sort criteria, example: +dc:title
     */
    public void search(String containerID, String searchCriteria, String filter, String startingIndex,
            String requestedCount, String sortCriteria) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put("ContainerID", containerID);
        inputs.put("SearchCriteria", searchCriteria);
        inputs.put("Filter", filter);
        inputs.put("StartingIndex", startingIndex);
        inputs.put("RequestedCount", requestedCount);
        inputs.put("SortCriteria", sortCriteria);

        invokeAction(CONTENT_DIRECTORY, "Search", inputs);
    }

    @Override
    public void onValueReceived(String variable, String value) {
        switch (variable) {
            case "Result":
                onValueReceivedResult(value);
                break;
            case "NumberReturned":
            case "TotalMatches":
            case "UpdateID":
                break;
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
}
