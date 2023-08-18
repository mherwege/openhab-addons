/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlServerConfiguration;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntryQueue;
import org.openhab.binding.upnpcontrol.internal.util.UpnpControlUtil;
import org.openhab.binding.upnpcontrol.internal.util.UpnpProtocolMatcher;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpServerBrowser} keeps state of browsing on an media server from a renderer control point.
 *
 * @author Mark Herwege - Initial contribution * @author Karel Goderis - Based on UPnP logic in Sonos binding
 */
@NonNullByDefault
public class UpnpServerBrowser implements UpnpInvocationCallback {

    private final Logger logger = LoggerFactory.getLogger(UpnpServerBrowser.class);

    private UpnpServerHandler serverHandler;
    private @Nullable UpnpRendererHandler rendererHandler; // Null when not created from the renderer

    private volatile boolean browseUp = false; // used to avoid automatically going down a level if only one container
                                               // entry found when going up in the hierarchy

    private static final UpnpEntry ROOT_ENTRY = new UpnpEntry(DIRECTORY_ROOT, DIRECTORY_ROOT, DIRECTORY_ROOT,
            "object.container");
    volatile UpnpEntry currentEntry = ROOT_ENTRY;
    // current entry list in selection
    List<UpnpEntry> entries = Collections.synchronizedList(new ArrayList<>());
    // store parents in hierarchy separately to be able to move up in directory structure
    private ConcurrentMap<String, UpnpEntry> parentMap = new ConcurrentHashMap<>();

    private volatile String playlistName = "";

    public UpnpServerBrowser(UpnpServerHandler serverHandler) {
        // there is no rendererHandler for the default browser attached to the server channels
        this(serverHandler, null);
    }

    public UpnpServerBrowser(UpnpServerHandler serverHandler, @Nullable UpnpRendererHandler rendererHandler) {
        this.serverHandler = serverHandler;
        this.rendererHandler = rendererHandler;

        // put root as highest level in parent map
        parentMap.put(ROOT_ENTRY.getId(), ROOT_ENTRY);

        serverBrowse();
        UpnpControlUtil.updatePlaylistsList(getBindingConfig().path);
    }

    protected void serverBrowse() {
        serverHandler.browse(currentEntry.getId(), "BrowseDirectChildren", "*", "0", "0", getConfig().sortCriteria,
                this);
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
    protected void prepareForConnection(String remoteProtocolInfo, String peerConnectionManager, int peerConnectionId,
            String direction) {
        serverHandler.prepareForConnection(remoteProtocolInfo, peerConnectionManager, peerConnectionId, direction,
                this);
    }

    protected void currentTitle(UpnpHandler handler, Command command) {
        if (command instanceof RefreshType) {
            handler.updateState(CURRENTTITLE, StringType.valueOf(currentEntry.getTitle()));
        }
    }

    protected void browse(UpnpHandler handler, Command command) {
        String browseTarget = "";
        if (command instanceof StringType) {
            browseTarget = command.toString();
            if (!browseTarget.isEmpty()) {
                if (UP.equals(browseTarget)) {
                    // Move up in tree
                    browseTarget = currentEntry.getParentId();
                    if (browseTarget.isEmpty()) {
                        // No parent found, so make it the root directory
                        browseTarget = DIRECTORY_ROOT;
                    }
                    browseUp = true;
                }
                UpnpEntry entry = parentMap.get(browseTarget);
                if (entry != null) {
                    currentEntry = entry;
                } else {
                    final String target = browseTarget;
                    synchronized (entries) {
                        Optional<UpnpEntry> current = entries.stream().filter(e -> target.equals(e.getId()))
                                .findFirst();
                        if (current.isPresent()) {
                            currentEntry = current.get();
                        } else {
                            // The real entry is not in the parentMap or options list yet, so construct a default one
                            currentEntry = new UpnpEntry(browseTarget, browseTarget, DIRECTORY_ROOT,
                                    "object.container");
                        }
                    }
                }

                logger.debug("Browse target {}", browseTarget);
                logger.debug("Navigating to node {} on server {}", currentEntry.getId(),
                        serverHandler.getThing().getLabel());
                handler.updateState(BROWSE, StringType.valueOf(browseTarget));
                handler.updateState(CURRENTTITLE, StringType.valueOf(currentEntry.getTitle()));
                serverHandler.browse(browseTarget, "BrowseDirectChildren", "*", "0", "0", getConfig().sortCriteria,
                        this);
            }
        } else if (command instanceof RefreshType) {
            browseTarget = currentEntry.getId();
            handler.updateState(BROWSE, StringType.valueOf(browseTarget));
        }
    }

    protected void search(UpnpHandler handler, Command command) {
        if (command instanceof StringType) {
            String criteria = command.toString();
            if (!criteria.isEmpty()) {
                String searchContainer = "";
                if (currentEntry.isContainer()) {
                    searchContainer = currentEntry.getId();
                } else {
                    searchContainer = currentEntry.getParentId();
                }
                if (getConfig().searchFromRoot || searchContainer.isEmpty()) {
                    // Config option search from root or no parent found, so make it the root directory
                    searchContainer = DIRECTORY_ROOT;
                }
                UpnpEntry entry = parentMap.get(searchContainer);
                if (entry != null) {
                    currentEntry = entry;
                } else {
                    // The real entry is not in the parentMap yet, so construct a default one
                    currentEntry = new UpnpEntry(searchContainer, searchContainer, DIRECTORY_ROOT, "object.container");
                }

                logger.debug("Navigating to node {} on server {}", searchContainer,
                        serverHandler.getThing().getLabel());
                handler.updateState(BROWSE, StringType.valueOf(currentEntry.getId()));
                logger.debug("Search container {} for {}", searchContainer, criteria);
                serverHandler.search(searchContainer, criteria, "*", "0", "0", getConfig().sortCriteria, this);
            }
        }
    }

    protected void playlistSelect(UpnpHandler handler, Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
            handler.updateState(PLAYLIST, StringType.valueOf(playlistName));
        }
    }

