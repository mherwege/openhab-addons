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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlServerConfiguration;
import org.openhab.binding.upnpcontrol.internal.util.UpnpControlUtil;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpServerHandler} is responsible for handling commands sent to the UPnP Server. It implements UPnP
 * ContentDirectory service actions.
 *
 * @author Mark Herwege - Initial contribution
 * @author Karel Goderis - Based on UPnP logic in Sonos binding
 * @author Mark Herwege - refactor to allow one server to serve multiple renderers
 */
@NonNullByDefault
public class UpnpServerHandler extends UpnpHandler {

    private final Logger logger = LoggerFactory.getLogger(UpnpServerHandler.class);

    ConcurrentMap<String, UpnpRendererHandler> upnpRenderers;
    private volatile @Nullable UpnpRendererHandler currentRendererHandler;
    private volatile List<StateOption> rendererStateOptionList = Collections.synchronizedList(new ArrayList<>());

    private @Nullable ChannelUID rendererChannelUID;

    private volatile @Nullable CompletableFuture<Boolean> isBrowsing;
    UpnpServerBrowser browser;

    private volatile String playlistName = "";

    protected @NonNullByDefault({}) UpnpControlServerConfiguration config;

    private volatile List<String> source = new ArrayList<>();

    public UpnpServerHandler(Thing thing, UpnpIOService upnpIOService,
            ConcurrentMap<String, UpnpRendererHandler> upnpRenderers,
            UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider,
            UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider,
            UpnpControlBindingConfiguration configuration) {
        super(thing, upnpIOService, configuration, upnpStateDescriptionProvider, upnpCommandDescriptionProvider);
        this.upnpRenderers = upnpRenderers;

        // This creates a default browser, not linked to a renderer
        browser = new UpnpServerBrowser(this);
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(UpnpControlServerConfiguration.class);

        logger.debug("Initializing handler for media server device {} with udn {}", thing.getLabel(), getDeviceUDN());

        Channel rendererChannel = thing.getChannel(UPNPRENDERER);
        if (rendererChannel != null) {
            rendererChannelUID = rendererChannel.getUID();
        } else {
            String msg = String.format("@text/offline.channel-undefined [ \"%s\" ]", UPNPRENDERER);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return;
        }

        initDevice();
    }

    @Override
    public void dispose() {
        logger.debug("Disposing handler for media server device {}", thing.getLabel());

        CompletableFuture<Boolean> browsingFuture = isBrowsing;
        if (browsingFuture != null) {
            browsingFuture.complete(false);
            isBrowsing = null;
        }

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
            browser.browse();

            synchronized (rendererStateOptionList) {
                // Create empty state option to allow deselecting renderer (while keeping browse state)
                StateOption emptyOption = new StateOption("", "");
                if (!rendererStateOptionList.contains(emptyOption)) {
                    rendererStateOptionList.add(emptyOption);
                }
                upnpRenderers.forEach((key, value) -> {
                    StateOption stateOption = new StateOption(key, value.getThing().getLabel());
                    if (!rendererStateOptionList.contains(stateOption)) {
                        rendererStateOptionList.add(stateOption);
                    }
                });
            }
            updateRenderersList();

            getFeatureList();
            getProtocolInfo();
            getCurrentConnectionInfo();

            updateStatus(ThingStatus.ONLINE);
        }

