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
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
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
 */
@NonNullByDefault
public class UpnpServerHandler extends UpnpHandler {

    private final Logger logger = LoggerFactory.getLogger(UpnpServerHandler.class);

    ConcurrentMap<String, UpnpRendererHandler> upnpRenderers;
    private volatile @Nullable UpnpRendererHandler currentRendererHandler;
    private volatile List<StateOption> rendererStateOptionList = Collections.synchronizedList(new ArrayList<>());

    private @NonNullByDefault({}) ChannelUID rendererChannelUID;

    private volatile @Nullable CompletableFuture<Boolean> isBrowsing;

    @NonNullByDefault({})
    UpnpServerBrowser browser;

    protected @NonNullByDefault({}) UpnpControlServerConfiguration config;

    private volatile List<String> source = new ArrayList<>();

    public UpnpServerHandler(Thing thing, UpnpIOService upnpIOService,
            ConcurrentMap<String, UpnpRendererHandler> upnpRenderers,
            UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider,
            UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider,
            UpnpControlBindingConfiguration configuration) {
        super(thing, upnpIOService, configuration, upnpStateDescriptionProvider, upnpCommandDescriptionProvider);
        this.upnpRenderers = upnpRenderers;
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(UpnpControlServerConfiguration.class);
        browser = new UpnpServerBrowser(this);

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
            rendererStateOptionList = Collections.synchronizedList(new ArrayList<>());
            synchronized (rendererStateOptionList) {
                upnpRenderers.forEach((key, value) -> {
                    StateOption stateOption = new StateOption(key, value.getThing().getLabel());
                    rendererStateOptionList.add(stateOption);
                });
            }
            updateStateDescription(rendererChannelUID, rendererStateOptionList);

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
     */
    public void browse(String objectID, String browseFlag, String filter, String startingIndex, String requestedCount,
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

            invokeAction(callback, CONTENT_DIRECTORY, "Browse", inputs);
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
     */
    public void search(String containerID, String searchCriteria, String filter, String startingIndex,
            String requestedCount, String sortCriteria, @Nullable UpnpInvocationCallback callback) {
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

            invokeAction(callback, CONTENT_DIRECTORY, "Search", inputs);
        } else {
            logger.debug("Cannot search, cancelled querying server {}", thing.getLabel());
        }
    }

    protected void updateServerState(ChannelUID channelUID, State state) {
        updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} for channel {} on server {}", command, channelUID, thing.getLabel());

        switch (channelUID.getId()) {
            case UPNPRENDERER:
                handleCommandUpnpRenderer(channelUID, command);
                break;
            case CURRENTTITLE:
                browser.currentTitle(this, command);
                break;
            case BROWSE:
                browser.browse(this, command);
                break;
            case SEARCH:
                browser.search(this, command);
                break;
            case PLAYLIST_SELECT:
                browser.playlistSelect(this, command);
                break;
            case PLAYLIST:
                browser.playlist(this, command);
                break;
            case PLAYLIST_ACTION:
                browser.playlistAction(this, command);
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
        UpnpRendererHandler renderer = null;
        UpnpRendererHandler previousRenderer = currentRendererHandler;
        if (command instanceof StringType) {
            renderer = (upnpRenderers.get(((StringType) command).toString()));
            currentRendererHandler = renderer;
            if (config.filter) {
                // only refresh title list if filtering by renderer capabilities
                browser.serverBrowse();
            } else {
                browser.serveMedia();
            }
        }

        if ((renderer != null) && !renderer.equals(previousRenderer)) {
            if (previousRenderer != null) {
                previousRenderer.unsetServerHandler();
            }
            renderer.setServerHandler(this);

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

        if ((renderer = currentRendererHandler) != null) {
            updateState(channelUID, StringType.valueOf(renderer.getThing().getUID().toString()));
        } else {
            updateState(channelUID, UnDefType.UNDEF);
        }
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
                rendererStateOptionList.add(new StateOption(key, handler.getThing().getLabel()));
            }
        }
        updateStateDescription(rendererChannelUID, rendererStateOptionList);
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
        if ((handler != null) && (handler.getThing().getUID().toString().equals(key))) {
            currentRendererHandler = null;
            updateState(rendererChannelUID, UnDefType.UNDEF);
        }
        synchronized (rendererStateOptionList) {
            rendererStateOptionList.removeIf(stateOption -> (stateOption.getValue().equals(key)));
        }
        updateStateDescription(rendererChannelUID, rendererStateOptionList);
        logger.debug("Renderer option {} removed from {} with udn {}", key, thing.getLabel(), getDeviceUDN());
    }

    @Override
    protected void onValueReceived(@Nullable UpnpInvocationCallback callback, @Nullable String variable,
            @Nullable String value, @Nullable String service) {
        if (callback != null && "Result".equals(variable)) {
            callback.onValueReceived(callback.getHandler(), variable, value, service);
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
                browser.onValueReceived(this, variable, value, service);
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

    @Override
    protected void updateProtocolInfo(String value) {
        source.clear();
        source.addAll(Arrays.asList(value.split(",")));
    }

    protected @Nullable UpnpRendererHandler getRendererHandler() {
        return currentRendererHandler;
    }

    protected UpnpControlServerConfiguration getServerConfig() {
        return config;
    }

    /**
     * @return UPnP source definitions supported by the media server.
     */
    public List<String> getSource() {
        return source;
    }
}
