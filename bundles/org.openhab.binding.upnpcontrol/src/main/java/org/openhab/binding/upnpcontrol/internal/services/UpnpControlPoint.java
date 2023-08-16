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

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;
import static org.openhab.binding.upnpcontrol.internal.services.UpnpControlServiceConstants.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlRendererConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlServerConfiguration;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpRendererHandler;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpServerHandler;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntryQueue;
import org.openhab.binding.upnpcontrol.internal.util.UpnpControlUtil;
import org.openhab.binding.upnpcontrol.internal.util.UpnpProtocolMatcher;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpControlPoint} acts as the control point for controlling AV playback. It is created for each pair of
 * mediaserver and mediarenderer to control content playback from server to renderer. It keeps track of all connection
 * id's and queue status.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpControlPoint {

    private final Logger logger = LoggerFactory.getLogger(UpnpControlPoint.class);

    // UPnP constants
    static final String CONTENT_DIRECTORY = "ContentDirectory";
    static final String DIRECTORY_ROOT = "0";
    static final String UP = "..";

    private volatile @Nullable CompletableFuture<Boolean> isBrowsing;
    private volatile boolean browseUp = false; // used to avoid automatically going down a level if only one container
                                               // entry found when going up in the hierarchy

    private volatile boolean repeat;
    private volatile boolean shuffle;
    private volatile boolean onlyplayone; // Set to true if we only want to play one at a time

    // Group of fields representing current state of player
    private volatile String nowPlayingUri = ""; // Used to block waiting for setting URI when it is the same as current
                                                // as some players will not send URI update when it is the same as
                                                // previous
    private volatile String transportState = ""; // Current transportState to be able to refresh the control
    volatile boolean playerStopped; // Set if the player is stopped from OH command or code, allows to identify
                                    // if STOP came from other source when receiving STOP state from GENA event
    volatile boolean playing; // Set to false when a STOP is received, so we can filter two consecutive STOPs
                              // and not play next entry second time
    private volatile @Nullable ScheduledFuture<?> paused; // Set when a pause command is given, to compensate for
                                                          // renderers that cannot pause playback
    private volatile @Nullable CompletableFuture<Boolean> isSettingURI; // Set to wait for setting URI before starting
                                                                        // to play or seeking
    private volatile @Nullable CompletableFuture<Boolean> isStopping; // Set when stopping to be able to wait for stop
                                                                      // confirmation for subsequent actions that need
                                                                      // the player to be stopped
    volatile boolean registeredQueue; // Set when registering a new queue. This allows to decide if we just
                                      // need to play URI, or serve the first entry in a queue when a play
                                      // command is given.
    volatile boolean playingQueue; // Identifies if we are playing a queue received from a server. If so, a new
                                   // queue received will be played after the currently playing entry
    private volatile boolean oneplayed; // Set to true when the one entry is being played, allows to check if stop is
                                        // needed when only playing one
    volatile boolean playingNotification; // Set when playing a notification
    private volatile @Nullable ScheduledFuture<?> playingNotificationFuture; // Set when playing a notification, allows
                                                                             // timing out notification
    private volatile String notificationUri = ""; // Used to check if the received URI is from the notification
    private final Object notificationLock = new Object();

    // Track position and duration fields
    private volatile int trackDuration = 0;
    private volatile int trackPosition = 0;
    private volatile long expectedTrackend = 0;
    private volatile @Nullable ScheduledFuture<?> trackPositionRefresh;
    private volatile int posAtNotificationStart = 0;

    private static final UpnpEntry ROOT_ENTRY = new UpnpEntry(DIRECTORY_ROOT, DIRECTORY_ROOT, DIRECTORY_ROOT,
            "object.container");
    volatile UpnpEntry currentDirectoryEntry = ROOT_ENTRY;
    // current entry list in selection
    List<UpnpEntry> entries = Collections.synchronizedList(new ArrayList<>());
    // store parents in hierarchy separately to be able to move up in directory structure
    private ConcurrentMap<String, UpnpEntry> parentMap = new ConcurrentHashMap<>();

    private UpnpServerHandler serverHandler;
    private UpnpRendererHandler rendererHandler;

    private UpnpControlBindingConfiguration bindingConfig;
    private UpnpControlRendererConfiguration rendererConfig;
    private UpnpControlServerConfiguration serverConfig;

    private ScheduledExecutorService upnpScheduler;

    protected volatile int rendererConnectionId = -1; // UPnP Renderer Connection Id
    protected volatile int serverConnectionId = -1; // UPnP Server Connection Id
    protected volatile int avTransportId = -1; // UPnP AVTtransport Id
    protected volatile int rcsId = -1; // UPnP Rendering Control Id

    // Queue as received from server and current and next media entries for playback
    private volatile UpnpEntryQueue currentQueue = new UpnpEntryQueue();
    volatile @Nullable UpnpEntry currentQueueEntry = null;

    public @Nullable UpnpEntry getCurrentQueueEntry() {
        return currentQueueEntry;
    }

    volatile @Nullable UpnpEntry nextQueueEntry = null;

    public UpnpControlPoint(UpnpServerHandler serverHandler, UpnpRendererHandler rendererHandler,
            UpnpControlBindingConfiguration bindingConfig, UpnpControlRendererConfiguration rendererConfig,
            UpnpControlServerConfiguration serverConfig, ScheduledExecutorService upnpScheduler) {
        this.serverHandler = serverHandler;
        this.rendererHandler = rendererHandler;

        this.bindingConfig = bindingConfig;
        this.rendererConfig = rendererConfig;
        this.serverConfig = serverConfig;

        this.upnpScheduler = upnpScheduler;

        // put root as highest level in parent map
        parentMap.put(ROOT_ENTRY.getId(), ROOT_ENTRY);
    }

    public void dispose() {
        CompletableFuture<Boolean> browsingFuture = isBrowsing;
        if (browsingFuture != null) {
            browsingFuture.complete(false);
            isBrowsing = null;
        }

        cancelTrackPositionRefresh();
        resetPaused();
        CompletableFuture<Boolean> settingURI = isSettingURI;
        if (settingURI != null) {
            settingURI.complete(false);
            isSettingURI = null;
        }

        connectionComplete();
    }

    /**
     * Invoke Seek on UPnP AV Transport.
     *
     * @param seekTarget relative position in current track, format HH:mm:ss
     */
    protected void seek(String seekTarget) {
        CompletableFuture<Boolean> settingURI = isSettingURI;
        boolean uriSet = true;
        try {
            if (settingURI != null) {
                // wait for maximum 2.5s until the media URI is set before seeking
                uriSet = settingURI.get(rendererConfig.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Timeout exception, media URI not yet set in renderer {}, skipping seek",
                    rendererHandler.getThing().getLabel());
            return;
        }

        if (uriSet) {
            Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
            inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
            inputs.put("Unit", "REL_TIME");
            inputs.put("Target", seekTarget);

            rendererHandler.invokeAction(AV_TRANSPORT, "Seek", inputs);
        } else {
            logger.debug("Cannot seek, cancelled setting URI in the renderer {}",
                    rendererHandler.getThing().getLabel());
        }
    }

    /**
     * Invoke SetAVTransportURI on UPnP AV Transport.
     *
     * @param URI
     * @param URIMetaData
     */
    public void setCurrentURI(String URI, String URIMetaData) {
        String uri = "";
        uri = URLDecoder.decode(URI.trim(), StandardCharsets.UTF_8);
        // Some renderers don't send a URI Last Changed event when the same URI is requested, so don't wait for it
        // before starting to play
        if (!uri.equals(nowPlayingUri) && !playingNotification) {
            CompletableFuture<Boolean> settingURI = isSettingURI;
            if (settingURI != null) {
                settingURI.complete(false);
            }
            isSettingURI = new CompletableFuture<Boolean>(); // set this so we don't start playing when not finished
                                                             // setting URI
        } else {
            logger.debug("New URI {} is same as previous on renderer {}", nowPlayingUri,
                    rendererHandler.getThing().getLabel());
        }

        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("CurrentURI", uri);
        inputs.put("CurrentURIMetaData", URIMetaData);

        rendererHandler.invokeAction(AV_TRANSPORT, "SetAVTransportURI", inputs);
    }

    /**
     * Method that does a UPnP browse on a content directory. Results will be retrieved in the {@link #onValueReceived}
     * method.
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
        CompletableFuture<Boolean> browsing = isBrowsing;
        boolean browsed = true;
        try {
            if (browsing != null) {
                // wait for maximum 2.5s until browsing is finished
                browsed = browsing.get(serverConfig.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Exception, previous server query on {} interrupted or timed out, trying new browse anyway",
                    serverHandler.getThing().getLabel());
        }

        if (browsed) {
            isBrowsing = new CompletableFuture<Boolean>();
            serverHandler.getContentDirectoryService().browse(objectID, browseFlag, filter, startingIndex,
                    requestedCount, sortCriteria);
        } else {
            logger.debug("Cannot browse, cancelled querying server {}", serverHandler.getThing().getLabel());
        }
    }

    /**
     * Method that does a UPnP search on a content directory. Results will be retrieved in the {@link #onValueReceived}
     * method.
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
        CompletableFuture<Boolean> browsing = isBrowsing;
        boolean browsed = true;
        try {
            if (browsing != null) {
                // wait for maximum 2.5s until browsing is finished
                browsed = browsing.get(serverConfig.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Exception, previous server query on {} interrupted or timed out, trying new search anyway",
                    serverHandler.getThing().getLabel());
        }

        if (browsed) {
            isBrowsing = new CompletableFuture<Boolean>();
            serverHandler.getContentDirectoryService().search(containerID, searchCriteria, filter, startingIndex,
                    requestedCount, sortCriteria);
        } else {
            logger.debug("Cannot search, cancelled querying server {}", serverHandler.getThing().getLabel());
        }
    }

    /**
     * Move to next position in queue and start playing.
     */
    private void serveNext() {
        if (currentQueue.hasNext()) {
            currentQueueEntry = currentQueue.next();
            nextQueueEntry = currentQueue.get(currentQueue.nextIndex());
            logger.debug("Serve next media '{}' from queue on renderer {}", currentQueueEntry,
                    rendererHandler.getThing().getLabel());
            logger.trace("Serve next, current queue index: {}", currentQueue.index());

            serve();
        } else {
            logger.debug("Cannot serve next, end of queue on renderer {}", rendererHandler.getThing().getLabel());
            resetToStartQueue();
        }
    }

    /**
     * Move to previous position in queue and start playing.
     */
    private void servePrevious() {
        if (currentQueue.hasPrevious()) {
            currentQueueEntry = currentQueue.previous();
            nextQueueEntry = currentQueue.get(currentQueue.nextIndex());
            logger.debug("Serve previous media '{}' from queue on renderer {}", currentQueueEntry,
                    rendererHandler.getThing().getLabel());
            logger.trace("Serve previous, current queue index: {}", currentQueue.index());

            serve();
        } else {
            logger.debug("Cannot serve previous, already at start of queue on renderer {}",
                    rendererHandler.getThing().getLabel());
            resetToStartQueue();
        }
    }

    /**
     * Serve media from a queue and play immediately when already playing.
     *
     * @param media
     */
    private void serve() {
        logger.trace("Serve media on renderer {}", rendererHandler.getThing().getLabel());

        UpnpEntry entry = currentQueueEntry;
        if (entry != null) {
            rendererHandler.clearMetaDataState();
            String res = entry.getRes();
            if (res.isEmpty()) {
                logger.debug("Renderer {} cannot serve media '{}', no URI", rendererHandler.getThing().getLabel(),
                        currentQueueEntry);
                playingQueue = false;
                return;
            }
            updateMetaDataState(entry);
            rendererHandler.getAVTransportService().setAVTransportURI(avTransportId, res,
                    UpnpXMLParser.compileMetadataString(entry));

            boolean play = (playingQueue || playing) && !(onlyplayone && oneplayed);
            logger.trace("Will we play? {} (playingQueue {}, playing {}, onlyplayone {}, oneplayed {})", play,
                    playingQueue, playing, onlyplayone, oneplayed);
            if (play) {
                logger.trace("Ready to play '{}' from queue", currentQueueEntry);

                trackDuration = 0;
                trackPosition = 0;
                expectedTrackend = 0;
                play();

                oneplayed = true;
                playingQueue = true;
            }

            // make the next entry available to renderers that support it
            if (!onlyplayone) {
                UpnpEntry next = nextQueueEntry;
                if (next != null) {
                    rendererHandler.getAVTransportService().setNextAVTransportURI(avTransportId, next.getRes(),
                            UpnpXMLParser.compileMetadataString(next));
                }
            }
        }
    }

    /**
     * Invoke Stop on UPnP AV Transport.
     */
    public void stop() {
        playerStopped = true;

        if (playing) {
            CompletableFuture<Boolean> stopping = isStopping;
            if (stopping != null) {
                stopping.complete(false);
            }
            isStopping = new CompletableFuture<Boolean>(); // set this so we can check if stop confirmation has been
                                                           // received
        }

        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        rendererHandler.invokeAction(AV_TRANSPORT, "Stop", inputs);
    }

    /**
     * Invoke Play on UPnP AV Transport.
     */
    public void play() {
        CompletableFuture<Boolean> settingURI = isSettingURI;
        boolean uriSet = true;
        try {
            if (settingURI != null) {
                // wait for maximum 2.5s until the media URI is set before playing
                uriSet = settingURI.get(rendererConfig.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Timeout exception, media URI not yet set in renderer {} trying to play anyway",
                    rendererHandler.getThing().getLabel());
        }

        if (uriSet) {
            Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
            inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
            inputs.put("Speed", "1");

            rendererHandler.invokeAction(AV_TRANSPORT, "Play", inputs);
        } else {
            logger.debug("Cannot play, cancelled setting URI in the renderer {}",
                    rendererHandler.getThing().getLabel());
        }
    }

    /**
     * Invoke Pause on UPnP AV Transport.
     */
    protected void pause() {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        rendererHandler.invokeAction(AV_TRANSPORT, "Pause", inputs);
    }

    /**
     * Invoke Next on UPnP AV Transport.
     */
    protected void next() {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        rendererHandler.invokeAction(AV_TRANSPORT, "Next", inputs);
    }

    /**
     * Invoke Previous on UPnP AV Transport.
     */
    protected void previous() {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        rendererHandler.invokeAction(AV_TRANSPORT, "Previous", inputs);
    }

    /**
     * Called before handling a pause CONTROL command. If we do not received PAUSED_PLAYBACK or STOPPED back within
     * timeout, we will revert to playing state. This takes care of renderers that cannot pause playback.
     */
    private void checkPaused() {
        paused = upnpScheduler.schedule(this::resetPaused, rendererConfig.responseTimeout, TimeUnit.MILLISECONDS);
    }

    private void resetPaused() {
        updateState(CONTROL, PlayPauseType.PLAY);
    }

    private void cancelCheckPaused() {
        ScheduledFuture<?> future = paused;
        if (future != null) {
            future.cancel(true);
            paused = null;
        }
    }

    private void setExpectedTrackend() {
        expectedTrackend = Instant.now().toEpochMilli() + (trackDuration - trackPosition) * 1000
                - rendererConfig.responseTimeout;
    }

    private void updateTitleSelection(List<UpnpEntry> titleList) {
        // Optionally, filter only items that can be played on the renderer
        logger.debug("Filtering content for renderer {} from server {}: {}", rendererHandler.getThing().getLabel(),
                serverHandler.getThing().getLabel(), serverConfig.filter);
        List<UpnpEntry> resultList = serverConfig.filter ? filterEntries(titleList, true) : titleList;

        List<StateOption> stateOptionList = new ArrayList<>();
        // Add a directory up selector if not in the directory root
        if ((!resultList.isEmpty() && !(DIRECTORY_ROOT.equals(resultList.get(0).getParentId())))
                || (resultList.isEmpty() && !DIRECTORY_ROOT.equals(currentDirectoryEntry.getId()))) {
            StateOption stateOption = new StateOption(UP, UP);
            stateOptionList.add(stateOption);
            logger.debug("UP added to selection list for renderer {} from server {}",
                    rendererHandler.getThing().getLabel(), serverHandler.getThing().getLabel());
        }

        synchronized (entries) {
            entries.clear(); // always only keep the current selection in the entry map to keep memory usage down
            resultList.forEach((value) -> {
                StateOption stateOption = new StateOption(value.getId(), value.getTitle());
                stateOptionList.add(stateOption);
                logger.trace("{} added to selection list for renderer {} from server {}", value.getId(),
                        rendererHandler.getThing().getLabel(), serverHandler.getThing().getLabel());

                // Keep the entries in a map so we can find the parent and container for the current selection to go
                // back up
                if (value.isContainer()) {
                    parentMap.put(value.getId(), value);
                }
                entries.add(value);
            });
        }

        logger.debug("{} entries added to selection list for renderer {} from server {}", stateOptionList.size(),
                rendererHandler.getThing().getLabel(), serverHandler.getThing().getLabel());
        rendererHandler.updateCurrentSelection(stateOptionList);
        rendererHandler.updateState(BROWSE, StringType.valueOf(currentDirectoryEntry.getId()));
        rendererHandler.updateState(CURRENTTITLE, StringType.valueOf(currentDirectoryEntry.getTitle()));

        serveMedia();
    }

    /**
     * Filter a list of media and only keep the media that are playable on the renderer. Return all
     * if no renderer is selected.
     *
     * @param resultList
     * @param includeContainers
     * @return
     */
    private List<UpnpEntry> filterEntries(List<UpnpEntry> resultList, boolean includeContainers) {
        logger.debug("Server {}, raw result list {}", serverHandler.getThing().getLabel(), resultList);

        UpnpRendererHandler rendererHandler = this.rendererHandler;
        List<String> sink = rendererHandler.getSink();
        List<UpnpEntry> list = resultList.stream()
                .filter(entry -> ((includeContainers && entry.isContainer()) || !entry.isContainer())
                        || UpnpProtocolMatcher.testProtocolList(entry.getProtocolList(), sink))
                .collect(Collectors.toList());

        logger.debug("Server {}, filtered result list {}", serverHandler.getThing().getLabel(), list);
        return list;
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

    private void clearCurrentEntry() {
        rendererHandler.clearMetaDataState();

        trackDuration = 0;
        rendererHandler.updateState(TRACK_DURATION, UnDefType.UNDEF);
        trackPosition = 0;
        rendererHandler.updateState(TRACK_POSITION, UnDefType.UNDEF);
        rendererHandler.updateState(REL_TRACK_POSITION, UnDefType.UNDEF);

        currentQueueEntry = null;
    }

    private void serveMedia() {
        List<UpnpEntry> mediaQueue = new ArrayList<>();
        mediaQueue.addAll(filterEntries(entries, false));
        if (mediaQueue.isEmpty() && !currentDirectoryEntry.isContainer()) {
            mediaQueue.add(currentDirectoryEntry);
        }
        if (mediaQueue.isEmpty()) {
            logger.debug("Nothing to play on renderer {} from server {}", rendererHandler.getThing().getLabel(),
                    serverHandler.getThing().getLabel());
        } else {
            UpnpEntryQueue queue = new UpnpEntryQueue(mediaQueue, serverConfig.udn);
            registerQueue(queue);
            logger.debug("Serving media queue {} on renderer {} from server {}", mediaQueue,
                    rendererHandler.getThing().getLabel(), serverHandler.getThing().getLabel());

            // always keep a copy of current list that is being served
            queue.persistQueue(bindingConfig.path);
            UpnpControlUtil.updatePlaylistsList(bindingConfig.path);
        }
    }

    /**
     * Register a new queue with media entries to the renderer. Set the next position at the first entry in the list.
     * If the renderer is currently playing, set the first entry in the list as the next media. If not playing, set it
     * as current media.
     *
     * @param queue
     */
    protected void registerQueue(UpnpEntryQueue queue) {
        if (currentQueue.equals(queue)) {
            // We get the same queue, so do nothing
            return;
        }

        logger.debug("Registering queue on renderer {}", rendererHandler.getThing().getLabel());

        registeredQueue = true;
        currentQueue = queue;
        currentQueue.setRepeat(repeat);
        currentQueue.setShuffle(shuffle);
        if (playingQueue) {
            nextQueueEntry = currentQueue.get(currentQueue.nextIndex());
            UpnpEntry next = nextQueueEntry;
            if ((next != null) && !onlyplayone) {
                // make the next entry available to renderers that support it
                logger.trace("Renderer {} still playing, set new queue as next entry",
                        rendererHandler.getThing().getLabel());
                rendererHandler.getAVTransportService().setNextAVTransportURI(avTransportId, next.getRes(),
                        UpnpXMLParser.compileMetadataString(next));
            }
        } else {
            resetToStartQueue();
        }
    }

    private void resetToStartQueue() {
        logger.trace("Reset to start queue on renderer {}", rendererHandler.getThing().getLabel());

        playingQueue = false;
        registeredQueue = true;

        rendererHandler.stop();

        currentQueue.resetIndex(); // reset to beginning of queue
        currentQueueEntry = currentQueue.next();
        nextQueueEntry = currentQueue.get(currentQueue.nextIndex());
        logger.trace("Reset queue, current queue index: {}", currentQueue.index());
        UpnpEntry current = currentQueueEntry;
        if (current != null) {
            rendererHandler.clearMetaDataState();
            updateMetaDataState(current);
            rendererHandler.getAVTransportService().setAVTransportURI(avTransportId, current.getRes(),
                    UpnpXMLParser.compileMetadataString(current));
        } else {
            clearCurrentEntry();
        }

        UpnpEntry next = nextQueueEntry;
        if (onlyplayone) {
            rendererHandler.getAVTransportService().setNextAVTransportURI(avTransportId, "", "");
        } else if (next != null) {
            rendererHandler.getAVTransportService().setNextAVTransportURI(avTransportId, next.getRes(),
                    UpnpXMLParser.compileMetadataString(next));
        }
    }

    /**
     * Update metadata channels for media with data received from the Media Server or AV Transport.
     *
     * @param media
     */
    private void updateMetaDataState(UpnpEntry media) {
        // We don't want to update metadata if the metadata from the AVTransport is less complete than in the current
        // entry.
        boolean isCurrent = false;
        UpnpEntry entry = null;
        if (playingQueue) {
            entry = currentQueueEntry;
        }

        logger.trace("Renderer {}, received media ID: {}", rendererHandler.getThing().getLabel(), media.getId());

        if ((entry != null) && entry.getId().equals(media.getId())) {
            logger.trace("Current ID: {}", entry.getId());

            isCurrent = true;
        } else {
            // Sometimes we receive the media URL without the ID, then compare on URL
            String mediaRes = media.getRes().trim();
            String entryRes = (entry != null) ? entry.getRes().trim() : "";

            String mediaUrl = URLDecoder.decode(mediaRes, StandardCharsets.UTF_8);
            String entryUrl = URLDecoder.decode(entryRes, StandardCharsets.UTF_8);
            isCurrent = mediaUrl.equals(entryUrl);

            logger.trace("Current queue res: {}", entryRes);
            logger.trace("Updated media res: {}", mediaRes);
        }

        logger.trace("Received meta data is for current entry: {}", isCurrent);

        if (!(isCurrent && media.getTitle().isEmpty())) {
            rendererHandler.updateState(TITLE, StringType.valueOf(media.getTitle()));
        }
        if (!(isCurrent && (media.getAlbum().isEmpty() || media.getAlbum().matches("Unknown.*")))) {
            rendererHandler.updateState(ALBUM, StringType.valueOf(media.getAlbum()));
        }
        if (!(isCurrent
                && (media.getAlbumArtUri().isEmpty() || media.getAlbumArtUri().contains("DefaultAlbumCover")))) {
            if (media.getAlbumArtUri().isEmpty() || media.getAlbumArtUri().contains("DefaultAlbumCover")) {
                rendererHandler.updateState(ALBUM_ART, UnDefType.UNDEF);
            } else {
                State albumArt = HttpUtil.downloadImage(media.getAlbumArtUri());
                if (albumArt == null) {
                    logger.debug("Failed to download the content of album art from URL {}", media.getAlbumArtUri());
                    if (!isCurrent) {
                        rendererHandler.updateState(ALBUM_ART, UnDefType.UNDEF);
                    }
                } else {
                    rendererHandler.updateState(ALBUM_ART, albumArt);
                }
            }
        }
        if (!(isCurrent && (media.getCreator().isEmpty() || media.getCreator().matches("Unknown.*")))) {
            rendererHandler.updateState(CREATOR, StringType.valueOf(media.getCreator()));
        }
        if (!(isCurrent && (media.getArtist().isEmpty() || media.getArtist().matches("Unknown.*")))) {
            rendererHandler.updateState(ARTIST, StringType.valueOf(media.getArtist()));
        }
        if (!(isCurrent && (media.getPublisher().isEmpty() || media.getPublisher().matches("Unknown.*")))) {
            rendererHandler.updateState(PUBLISHER, StringType.valueOf(media.getPublisher()));
        }
        if (!(isCurrent && (media.getGenre().isEmpty() || media.getGenre().matches("Unknown.*")))) {
            rendererHandler.updateState(GENRE, StringType.valueOf(media.getGenre()));
        }
        if (!(isCurrent && (media.getOriginalTrackNumber() == null))) {
            Integer trackNumber = media.getOriginalTrackNumber();
            State trackNumberState = (trackNumber != null) ? new DecimalType(trackNumber) : UnDefType.UNDEF;
            rendererHandler.updateState(TRACK_NUMBER, trackNumberState);
        }
    }

    public void connectionComplete() {
        rendererHandler.getConnectionManagerService().connectionComplete(rendererConnectionId);
        serverHandler.getConnectionManagerService().connectionComplete(serverConnectionId);
    }

    public int getRendererConnectionId() {
        return rendererConnectionId;
    }

    public int getServerConnectionId() {
        return serverConnectionId;
    }

    public int getAvTransportId() {
        return avTransportId;
    }

    public int getRcsId() {
        return rcsId;
    }

    public void setRendererConnectionId(int connectionId) {
        this.rendererConnectionId = connectionId;
    }

    public void setServerConnectionId(int connectionId) {
        this.serverConnectionId = connectionId;
    }

    public void setAvTransportId(int avTransportId) {
        this.avTransportId = avTransportId;
    }

    public void setRcsId(int rcsId) {
        this.rcsId = rcsId;
    }
}