    protected void playlist(UpnpHandler handler, Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
        }
        handler.updateState(PLAYLIST, StringType.valueOf(playlistName));
    }

    protected void playlistAction(UpnpHandler handler, Command command) {
        if (command instanceof StringType) {
            switch (command.toString()) {
                case RESTORE:
                    playlistRestore(handler);
                    break;
                case SAVE:
                    playlistSave(false);
                    break;
                case APPEND:
                    playlistSave(true);
                    break;
                case DELETE:
                    playlistDelete(handler);
                    break;
            }
        }
    }

    private void playlistRestore(UpnpHandler handler) {
        if (!playlistName.isEmpty()) {
            UpnpEntryQueue queue = new UpnpEntryQueue();
            queue.restoreQueue(playlistName, getConfig().udn, getBindingConfig().path);
            updateTitleSelection(handler, queue.getEntryList());

            String parentId;
            UpnpEntry current = queue.get(0);
            if (current != null) {
                parentId = current.getParentId();
                UpnpEntry entry = parentMap.get(parentId);
                if (entry != null) {
                    currentEntry = entry;
                } else {
                    // The real entry is not in the parentMap yet, so construct a default one
                    currentEntry = new UpnpEntry(parentId, parentId, DIRECTORY_ROOT, "object.container");
                }
            } else {
                parentId = DIRECTORY_ROOT;
                currentEntry = ROOT_ENTRY;
            }

            logger.debug("Restoring playlist to node {} on {}", parentId, handler.getThing().getLabel());
        }
    }

    private void playlistSave(boolean append) {
        if (!playlistName.isEmpty()) {
            List<UpnpEntry> mediaQueue = new ArrayList<>();
            mediaQueue.addAll(entries);
            if (mediaQueue.isEmpty() && !currentEntry.isContainer()) {
                mediaQueue.add(currentEntry);
            }
            UpnpEntryQueue queue = new UpnpEntryQueue(mediaQueue, getConfig().udn);
            queue.persistQueue(playlistName, append, getBindingConfig().path);
            UpnpControlUtil.updatePlaylistsList(getBindingConfig().path);
        }
    }

    private void playlistDelete(UpnpHandler handler) {
        if (!playlistName.isEmpty()) {
            UpnpControlUtil.deletePlaylist(playlistName, getBindingConfig().path);
            UpnpControlUtil.updatePlaylistsList(getBindingConfig().path);
            handler.updateState(PLAYLIST, UnDefType.UNDEF);
        }
    }

    private void updateTitleSelection(UpnpHandler handler, List<UpnpEntry> titleList) {
        // Optionally, filter only items that can be played on the renderer
        logger.debug("Filtering content on server {}: {}", serverHandler.getThing().getLabel(), getConfig().filter);
        List<UpnpEntry> resultList = getConfig().filter ? filterEntries(titleList, true) : titleList;

        List<StateOption> stateOptionList = new ArrayList<>();
        // Add a directory up selector if not in the directory root
        if ((!resultList.isEmpty() && !(DIRECTORY_ROOT.equals(resultList.get(0).getParentId())))
                || (resultList.isEmpty() && !DIRECTORY_ROOT.equals(currentEntry.getId()))) {
            StateOption stateOption = new StateOption(UP, UP);
            stateOptionList.add(stateOption);
            logger.debug("UP added to selection list from server {}", serverHandler.getThing().getLabel());
        }

        synchronized (entries) {
            entries.clear(); // always only keep the current selection in the entry map to keep memory usage down
            resultList.forEach((value) -> {
                StateOption stateOption = new StateOption(value.getId(), value.getTitle());
                stateOptionList.add(stateOption);
                logger.trace("{} added to selection list from server {}", value.getId(),
                        serverHandler.getThing().getLabel());

                // Keep the entries in a map so we can find the parent and container for the current selection to go
                // back up
                if (value.isContainer()) {
                    parentMap.put(value.getId(), value);
                }
                entries.add(value);
            });
        }

        logger.debug("{} entries added to selection list from server {}", stateOptionList.size(),
                serverHandler.getThing().getLabel());
        Channel selectionChannel = handler.getThing().getChannel(BROWSE);
        if (selectionChannel != null) {
            ChannelUID selectionChannelUID = selectionChannel.getUID();
            handler.updateStateDescription(selectionChannelUID, stateOptionList);
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", BROWSE);
            handler.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }

        handler.updateState(BROWSE, StringType.valueOf(currentEntry.getId()));
        handler.updateState(CURRENTTITLE, StringType.valueOf(currentEntry.getTitle()));

        serveMedia();
    }

    /**
     * Filter a list of media and only keep the media that are playable on the currently selected renderer. Return all
     * if no renderer is selected.
     *
     * @param resultList
     * @param includeContainers
     * @return
     */
    private List<UpnpEntry> filterEntries(List<UpnpEntry> resultList, boolean includeContainers) {
        logger.debug("Server {}, raw result list {}", serverHandler.getThing().getLabel(), resultList);

        UpnpRendererHandler handler = getRendererHandler();
        List<String> sink = (handler != null) ? handler.getSink() : null;
        List<UpnpEntry> list = resultList.stream()
                .filter(entry -> ((includeContainers && entry.isContainer()) || (sink == null) && !entry.isContainer())
                        || ((sink != null) && UpnpProtocolMatcher.testProtocolList(entry.getProtocolList(), sink)))
                .collect(Collectors.toList());

        logger.debug("Server {}, filtered result list {}", serverHandler.getThing().getLabel(), list);
        return list;
    }

    @Override
    public UpnpHandler getHandler() {
        UpnpHandler handler = rendererHandler != null ? rendererHandler : serverHandler;
        return handler;
    }

    private @Nullable UpnpRendererHandler getRendererHandler() {
        UpnpRendererHandler handler = rendererHandler != null ? rendererHandler : serverHandler.getRendererHandler();
        return handler;
    }

    @Override
    public void onValueReceived(UpnpHandler handler, @Nullable String variable, @Nullable String value,
            @Nullable String service) {
        if (variable == null) {
            return;
        }
        switch (variable) {
            case CONNECTION_ID:
                if (handler instanceof UpnpRendererHandler rendererHandler) {
                    try {
                        rendererHandler.setPeerConnectionId((value == null) ? 0 : Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                    }
                }
                break;
            case "Result":
                if (!((value == null) || (value.isEmpty()))) {
                    List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
                    if (getConfig().browseDown && (list.size() == 1) && list.get(0).isContainer() && !browseUp) {
                        currentEntry = list.get(0);
                        String browseTarget = currentEntry.getId();
                        parentMap.put(browseTarget, currentEntry);
                        logger.debug("Server {}, browsing down one level to the unique container result {}",
                                serverHandler.getThing().getLabel(), browseTarget);
                        serverHandler.browse(browseTarget, "BrowseDirectChildren", "*", "0", "0",
                                getConfig().sortCriteria, this);
                    } else {
                        updateTitleSelection(handler, removeDuplicates(list));
                    }
                } else {
                    updateTitleSelection(handler, new ArrayList<UpnpEntry>());
                }
                break;
        }
    }

    /**
     * Remove double entries by checking the refId if it exists as Id in the list and only keeping the original entry if
     * available. If the original entry is not in the list, only keep one referring entry.
     *
     * @param list
     * @return filtered list
     */
    private List<UpnpEntry> removeDuplicates(List<UpnpEntry> list) {
        List<UpnpEntry> newList = new ArrayList<>();
        Set<String> refIdSet = new HashSet<>();
        list.forEach(entry -> {
            String refId = entry.getRefId();
            if (refId.isEmpty() || !refIdSet.contains(refId)) {
                newList.add(entry);
                refIdSet.add(refId);
            }
        });
        return newList;
    }

    protected void serveMedia() {
        UpnpRendererHandler handler = getRendererHandler();
        if (handler != null) {
            String label = handler.getThing().getLabel();
            List<UpnpEntry> mediaQueue = new ArrayList<>();
            mediaQueue.addAll(filterEntries(entries, false));
            if (mediaQueue.isEmpty() && !currentEntry.isContainer()) {
                mediaQueue.add(currentEntry);
            }
            if (mediaQueue.isEmpty()) {
                logger.debug("Nothing to serve from server {} to renderer {}", serverHandler.getThing().getLabel(),
                        label);
            } else {
                UpnpEntryQueue queue = new UpnpEntryQueue(mediaQueue, getConfig().udn);
                handler.registerQueue(queue);
                logger.debug("Serving media queue {} from server {} to renderer {}", mediaQueue,
                        serverHandler.getThing().getLabel(), handler.getThing().getLabel());

                // always keep a copy of current list that is being served
                queue.persistQueue(label != null ? label : "current", getBindingConfig().path);
                UpnpControlUtil.updatePlaylistsList(getBindingConfig().path);
            }
        }
    }

    private UpnpControlServerConfiguration getConfig() {
        return serverHandler.getServerConfig();
    }

    private UpnpControlBindingConfiguration getBindingConfig() {
        return serverHandler.getBindingConfig();
    }
}
