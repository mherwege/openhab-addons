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

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;
import static org.openhab.binding.upnpcontrol.internal.services.UpnpControlServiceConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.model.meta.RemoteDevice;
import org.openhab.binding.upnpcontrol.internal.UpnpChannelName;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.audiosink.UpnpAudioSinkReg;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlRendererConfiguration;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntryQueue;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpFavorite;
import org.openhab.binding.upnpcontrol.internal.services.UpnpAVTransportService;
import org.openhab.binding.upnpcontrol.internal.services.UpnpConnectionManagerService;
import org.openhab.binding.upnpcontrol.internal.services.UpnpControlPoint;
import org.openhab.binding.upnpcontrol.internal.services.UpnpRenderingControlService;
import org.openhab.binding.upnpcontrol.internal.services.UpnpRenderingControlServiceConfiguration;
import org.openhab.binding.upnpcontrol.internal.util.UpnpControlUtil;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.audio.AudioFormat;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.CommandOption;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpRendererHandler} is responsible for handling commands sent to the UPnP Renderer. It extends
 * {@link UpnpHandler} with UPnP renderer specific logic. It implements UPnP AVTransport and RenderingControl service
 * actions.
 *
 * @author Mark Herwege - Initial contribution
 * @author Karel Goderis - Based on UPnP logic in Sonos binding
 */
@NonNullByDefault
public class UpnpRendererHandler extends UpnpHandler {

    private final Logger logger = LoggerFactory.getLogger(UpnpRendererHandler.class);

    private volatile boolean audioSupport;
    protected volatile Set<AudioFormat> supportedAudioFormats = new HashSet<>();
    private volatile boolean audioSinkRegistered;

    private volatile UpnpAudioSinkReg audioSinkReg;

    private UpnpConnectionManagerService cmService;
    private UpnpRenderingControlService rcService;
    private UpnpAVTransportService avtService;

    private @Nullable volatile UpnpControlPoint controlPoint;

    ConcurrentMap<String, UpnpServerHandler> upnpServers;
    private volatile @Nullable UpnpServerHandler currentServerHandler;
    private @NonNullByDefault({}) ChannelUID serverChannelUID;
    private volatile Set<UpnpServerHandler> serverHandlers = ConcurrentHashMap.newKeySet();
    private volatile List<StateOption> serverStateOptionList = Collections.synchronizedList(new ArrayList<>());

    protected @NonNullByDefault({}) UpnpControlRendererConfiguration config;
    private UpnpRenderingControlServiceConfiguration renderingControlConfiguration = new UpnpRenderingControlServiceConfiguration();

    private volatile List<CommandOption> favoriteCommandOptionList = List.of();
    private volatile List<CommandOption> playlistCommandOptionList = List.of();

    private @NonNullByDefault({}) ChannelUID currentSelectionChannelUID;
    private @NonNullByDefault({}) ChannelUID favoriteSelectChannelUID;
    private @NonNullByDefault({}) ChannelUID playlistSelectChannelUID;

    private volatile String playlistName = "";

    private volatile PercentType soundVolume = new PercentType();
    private @Nullable volatile PercentType notificationVolume;
    private volatile List<String> sink = new ArrayList<>();

    private volatile String favoriteName = ""; // Currently selected favorite

    // Track position and duration fields
    private volatile int trackDuration = 0;
    private volatile int trackPosition = 0;
    private volatile long expectedTrackend = 0;
    private volatile @Nullable ScheduledFuture<?> trackPositionRefresh;
    private volatile int posAtNotificationStart = 0;