        if (!upnpSubscribed) {
            addSubscriptions();
        }
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
     * @param callback, set when the result in onValueReceived needs to go to a specific {@link UpnpInvocationCallback}
     */
    void browse(String objectID, String browseFlag, String filter, String startingIndex, String requestedCount,
            String sortCriteria, @Nullable UpnpInvocationCallback callback) {
        CompletableFuture<Boolean> browsing = isBrowsing;
        boolean browsed = true;
        try {
            if (browsing != null) {
                // wait for maximum 2.5s until browsing is finished
                browsed = browsing.get(config.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Exception, previous server query on {} interrupted or timed out, trying new browse anyway",
                    thing.getLabel());
        }

        if (browsed) {
            isBrowsing = new CompletableFuture<Boolean>();

            Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
            inputs.put("ObjectID", objectID);
            inputs.put("BrowseFlag", browseFlag);
            inputs.put("Filter", filter);
            inputs.put("StartingIndex", startingIndex);
            inputs.put("RequestedCount", requestedCount);
            inputs.put("SortCriteria", sortCriteria);

            invokeAction(CONTENT_DIRECTORY, "Browse", inputs, callback);
        } else {
            logger.debug("Cannot browse, cancelled querying server {}", thing.getLabel());
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
     * @param callback, set when the result in onValueReceived needs to go to a specific {@link UpnpInvocationCallback}
     */
    void search(String containerID, String searchCriteria, String filter, String startingIndex, String requestedCount,
            String sortCriteria, @Nullable UpnpInvocationCallback callback) {
        CompletableFuture<Boolean> browsing = isBrowsing;
        boolean browsed = true;
        try {
            if (browsing != null) {
                // wait for maximum 2.5s until browsing is finished
                browsed = browsing.get(config.responseTimeout, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Exception, previous server query on {} interrupted or timed out, trying new search anyway",
                    thing.getLabel());
        }

        if (browsed) {
            isBrowsing = new CompletableFuture<Boolean>();

            Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
            inputs.put("ContainerID", containerID);
            inputs.put("SearchCriteria", searchCriteria);
            inputs.put("Filter", filter);
            inputs.put("StartingIndex", startingIndex);
            inputs.put("RequestedCount", requestedCount);
            inputs.put("SortCriteria", sortCriteria);

            invokeAction(CONTENT_DIRECTORY, "Search", inputs, callback);
        } else {
            logger.debug("Cannot search, cancelled querying server {}", thing.getLabel());
        }
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} for channel {} on server {}", command, channelUID, thing.getLabel());

        switch (channelUID.getId()) {
            case UPNPRENDERER:
                handleCommandUpnpRenderer(channelUID, command);
                break;
            case CURRENTTITLE:
                browser.handleCommandCurrentTitle(this, command);
                break;
            case BROWSE:
                browser.handleCommandBrowse(this, command);
                break;
            case SEARCH:
                browser.handleCommandSearch(this, command);
                break;
            case PLAYLIST_SELECT:
                handleCommandPlaylistSelect(command);
                break;
            case PLAYLIST:
                handleCommandPlaylist(command);
                break;
            case PLAYLIST_ACTION:
                handleCommandPlaylistAction(command);
                break;
            case VOLUME:
            case MUTE:
            case CONTROL:
            case STOP:
                // Pass these on to the media renderer thing if one is selected
                handleCommandInRenderer(channelUID, command);
                break;
        }
    }

    private void handleCommandUpnpRenderer(ChannelUID channelUID, Command command) {
        UpnpRendererHandler renderer = currentRendererHandler;
        if (command instanceof StringType) {
            renderer = (upnpRenderers.get(((StringType) command).toString()));
        }

        if (renderer != null) {
            if (!renderer.equals(currentRendererHandler)) {
                browser = new UpnpServerBrowser(browser, renderer);
            }

            Channel channel = renderer.getThing().getChannel(UPNPSERVER);
            if (channel != null) {
                renderer.initServerBrowser(this, browser);
            }

            updateState(channelUID, StringType.valueOf(renderer.getThing().getUID().toString()));
            updateState(VOLUME, renderer.getCurrentVolume());
            updateState(MUTE, renderer.getCurrentMute());
            updateState(CONTROL, renderer.getCurrentControl());
        } else {
            browser = new UpnpServerBrowser(browser, null);

            updateState(channelUID, UnDefType.UNDEF);
            updateState(VOLUME, UnDefType.UNDEF);
            updateState(MUTE, UnDefType.UNDEF);
            updateState(CONTROL, UnDefType.UNDEF);
        }

        currentRendererHandler = renderer;
    }

    private void handleCommandInRenderer(ChannelUID channelUID, Command command) {
        String channelId = channelUID.getId();
        UpnpRendererHandler handler = currentRendererHandler;
        Channel channel;
        if ((handler != null) && (channel = handler.getThing().getChannel(channelId)) != null) {
            handler.handleCommand(channel.getUID(), command);
        } else if (!STOP.equals(channelId)) {
            updateState(channelId, UnDefType.UNDEF);
        }
    }

    private void handleCommandPlaylistSelect(Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
            updateState(PLAYLIST, StringType.valueOf(playlistName));
        }
    }

    private void handleCommandPlaylist(Command command) {
        if (command instanceof StringType) {
            playlistName = command.toString();
        }
        updateState(PLAYLIST, StringType.valueOf(playlistName));
    }

    private void handleCommandPlaylistAction(Command command) {
        if (command instanceof StringType) {
            switch (command.toString()) {
                case RESTORE:
                    browser.handleCommandPlaylistRestore(this, playlistName);
                    break;
                case SAVE:
                    browser.handleCommandPlaylistSave(false, playlistName);
                    break;
                case APPEND:
                    browser.handleCommandPlaylistSave(true, playlistName);
                    break;
                case DELETE:
                    handleCommandPlaylistDelete();
                    break;
            }
        }
    }

    private void handleCommandPlaylistDelete() {
        if (!playlistName.isEmpty()) {
            UpnpControlUtil.deletePlaylist(playlistName, getBindingConfig().path);
            UpnpControlUtil.updatePlaylistsList(getBindingConfig().path);
            updateState(PLAYLIST, UnDefType.UNDEF);
        }
    }

    /**
     * Add a renderer to the renderer channel state option list.
     * This method is called from the {@link org.openhab.binding.upnpcontrol.internal.UpnpControlHandlerFactory
     * UpnpControlHandlerFactory} class when creating a renderer handler.
     *
     * @param key
     */
    public void addRendererOption(String key) {
        synchronized (rendererStateOptionList) {
            UpnpRendererHandler handler = upnpRenderers.get(key);
            if (handler != null) {
                StateOption stateOption = new StateOption(key, handler.getThing().getLabel());
                if (!rendererStateOptionList.contains(stateOption)) {
                    rendererStateOptionList.add(stateOption);
                }
            }
        }
        updateRenderersList();
        logger.debug("Renderer option {} added to {} with udn {}", key, thing.getLabel(), getDeviceUDN());
    }

    /**
     * Remove a renderer from the renderer channel state option list.
     * This method is called from the {@link org.openhab.binding.upnpcontrol.internal.UpnpControlHandlerFactory
     * UpnpControlHandlerFactory} class when removing a renderer handler.
     *
     * @param key
     */
    public void removeRendererOption(String key) {
        UpnpRendererHandler handler = currentRendererHandler;
        ChannelUID uid = rendererChannelUID;
        if ((handler != null) && (uid != null) && (handler.getThing().getUID().toString().equals(key))) {
            currentRendererHandler = null;
            updateState(uid, UnDefType.UNDEF);
        }
        synchronized (rendererStateOptionList) {
            rendererStateOptionList.removeIf(stateOption -> (stateOption.getValue().equals(key)));
        }
        updateRenderersList();
        logger.debug("Renderer option {} removed from {} with udn {}", key, thing.getLabel(), getDeviceUDN());
    }

    private void updateRenderersList() {
        ChannelUID uid = rendererChannelUID;
        if (uid != null) {
            synchronized (rendererStateOptionList) {
                updateStateDescription(uid, rendererStateOptionList);
            }
        }
    }

    @Override
    protected void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service,
            @Nullable UpnpInvocationCallback callback) {
        if (callback != null && "Result".equals(variable)) {
            callback.onValueReceived(variable, value, service, callback.getHandler());
            return;
        }

        onValueReceived(variable, value, service);
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        logger.debug("UPnP device {} with udn {} received variable {} with value {} from service {}", thing.getLabel(),
                getDeviceUDN(), variable, value, service);
        if (variable == null) {
            return;
        }
        switch (variable) {
            case "Result":
                browser.onValueReceived(variable, value, service, this);
                break;
            case "NumberReturned":
            case "TotalMatches":
            case "UpdateID":
                break;
            default:
                super.onValueReceived(variable, value, service);
                break;
        }
    }

    void browsingFinished() {
        CompletableFuture<Boolean> browsing = isBrowsing;
        if (browsing != null) { // wait for maximum 2.5s until browsing is finished
            browsing.complete(true);
        }
        this.isBrowsing = null;
    }

    @Override
    protected void updateProtocolInfo(String value) {
        source.clear();
        source.addAll(Arrays.asList(value.split(",")));
    }

    @Nullable
    UpnpRendererHandler getRendererHandler() {
        return currentRendererHandler;
    }

    UpnpControlServerConfiguration getServerConfig() {
        return config;
    }

    /**
     * @return UPnP source definitions supported by the media server.
     */
    public List<String> getSource() {
        return source;
    }
}