    public UpnpRendererHandler(Thing thing, UpnpIOService upnpIOService,
            ConcurrentMap<String, UpnpServerHandler> upnpServers, UpnpAudioSinkReg audioSinkReg,
            UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider,
            UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider,
            UpnpControlBindingConfiguration configuration) {
        super(thing, upnpIOService, configuration, upnpStateDescriptionProvider, upnpCommandDescriptionProvider);
        this.upnpServers = upnpServers;

        cmService = new UpnpConnectionManagerService(this, upnpScheduler);
        rcService = new UpnpRenderingControlService(this, upnpScheduler, renderingControlConfiguration);
        avtService = new UpnpAVTransportService(this, upnpScheduler);

        serviceSubscriptions.add(AV_TRANSPORT);
        serviceSubscriptions.add(RENDERING_CONTROL);

        this.audioSinkReg = audioSinkReg;
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(UpnpControlRendererConfiguration.class);
        if (config.seekStep < 1) {
            config.seekStep = 1;
        }
        logger.debug("Initializing handler for media renderer device {} with udn {}", thing.getLabel(), getDeviceUDN());

        Channel serverChannel = thing.getChannel(UPNPSERVER);
        if (serverChannel != null) {
            serverChannelUID = serverChannel.getUID();
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", UPNPSERVER);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }

        Channel favoriteSelectChannel = thing.getChannel(FAVORITE_SELECT);
        if (favoriteSelectChannel != null) {
            favoriteSelectChannelUID = favoriteSelectChannel.getUID();
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", FAVORITE_SELECT);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }
        Channel playlistSelectChannel = thing.getChannel(PLAYLIST_SELECT);
        if (playlistSelectChannel != null) {
            playlistSelectChannelUID = playlistSelectChannel.getUID();
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", PLAYLIST_SELECT);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }
        Channel selectionChannel = thing.getChannel(BROWSE);
        if (selectionChannel != null) {
            currentSelectionChannelUID = selectionChannel.getUID();
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", BROWSE);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }

        initDevice();
    }

    @Override
    public void dispose() {
        logger.debug("Disposing handler for media renderer device {} with udn {}", thing.getLabel(), getDeviceUDN());

        cancelTrackPositionRefresh();

        UpnpControlPoint controlPoint = this.controlPoint;
        if (controlPoint != null) {
            controlPoint.dispose();
        }
        this.controlPoint = null;

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
            serverStateOptionList = Collections.synchronizedList(new ArrayList<>());
            synchronized (serverStateOptionList) {
                upnpServers.forEach((key, value) -> {
                    StateOption stateOption = new StateOption(key, value.getThing().getLabel());
                    serverStateOptionList.add(stateOption);
                });
            }
            updateStateDescription(serverChannelUID, serverStateOptionList);

            getProtocolInfo();

            getCurrentConnectionInfo();
            if (!checkForConnectionIds()) {
                String msg = String.format("@text/offline.no-connection-ids [ \"%s\" ]", config.udn);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, msg);
                return;
            }

            getTransportState();

            updateFavoritesList();
            playlistsListChanged();

            RemoteDevice device = getDevice();
            if (device != null) { // The handler factory will update the device config later when it has not been
                                  // set yet
                updateDeviceConfig(device);
            }

            updateStatus(ThingStatus.ONLINE);
        }

        if (!upnpSubscribed) {
            addSubscriptions();
        }
    }

    @Override
    public synchronized void updateDeviceConfig(RemoteDevice device) {
        super.updateDeviceConfig(device);

        UpnpRenderingControlServiceConfiguration config = new UpnpRenderingControlServiceConfiguration(device);
        renderingControlConfiguration = config;
        for (String audioChannel : config.audioChannels) {
            createAudioChannels(audioChannel);
        }

        updateChannels();
    }

    private void createAudioChannels(String audioChannel) {
        UpnpRenderingControlServiceConfiguration config = renderingControlConfiguration;
        if (config.volume && !UPNP_MASTER.equals(audioChannel)) {
            String name = audioChannel + "volume";
            if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
            } else {
                String label = String.format("@text/channel.upnpcontrol.vendorvolume.label [ \"%s\" ]", audioChannel);
                createChannel(name, label, "@text/channel.upnpcontrol.vendorvolume.description", ITEM_TYPE_VOLUME,
                        CHANNEL_TYPE_VOLUME);
            }
        }
        if (config.mute && !UPNP_MASTER.equals(audioChannel)) {
            String name = audioChannel + "mute";
            if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
            } else {
                String label = String.format("@text/channel.upnpcontrol.vendormute.label [ \"%s\" ]", audioChannel);
                createChannel(name, label, "@text/channel.upnpcontrol.vendormute.description", ITEM_TYPE_MUTE,
                        CHANNEL_TYPE_MUTE);
            }
        }
        if (config.loudness) {
            String name = (UPNP_MASTER.equals(audioChannel) ? "" : audioChannel) + "loudness";
            if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
            } else {
                String label = String.format("@text/channel.upnpcontrol.vendorloudness.label [ \"%s\" ]", audioChannel);
                createChannel(name, label, "@text/channel.upnpcontrol.vendorloudness.description", ITEM_TYPE_LOUDNESS,
                        CHANNEL_TYPE_LOUDNESS);
            }
        }
    }

    /**
     * Retrieves the current volume known to the control point, gets updated by GENA events or after UPnP Rendering
     * Control GetVolume call. This method is used to retrieve volume with the
     * {@link org.openhab.binding.upnpcontrol.internal.audiosink.UpnpAudioSink#getVolume UpnpAudioSink.getVolume}
     * method.
     *
     * @return current volume
     */
    public PercentType getCurrentVolume() {
        return soundVolume;
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        // override to be able to propagate channel state updates to corresponding channels on the server
        if (SERVER_CONTROL_CHANNELS.contains(channelUID.getId())) {
            for (UpnpServerHandler handler : serverHandlers) {
                Thing serverThing = handler.getThing();
                Channel serverChannel = serverThing.getChannel(channelUID.getId());
                if (serverChannel != null) {
                    logger.debug("Update server {} channel {} with state {} from renderer {}", serverThing.getLabel(),
                            state, channelUID, thing.getLabel());
                    handler.updateServerState(serverChannel.getUID(), state);
                }
            }
        }
        super.updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} for channel {} on renderer {}", command, channelUID, thing.getLabel());

        String id = channelUID.getId();

        if (id.endsWith("volume")) {
            handleCommandVolume(command, id);
        } else if (id.endsWith("mute")) {
            handleCommandMute(command, id);
        } else if (id.endsWith("loudness")) {
            handleCommandLoudness(command, id);
        } else {
            switch (id) {
                case CURRENTTITLE:
                    handleCommandCurrentTitle(channelUID, command);
                    break;
                case BROWSE:
                    handleCommandBrowse(channelUID, command);
                    break;
                case SEARCH:
                    handleCommandSearch(command);
                    break;
                case PLAYLIST_SELECT:
                    handleCommandPlaylistSelect(channelUID, command);
                    break;
                case PLAYLIST:
                    handleCommandPlaylist(channelUID, command);
                    break;
                case PLAYLIST_ACTION:
                    handleCommandPlaylistAction(command);
                    break;
                case UPNPSERVER:
                    handleCommandUpnpServer(channelUID, command);
                    break;
                case STOP:
                    handleCommandStop(command);
                    break;
                case CONTROL:
                    handleCommandControl(channelUID, command);
                    break;
                case REPEAT:
                    handleCommandRepeat(channelUID, command);
                    break;
                case SHUFFLE:
                    handleCommandShuffle(channelUID, command);
                    break;
                case ONLY_PLAY_ONE:
                    handleCommandOnlyPlayOne(channelUID, command);
                    break;
                case URI:
                    handleCommandUri(channelUID, command);
                    break;
                case FAVORITE_SELECT:
                    handleCommandFavoriteSelect(command);
                    break;
                case FAVORITE:
                    handleCommandFavorite(channelUID, command);
                    break;
                case FAVORITE_ACTION:
                    handleCommandFavoriteAction(command);
                    break;
                case TRACK_POSITION:
                    handleCommandTrackPosition(channelUID, command);
                    break;
                case REL_TRACK_POSITION:
                    handleCommandRelTrackPosition(channelUID, command);
                    break;
                default:
                    break;
            }
        }
    }

    private void handleCommandCurrentTitle(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            UpnpControlPoint controlPoint = this.controlPoint;
            UpnpEntry currentEntry = (controlPoint != null) ? controlPoint.getCurrentQueueEntry() : null;
            updateState(channelUID, StringType.valueOf((currentEntry != null) ? currentEntry.getTitle() : null));
        }
    }

    private void handleCommandBrowse(ChannelUID channelUID, Command command) {
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
                logger.debug("Navigating to node {} on server {}", currentEntry.getId(), thing.getLabel());
                updateState(channelUID, StringType.valueOf(browseTarget));
                updateState(CURRENTTITLE, StringType.valueOf(currentEntry.getTitle()));
                browse(browseTarget, "BrowseDirectChildren", "*", "0", "0", config.sortCriteria);
            }
        } else if (command instanceof RefreshType) {
            UpnpControlPoint controlPoint = this.controlPoint;
            UpnpEntry currentEntry = (controlPoint != null) ? controlPoint.getCurrentQueueEntry() : null;
            browseTarget = (currentEntry != null) ? currentEntry.getId() : "";
            updateState(channelUID, StringType.valueOf(browseTarget));
        }
    }

    private void handleCommandSearch(Command command) {
        if (command instanceof StringType) {
            String criteria = command.toString();
            if (!criteria.isEmpty()) {
                String searchContainer = "";
                if (currentEntry.isContainer()) {
                    searchContainer = currentEntry.getId();
                } else {
                    searchContainer = currentEntry.getParentId();
                }
                if (config.searchFromRoot || searchContainer.isEmpty()) {
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

                logger.debug("Navigating to node {} on server {}", searchContainer, thing.getLabel());
                updateState(BROWSE, StringType.valueOf(currentEntry.getId()));
                logger.debug("Search container {} for {}", searchContainer, criteria);
                search(searchContainer, criteria, "*", "0", "0", config.sortCriteria);
            }
        }
    }

    private void handleCommandPlaylistSelect(ChannelUID channelUID, Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
            updateState(PLAYLIST, StringType.valueOf(playlistName));
        }
    }

    private void handleCommandPlaylist(ChannelUID channelUID, Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
        }
        updateState(channelUID, StringType.valueOf(playlistName));
    }

    private void handleCommandPlaylistAction(Command command) {
        if (command instanceof StringType) {
            switch (command.toString()) {
                case RESTORE:
                    handleCommandPlaylistRestore();
                    break;
                case SAVE:
                    handleCommandPlaylistSave(false);
                    break;
                case APPEND:
                    handleCommandPlaylistSave(true);
                    break;
                case DELETE:
                    handleCommandPlaylistDelete();
                    break;
            }
        }
    }

    private void handleCommandPlaylistRestore() {
        if (!playlistName.isEmpty()) {
            // Don't immediately restore a playlist if a browse or search is still underway, or it could get overwritten
            CompletableFuture<Boolean> browsing = isBrowsing;
            try {
                if (browsing != null) {
                    // wait for maximum 2.5s until browsing is finished
                    browsing.get(config.responseTimeout, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.debug(
                        "Exception, previous server on {} query interrupted or timed out, restoring playlist anyway",
                        thing.getLabel());
            }

            UpnpEntryQueue queue = new UpnpEntryQueue();
            queue.restoreQueue(playlistName, config.udn, bindingConfig.path);
            updateTitleSelection(queue.getEntryList());

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

            logger.debug("Restoring playlist to node {} on server {}", parentId, thing.getLabel());
        }
    }

    private void handleCommandPlaylistSave(boolean append) {
        if (!playlistName.isEmpty()) {
            List<UpnpEntry> mediaQueue = new ArrayList<>();
            mediaQueue.addAll(entries);
            if (mediaQueue.isEmpty() && !currentEntry.isContainer()) {
                mediaQueue.add(currentEntry);
            }
            UpnpEntryQueue queue = new UpnpEntryQueue(mediaQueue, config.udn);
            queue.persistQueue(playlistName, append, bindingConfig.path);
            UpnpControlUtil.updatePlaylistsList(bindingConfig.path);
        }
    }

    private void handleCommandPlaylistDelete() {
        if (!playlistName.isEmpty()) {
            UpnpControlUtil.deletePlaylist(playlistName, bindingConfig.path);
            UpnpControlUtil.updatePlaylistsList(bindingConfig.path);
            updateState(PLAYLIST, UnDefType.UNDEF);
        }
    }

    private void handleCommandUpnpServer(ChannelUID channelUID, Command command) {
        UpnpServerHandler server = null;
        UpnpServerHandler previousServer = currentServerHandler;
        if (command instanceof StringType) {
            server = (upnpServers.get(((StringType) command).toString()));
            currentServerHandler = server;
            if (server.config.filter) {
                // only refresh title list if filtering by renderer capabilities
                browse(currentEntry.getId(), "BrowseDirectChildren", "*", "0", "0", server.config.sortCriteria);
            } else {
                serveMedia();
            }
        }

        UpnpControlPoint controlPoint = this.controlPoint;
        if ((controlPoint != null) && (previousServer != null) && !previousServer.equals(server)) {
            controlPoint.connectionComplete();
        }

        if ((server != null) && !server.equals(previousServer)) {
            this.controlPoint = new UpnpControlPoint(server, this, bindingConfig, config, server.config, upnpScheduler);

            String protocolInfo = "";
            String peerConnectionManager = server.getConnectionManager();
            prepareForConnection(protocolInfo, peerConnectionManager, -1, "Output");

            Channel channel;
            if ((channel = thing.getChannel(VOLUME)) != null) {
                handleCommand(channel.getUID(), RefreshType.REFRESH);
            }
            if ((channel = thing.getChannel(MUTE)) != null) {
                handleCommand(channel.getUID(), RefreshType.REFRESH);
            }
            if ((channel = thing.getChannel(CONTROL)) != null) {
                handleCommand(channel.getUID(), RefreshType.REFRESH);
            }
        }

        if ((server = currentServerHandler) != null) {
            updateState(channelUID, StringType.valueOf(server.getThing().getUID().toString()));
        } else {
            updateState(channelUID, UnDefType.UNDEF);
        }
    }

    private void handleCommandVolume(Command command, String id) {
        UpnpControlPoint controlPoint = this.controlPoint;
        if (command instanceof RefreshType) {
            rcService.getVolume(controlPoint.getRcsId(), "volume".equals(id) ? UPNP_MASTER : id.replace("volume", ""));
        } else if (command instanceof PercentType) {
            rcService.setVolume(controlPoint.getRcsId(), "volume".equals(id) ? UPNP_MASTER : id.replace("volume", ""),
                    (PercentType) command);
        }
    }

    private void handleCommandMute(Command command, String id) {
        if (command instanceof RefreshType) {
            rcService.getMute(controlPoint.getRcsId(), "mute".equals(id) ? UPNP_MASTER : id.replace("mute", ""));
        } else if (command instanceof OnOffType) {
            rcService.setMute(controlPoint.getRcsId(), "mute".equals(id) ? UPNP_MASTER : id.replace("mute", ""),
                    (OnOffType) command);
        }
    }

    private void handleCommandLoudness(Command command, String id) {
        if (command instanceof RefreshType) {
            rcService.getLoudness(controlPoint.getRcsId(),
                    "loudness".equals(id) ? UPNP_MASTER : id.replace("loudness", ""));
        } else if (command instanceof OnOffType) {
            rcService.setLoudness(controlPoint.getRcsId(),
                    "loudness".equals(id) ? UPNP_MASTER : id.replace("loudness", ""), (OnOffType) command);
        }
    }

    private void handleCommandStop(Command command) {
        if (OnOffType.ON.equals(command)) {
            updateState(CONTROL, PlayPauseType.PAUSE);
            stop();
            updateState(TRACK_POSITION, new QuantityType<>(0, Units.SECOND));
        }
    }

    private void handleCommandControl(ChannelUID channelUID, Command command) {
        String state;
        if (command instanceof RefreshType) {
            state = transportState;
            State newState = UnDefType.UNDEF;
            if ("PLAYING".equals(state)) {
                newState = PlayPauseType.PLAY;
            } else if ("STOPPED".equals(state)) {
                newState = PlayPauseType.PAUSE;
            } else if ("PAUSED_PLAYBACK".equals(state)) {
                newState = PlayPauseType.PAUSE;
            }
            updateState(channelUID, newState);
        } else if (command instanceof PlayPauseType) {
            if (PlayPauseType.PLAY.equals(command)) {
                if (registeredQueue) {
                    registeredQueue = false;
                    playingQueue = true;
                    oneplayed = false;
                    serve();
                } else {
                    play();
                }
            } else if (PlayPauseType.PAUSE.equals(command)) {
                checkPaused();
                pause();
            }
        } else if (command instanceof NextPreviousType) {
            if (NextPreviousType.NEXT.equals(command)) {
                serveNext();
            } else if (NextPreviousType.PREVIOUS.equals(command)) {
                servePrevious();
            }
        } else if (command instanceof RewindFastforwardType) {
            int pos = 0;
            if (RewindFastforwardType.FASTFORWARD.equals(command)) {
                pos = Integer.min(trackDuration, trackPosition + config.seekStep);
            } else if (command == RewindFastforwardType.REWIND) {
                pos = Integer.max(0, trackPosition - config.seekStep);
            }
            seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
        }
    }

    private void handleCommandRepeat(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, OnOffType.from(repeat));
        } else {
            repeat = (OnOffType.ON.equals(command));
            currentQueue.setRepeat(repeat);
            updateState(channelUID, OnOffType.from(repeat));
            logger.debug("Repeat set to {} for {}", repeat, thing.getLabel());
        }
    }

    private void handleCommandShuffle(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, OnOffType.from(shuffle));
        } else {
            shuffle = (OnOffType.ON.equals(command));
            currentQueue.setShuffle(shuffle);
            if (!playing) {
                resetToStartQueue();
            }
            updateState(channelUID, OnOffType.from(shuffle));
            logger.debug("Shuffle set to {} for {}", shuffle, thing.getLabel());
        }
    }

    private void handleCommandOnlyPlayOne(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, OnOffType.from(onlyplayone));
        } else {
            onlyplayone = (OnOffType.ON.equals(command));
            oneplayed = (onlyplayone && playing) ? true : false;
            if (oneplayed) {
                setNextURI("", "");
            } else {
                UpnpEntry next = nextEntry;
                if (next != null) {
                    setNextURI(next.getRes(), UpnpXMLParser.compileMetadataString(next));
                }
            }
            updateState(channelUID, OnOffType.from(onlyplayone));
            logger.debug("OnlyPlayOne set to {} for {}", onlyplayone, thing.getLabel());
        }
    }

    private void handleCommandUri(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, StringType.valueOf(nowPlayingUri));
        } else if (command instanceof StringType) {
            setCurrentURI(command.toString(), "");
            play();
        }
    }

    private void handleCommandFavoriteSelect(Command command) {
        if (command instanceof StringType) {
            favoriteName = command.toString();
            updateState(FAVORITE, StringType.valueOf(favoriteName));
            playFavorite();
        }
    }

    private void handleCommandFavorite(ChannelUID channelUID, Command command) {
        if (command instanceof StringType) {
            favoriteName = command.toString();
            if (favoriteCommandOptionList.contains(new CommandOption(favoriteName, favoriteName))) {
                playFavorite();
            }
        }
        updateState(channelUID, StringType.valueOf(favoriteName));
    }

    private void handleCommandFavoriteAction(Command command) {
        if (command instanceof StringType) {
            switch (command.toString()) {
                case SAVE:
                    handleCommandFavoriteSave();
                    break;
                case DELETE:
                    handleCommandFavoriteDelete();
                    break;
            }
        }
    }

    private void handleCommandFavoriteSave() {
        if (!favoriteName.isEmpty()) {
            UpnpFavorite favorite = new UpnpFavorite(favoriteName, nowPlayingUri, currentEntry);
            favorite.saveFavorite(favoriteName, bindingConfig.path);
            updateFavoritesList();
        }
    }

    private void handleCommandFavoriteDelete() {
        if (!favoriteName.isEmpty()) {
            UpnpControlUtil.deleteFavorite(favoriteName, bindingConfig.path);
            updateFavoritesList();
            updateState(FAVORITE, UnDefType.UNDEF);
        }
    }

    private void handleCommandTrackPosition(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, new QuantityType<>(trackPosition, Units.SECOND));
        } else if (command instanceof QuantityType<?>) {
            QuantityType<?> position = ((QuantityType<?>) command).toUnit(Units.SECOND);
            if (position != null) {
                int pos = Integer.min(trackDuration, position.intValue());
                seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
            }
        }
    }

    private void handleCommandRelTrackPosition(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            int relPosition = (trackDuration != 0) ? (trackPosition * 100) / trackDuration : 0;
            updateState(channelUID, new PercentType(relPosition));
        } else if (command instanceof PercentType) {
            int pos = ((PercentType) command).intValue() * trackDuration / 100;
            seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
        }
    }

    /**
     * Update the current track position every second if the channel is linked.
     */
    public void scheduleTrackPositionRefresh() {
        if (playingNotification) {
            return;
        }

        cancelTrackPositionRefresh();
        if (!(isLinked(TRACK_POSITION) || isLinked(REL_TRACK_POSITION))) {
            // only get it once, so we can use the track end to correctly identify STOP pressed directly on renderer
            getPositionInfo();
        } else {
            if (trackPositionRefresh == null) {
                trackPositionRefresh = upnpScheduler.scheduleWithFixedDelay(this::getPositionInfo, 1, 1,
                        TimeUnit.SECONDS);
            }
        }
    }

    private void cancelTrackPositionRefresh() {
        ScheduledFuture<?> refresh = trackPositionRefresh;

        if (refresh != null) {
            refresh.cancel(true);
        }
        trackPositionRefresh = null;

        trackPosition = 0;
        updateState(TRACK_POSITION, new QuantityType<>(trackPosition, Units.SECOND));
        int relPosition = (trackDuration != 0) ? trackPosition / trackDuration : 0;
        updateState(REL_TRACK_POSITION, new PercentType(relPosition));
    }

    /**
     * Add a server to the server channel state option list.
     * This method is called from the {@link org.openhab.binding.upnpcontrol.internal.UpnpControlHandlerFactory
     * UpnpControlHandlerFactory} class when creating a server handler.
     *
     * @param key
     */
    public void addServerOption(String key) {
        synchronized (serverStateOptionList) {
            UpnpServerHandler handler = upnpServers.get(key);
            if (handler != null) {
                serverStateOptionList.add(new StateOption(key, handler.getThing().getLabel()));
            }
        }
        updateStateDescription(serverChannelUID, serverStateOptionList);
        logger.debug("Server option {} added to {} with udn {}", key, thing.getLabel(), getDeviceUDN());
    }

    public void updateSelectionChannel(List<StateOption> stateOptionList) {

    }

    /**
     * Remove a server from the server channel state option list.
     * This method is called from the {@link org.openhab.binding.upnpcontrol.internal.UpnpControlHandlerFactory
     * UpnpControlHandlerFactory} class when removing a server handler.
     *
     * @param key
     */
    public void removeServerOption(String key) {
        UpnpServerHandler handler = currentServerHandler;
        if ((handler != null) && (handler.getThing().getUID().toString().equals(key))) {
            currentServerHandler = null;
            updateState(serverChannelUID, UnDefType.UNDEF);
        }
        synchronized (serverStateOptionList) {
            serverStateOptionList.removeIf(stateOption -> (stateOption.getValue().equals(key)));
        }
        updateStateDescription(serverChannelUID, serverStateOptionList);
        logger.debug("Server option {} removed from {} with udn {}", key, thing.getLabel(), getDeviceUDN());
    }

    /**
     * Set the volume for notifications.
     *
     * @param volume
     */
    public void setNotificationVolume(PercentType volume) {
        notificationVolume = volume;
    }

    /**
     * Play a notification. Previous state of the renderer will resume at the end of the notification, or after the
     * maximum notification duration as defined in the renderer parameters.
     *
     * @param URI for notification sound
     */
    public void playNotification(String URI) {
        synchronized (notificationLock) {
            if (URI.isEmpty()) {
                logger.debug("UPnP device {} received empty notification URI", thing.getLabel());
                return;
            }

            notificationUri = URI;

            logger.debug("UPnP device {} playing notification {}", thing.getLabel(), URI);

            cancelTrackPositionRefresh();
            getPositionInfo();

            cancelPlayingNotificationFuture();

            if (config.maxNotificationDuration > 0) {
                playingNotificationFuture = upnpScheduler.schedule(this::stop, config.maxNotificationDuration,
                        TimeUnit.SECONDS);
            }
            playingNotification = true;

            setCurrentURI(URI, "");
            setNextURI("", "");
            PercentType volume = notificationVolume;
            setVolume(volume == null
                    ? new PercentType(Math.min(100,
                            Math.max(0, (100 + config.notificationVolumeAdjustment) * soundVolume.intValue() / 100)))
                    : volume);

            CompletableFuture<Boolean> stopping = isStopping;
            try {
                if (stopping != null) {
                    // wait for maximum 2.5s until the renderer stopped before playing
                    stopping.get(config.responseTimeout, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.debug("Timeout exception, renderer {} didn't stop yet, trying to play anyway", thing.getLabel());
            }
            play();
        }
    }

    private void cancelPlayingNotificationFuture() {
        ScheduledFuture<?> future = playingNotificationFuture;
        if (future != null) {
            future.cancel(true);
            playingNotificationFuture = null;
        }
    }

    private void resumeAfterNotification() {
        synchronized (notificationLock) {
            logger.debug("UPnP device {} resume after playing notification", thing.getLabel());

            setCurrentURI(nowPlayingUri, "");
            setVolume(soundVolume);

            cancelPlayingNotificationFuture();

            playingNotification = false;
            notificationVolume = null;
            notificationUri = "";

            if (playing) {
                int pos = posAtNotificationStart;
                seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
                play();
            }
            posAtNotificationStart = 0;
        }
    }

    private void playFavorite() {
        UpnpFavorite favorite = new UpnpFavorite(favoriteName, bindingConfig.path);
        String uri = favorite.getUri();
        UpnpEntry entry = favorite.getUpnpEntry();
        if (!uri.isEmpty()) {
            String metadata = "";
            if (entry != null) {
                metadata = UpnpXMLParser.compileMetadataString(entry);
            }
            setCurrentURI(uri, metadata);
            play();
        }
    }

    void updateFavoritesList() {
        favoriteCommandOptionList = UpnpControlUtil.favorites(bindingConfig.path).stream()
                .map(p -> (new CommandOption(p, p))).collect(Collectors.toList());
        updateCommandDescription(favoriteSelectChannelUID, favoriteCommandOptionList);
    }

    @Override
    public void playlistsListChanged() {
        playlistCommandOptionList = UpnpControlUtil.playlists().stream().map(p -> (new CommandOption(p, p)))
                .collect(Collectors.toList());
        updateCommandDescription(playlistSelectChannelUID, playlistCommandOptionList);
    }

    @Override
    public void onStatusChanged(boolean status) {
        if (!status) {
            removeSubscriptions();

            updateState(CONTROL, PlayPauseType.PAUSE);
            cancelTrackPositionRefresh();
        }
        super.onStatusChanged(status);
    }

    @Override
    protected @Nullable String preProcessValueReceived(Map<String, String> inputs, @Nullable String variable,
            @Nullable String value, @Nullable String service, @Nullable String action) {
        if (variable == null) {
            return null;
        } else {
            switch (variable) {
                case "CurrentVolume":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Volume";
                case "CurrentMute":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Mute";
                case "CurrentLoudness":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Loudness";
                default:
                    return variable;
            }
        }
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        if (logger.isTraceEnabled()) {
            logger.trace("UPnP device {} with udn {} received variable {} with value {} from service {}",
                    thing.getLabel(), getDeviceUDN(), variable, value, service);
        } else {
            if (logger.isDebugEnabled() && !("AbsTime".equals(variable) || "RelCount".equals(variable)
                    || "RelTime".equals(variable) || "AbsCount".equals(variable) || "Track".equals(variable)
                    || "TrackDuration".equals(variable))) {
                // don't log all variables received when updating the track position every second
                logger.debug("UPnP device {} with udn {} received variable {} with value {} from service {}",
                        thing.getLabel(), getDeviceUDN(), variable, value, service);
            }
        }
        if (variable == null || value == null) {
            return;
        }

        switch (service) {
            case CONNECTION_MANAGER:
                cmService.onValueReceived(variable, value);
                break;
            case AV_TRANSPORT:
                avtService.onValueReceived(variable, value);
                break;
            case RENDERING_CONTROL:
                rcService.onValueReceived(variable, value);
                break;
        }
    }

    public void updateCurrentSelection(List<StateOption> stateOptionList) {
        updateStateDescription(currentSelectionChannelUID, stateOptionList);
    }

    @Override
    protected void updateProtocolInfo(String value) {
        sink.clear();
        supportedAudioFormats.clear();
        audioSupport = false;

        sink.addAll(Arrays.asList(value.split(",")));

        for (String protocol : sink) {
            Matcher matcher = PROTOCOL_PATTERN.matcher(protocol);
            if (matcher.find()) {
                String format = matcher.group(1);
                switch (format) {
                    case "audio/mpeg3":
                    case "audio/mp3":
                    case "audio/mpeg":
                        supportedAudioFormats.add(AudioFormat.MP3);
                        break;
                    case "audio/wav":
                    case "audio/wave":
                        supportedAudioFormats.add(AudioFormat.WAV);
                        break;
                }
                audioSupport = audioSupport || Pattern.matches("audio.*", format);
            }
        }

        if (audioSupport) {
            logger.debug("Renderer {} with udn {} supports audio", thing.getLabel(), getDeviceUDN());
            registerAudioSink();
        }
    }

    public void clearMetaDataState() {
        updateState(TITLE, UnDefType.UNDEF);
        updateState(ALBUM, UnDefType.UNDEF);
        updateState(ALBUM_ART, UnDefType.UNDEF);
        updateState(CREATOR, UnDefType.UNDEF);
        updateState(ARTIST, UnDefType.UNDEF);
        updateState(PUBLISHER, UnDefType.UNDEF);
        updateState(GENRE, UnDefType.UNDEF);
        updateState(TRACK_NUMBER, UnDefType.UNDEF);
    }

    /**
     * @return Audio formats supported by the renderer.
     */
    public Set<AudioFormat> getSupportedAudioFormats() {
        return supportedAudioFormats;
    }

    private void registerAudioSink() {
        if (audioSinkRegistered) {
            logger.debug("Audio Sink already registered for renderer {} with udn {}", thing.getLabel(), getDeviceUDN());
            return;
        } else if (!upnpIOService.isRegistered(this)) {
            logger.debug("Audio Sink registration for renderer {} with udn {} failed, no service", thing.getLabel(),
                    getDeviceUDN());
            return;
        }
        logger.debug("Registering Audio Sink for renderer {} with udn {}", thing.getLabel(), getDeviceUDN());
        audioSinkReg.registerAudioSink(this);
        audioSinkRegistered = true;
    }

    /**
     * @return UPnP sink definitions supported by the renderer.
     */
    public List<String> getSink() {
        return sink;
    }

    public UpnpConnectionManagerService getConnectionManagerService() {
        return cmService;
    }

    public UpnpRenderingControlService getRenderingControlService() {
        return rcService;
    }

    public UpnpAVTransportService getAVTransportService() {
        return avtService;
    }

    @Override
    public void setConnectionId(int connectionId) {
        UpnpControlPoint controlPoint = this.controlPoint;
        if (controlPoint != null) {
            controlPoint.setRendererConnectionId(connectionId);
        }
    }

    @Override
    public void setAvTransportId(int avTransportId) {
        UpnpControlPoint controlPoint = this.controlPoint;
        if (controlPoint != null) {
            controlPoint.setAvTransportId(avTransportId);
        }
    }

    @Override
    public void setRcsId(int rcsId) {
        UpnpControlPoint controlPoint = this.controlPoint;
        if (controlPoint != null) {
            controlPoint.setRcsId(rcsId);
        }
    }
}
