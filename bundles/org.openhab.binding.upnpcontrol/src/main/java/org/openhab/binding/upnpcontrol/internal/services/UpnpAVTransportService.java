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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpHandler;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpRendererHandler;
import org.openhab.binding.upnpcontrol.internal.queue.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpAVTransportService} represents a UPnP AV Transport service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpAVTransportService extends UpnpService {

    private final Logger logger = LoggerFactory.getLogger(UpnpAVTransportService.class);

    public UpnpAVTransportService(UpnpHandler handler, ScheduledExecutorService upnpScheduler) {
        super(handler, upnpScheduler);
    }

    /**
     * Invoke SetAVTransportURI on UPnP AV Transport.
     *
     * @param avTransportId
     * @param URI
     * @param URIMetaData
     */
    public void setAVTransportURI(int avTransportId, String URI, String URIMetaData) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("CurrentURI", URI);
        inputs.put("CurrentURIMetaData", URIMetaData);

        invokeAction(AV_TRANSPORT, "SetAVTransportURI", inputs);
    }

    /**
     * Invoke SetNextAVTransportURI on UPnP AV Transport.
     *
     * @param avTransportId
     * @param nextURI
     * @param nextURIMetaData
     */
    public void setNextAVTransportURI(int avTransportId, String nextURI, String nextURIMetaData) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("NextURI", nextURI);
        inputs.put("NextURIMetaData", nextURIMetaData);

        invokeAction(AV_TRANSPORT, "SetNextAVTransportURI", inputs);
    }

    /**
     * Invoke GetMediaInfo on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getMediaInfo(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetMediaInfo", inputs);
    }

    /**
     * Invoke GetMediaInfo_Ext on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getMediaInfoExt(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetMediaInfo_Ext", inputs);
    }

    /**
     * Invoke GetTransportState on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getTransportState(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetTransportInfo", inputs);
    }

    /**
     * Invoke GetPositionInfo on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getPositionInfo(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetPositionInfo", inputs);
    }

    /**
     * Invoke GetDeviceCapabilities on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getDeviceCapabilities(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetDeviceCapabilities", inputs);
    }

    /**
     * Invoke GetTransportSettings on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getTransportSettings(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetTransportSettings", inputs);
    }

    /**
     * Invoke Stop on UPnP AV Transport.
     *
     * @param avTransportId
     */
    public void stop(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "Stop", inputs);
    }

    /**
     * Invoke Play on UPnP AV Transport.
     *
     * @param avTransportId
     * @param speed
     */
    public void play(int avTransportId, String speed) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("Speed", speed);

        invokeAction(AV_TRANSPORT, "Play", inputs);
    }

    /**
     * Invoke Pause on UPnP AV Transport.
     *
     * @param avTransportId
     */
    public void pause(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "Pause", inputs);
    }

    /**
     * Invoke Seek on UPnP AV Transport.
     *
     * @param avTransportId
     * @param unit
     * @param seekTarget relative position in current track
     */
    public void seek(int avTransportId, String unit, String seekTarget) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("Unit", unit);
        inputs.put("Target", seekTarget);

        invokeAction(AV_TRANSPORT, "Seek", inputs);
    }

    /**
     * Invoke Next on UPnP AV Transport.
     *
     * @param avTransportId
     */
    public void next(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "Next", inputs);
    }

    /**
     * Invoke Previous on UPnP AV Transport.
     *
     * @param avTransportId
     */
    public void previous(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "Previous", inputs);
    }

    /**
     * Invoke SetPlayMode on UPnP AV Transport.
     *
     * @param avTransportId
     * @param newPlayMode
     */
    public void setPlayMode(int avTransportId, String newPlayMode) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("NewPlayMode", newPlayMode);

        invokeAction(AV_TRANSPORT, "SetPlayMode", inputs);
    }

    /**
     * Invoke GetCurrentTransportActions on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getCurrentTransportActions(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetCurrentTransportActions", inputs);
    }

    /**
     * Invoke GetDRMState on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getDRMState(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetDRMState", inputs);
    }

    /**
     * Invoke GetSyncOffset on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     * @param stateVariableList
     */
    public void getSyncOffset(int avTransportId, String stateVariableList) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("StateVariableList", stateVariableList);

        invokeAction(AV_TRANSPORT, "GetSyncOffset", inputs);
    }

    /**
     * Invoke SetSyncOffset on UPnP AV Transport.
     *
     * @param avTransportId
     * @param deviceUdn
     * @param serviceType
     * @param serviceId
     * @param stateVariableValuePairs
     */
    public void setSyncOffset(int avTransportId, String deviceUdn, String serviceType, String serviceId,
            String stateVariableValuePairs) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("AVTransportUDN", deviceUdn);
        inputs.put("ServiceType", serviceType);
        inputs.put("ServiceId", serviceId);
        inputs.put("StateVariableValuePairs", stateVariableValuePairs);

        invokeAction(AV_TRANSPORT, "SetSyncOffset", inputs);
    }

    /**
     * Invoke GetSyncOffset on UPnP AV Transport.
     * Result is received in {@link #onValueReceived}.
     *
     * @param avTransportId
     */
    public void getSyncOffset(int avTransportId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID,
                Integer.toString(avTransportId));

        invokeAction(AV_TRANSPORT, "GetSyncOffset", inputs);
    }

    /**
     * Invoke SetSyncOffset on UPnP AV Transport.
     *
     * @param avTransportId
     * @param newSyncOffset
     */
    public void setSyncOffset(int avTransportId, String newSyncOffset) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("NewSyncOffset", newSyncOffset);

        invokeAction(AV_TRANSPORT, "SetSyncOffset", inputs);
    }

    /**
     * Invoke AdjustSyncOffset on UPnP AV Transport.
     *
     * @param avTransportId
     * @param adjustment
     */
    public void adjustSyncOffset(int avTransportId, String adjustment) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("Adjustment", adjustment);

        invokeAction(AV_TRANSPORT, "AdjustSyncOffset", inputs);
    }

    /**
     * Invoke SyncPlay on UPnP AV Transport.
     *
     * @param avTransportId
     * @param speed
     * @param referencePositionUnits
     * @param referencePosition
     * @param referencePresentationTime
     * @param referenceClockId
     */
    public void syncPlay(int avTransportId, String speed, String referencePositionUnits, String referencePosition,
            String referencePresentationTime, String referenceClockId) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("Speed", speed);
        inputs.put("ReferencePositionUnits", referencePositionUnits);
        inputs.put("ReferencePosition", referencePosition);
        inputs.put("ReferencePresentationTime", referencePresentationTime);
        inputs.put("ReferenceClockId", referenceClockId);

        invokeAction(AV_TRANSPORT, "SyncPlay", inputs);
    }

    /**
     * Invoke SyncStop on UPnP AV Transport.
     *
     * @param avTransportId
     * @param stopTime
     * @param referenceClockId
     */
    public void syncStop(int avTransportId, String stopTime, String referenceClockId) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("StopTime", stopTime);
        inputs.put("ReferenceClockId", referenceClockId);

        invokeAction(AV_TRANSPORT, "SyncStop", inputs);
    }

    /**
     * Invoke SyncPause on UPnP AV Transport.
     *
     * @param avTransportId
     * @param pauseTime
     * @param referenceClockId
     */
    public void syncPause(int avTransportId, String pauseTime, String referenceClockId) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(avTransportId));
        inputs.put("PauseTime", pauseTime);
        inputs.put("ReferenceClockId", referenceClockId);

        invokeAction(AV_TRANSPORT, "SyncPause", inputs);
    }

    @Override
    public void onValueReceived(String variable, String value) {
        switch (variable) {
            case "LastChange":
                onValueReceivedLastChange(value);
                break;
            case "CurrentTransportState":
            case "TransportState":
                onValueReceivedTransportState(value);
                break;
            case "CurrentTrackURI":
            case "CurrentURI":
                onValueReceivedCurrentURI(value);
                break;
            case "CurrentTrackMetaData":
            case "CurrentURIMetaData":
                onValueReceivedCurrentMetaData(value);
                break;
            case "NextAVTransportURIMetaData":
            case "NextURIMetaData":
                onValueReceivedNextMetaData(value);
                break;
            case "CurrentTrackDuration":
            case "TrackDuration":
                onValueReceivedDuration(value);
                break;
            case "RelTime":
                onValueReceivedRelTime(value);
                break;
        }
    }

    private void onValueReceivedLastChange(String value) {
        // This is returned from a GENA subscription. The jupnp library does not allow receiving new GENA subscription
        // messages as long as this thread has not finished. As we may trigger long running processes based on this
        // result, we run it in a separate thread.
        upnpScheduler.submit(() -> {
            // pre-process some variables, eg XML processing
            if (!value.isEmpty()) {
                Map<String, String> parsedValues = UpnpXMLParser.getAVTransportFromXML(value);
                for (Map.Entry<String, String> entrySet : parsedValues.entrySet()) {
                    switch (entrySet.getKey()) {
                        case "TransportState":
                            // Update the transport state after the update of the media information
                            // to not break the notification mechanism
                            break;
                        case "AVTransportURI":
                            onValueReceived("CurrentTrackURI", entrySet.getValue());
                            break;
                        case "AVTransportURIMetaData":
                            onValueReceived("CurrentTrackMetaData", entrySet.getValue());
                            break;
                        default:
                            onValueReceived(entrySet.getKey(), entrySet.getValue());
                    }
                }
                if (parsedValues.containsKey("TransportState")) {
                    String result = parsedValues.get("TransportState");
                    if (result != null) {
                        onValueReceived("TransportState", result);
                    }
                }
            }
        });
    }

    private void onValueReceivedTransportState(@Nullable String value) {
        transportState = (value == null) ? "" : value;

        if ("STOPPED".equals(value)) {
            CompletableFuture<Boolean> stopping = isStopping;
            if (stopping != null) {
                stopping.complete(true); // We have received stop confirmation
                isStopping = null;
            }

            if (playingNotification) {
                resumeAfterNotification();
                return;
            }

            cancelCheckPaused();
            updateState(CONTROL, PlayPauseType.PAUSE);
            cancelTrackPositionRefresh();
            // Only go to next for first STOP command, then wait until we received PLAYING before moving
            // to next (avoids issues with renderers sending multiple stop states)
            if (playing) {
                playing = false;

                // playerStopped is true if stop came from openHAB. This allows us to identify if we played to the
                // end of an entry, because STOP would come from the player and not from openHAB. We should then
                // move to the next entry if the queue is not at the end already.
                if (!playerStopped) {
                    if (Instant.now().toEpochMilli() >= expectedTrackend) {
                        // If we are receiving track duration info, we know when the track is expected to end. If we
                        // received STOP before track end, and it is not coming from openHAB, it must have been stopped
                        // from the renderer directly, and we do not want to play the next entry.
                        if (playingQueue) {
                            serveNext();
                        }
                    }
                } else if (playingQueue) {
                    playingQueue = false;
                }
            }
        } else if ("PLAYING".equals(value)) {
            if (playingNotification) {
                return;
            }

            playerStopped = false;
            playing = true;
            registeredQueue = false; // reset queue registration flag as we are playing something
            updateState(CONTROL, PlayPauseType.PLAY);
            ((UpnpRendererHandler) handler).scheduleTrackPositionRefresh();
        } else if ("PAUSED_PLAYBACK".equals(value)) {
            cancelCheckPaused();
            updateState(CONTROL, PlayPauseType.PAUSE);
        } else if ("NO_MEDIA_PRESENT".equals(value)) {
            updateState(CONTROL, UnDefType.UNDEF);
        }
    }

    private void onValueReceivedCurrentURI(@Nullable String value) {
        CompletableFuture<Boolean> settingURI = isSettingURI;
        if (settingURI != null) {
            settingURI.complete(true); // We have received current URI, so can allow play to start
        }

        UpnpEntry current = currentEntry;
        UpnpEntry next = nextEntry;

        String uri = "";
        String currentUri = "";
        String nextUri = "";
        if (value != null) {
            uri = URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        }
        if (current != null) {
            currentUri = URLDecoder.decode(current.getRes().trim(), StandardCharsets.UTF_8);
        }
        if (next != null) {
            nextUri = URLDecoder.decode(next.getRes(), StandardCharsets.UTF_8);
        }

        if (playingNotification && uri.equals(notificationUri)) {
            // No need to update anything more if this is for playing a notification
            return;
        }

        nowPlayingUri = uri;
        updateState(URI, StringType.valueOf(uri));

        logger.trace("Renderer {} received URI: {}", thing.getLabel(), uri);
        logger.trace("Renderer {} current URI: {}, equal to received URI {}", thing.getLabel(), currentUri,
                uri.equals(currentUri));
        logger.trace("Renderer {} next URI: {}", thing.getLabel(), nextUri);

        if (!uri.equals(currentUri)) {
            if ((next != null) && uri.equals(nextUri)) {
                // Renderer advanced to next entry independent of openHAB UPnP control point.
                // Advance in the queue to keep proper position status.
                // Make the next entry available to renderers that support it.
                logger.trace("Renderer {} moved from '{}' to next entry '{}' in queue", thing.getLabel(), current,
                        next);
                currentEntry = currentQueue.next();
                nextEntry = currentQueue.get(currentQueue.nextIndex());
                logger.trace("Renderer {} auto move forward, current queue index: {}", thing.getLabel(),
                        currentQueue.index());

                updateMetaDataState(next);

                // look one further to get next entry for next URI
                next = nextEntry;
                if ((next != null) && !onlyplayone) {
                    setNextURI(next.getRes(), UpnpXMLParser.compileMetadataString(next));
                }
            } else {
                // A new entry is being served that does not match the next entry in the queue. This can be because a
                // sound or stream is being played through an action, or another control point started a new entry.
                // We should clear the metadata in this case and wait for new metadata to arrive.
                clearMetaDataState();
            }
        }
    }

    private void onValueReceivedCurrentMetaData(@Nullable String value) {
        if (playingNotification) {
            // Don't update metadata when playing notification
            return;
        }

        if (value != null && !value.isEmpty()) {
            List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
            if (!list.isEmpty()) {
                updateMetaDataState(list.get(0));
                return;
            }
        }
        clearMetaDataState();
    }

    private void onValueReceivedNextMetaData(@Nullable String value) {
        if (value != null && !value.isEmpty() && !"NOT_IMPLEMENTED".equals(value)) {
            List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
            if (!list.isEmpty()) {
                nextEntry = list.get(0);
            }
        }
    }

    private void onValueReceivedDuration(@Nullable String value) {
        // track duration and track position have format H+:MM:SS[.F+] or H+:MM:SS[.F0/F1]. We are not
        // interested in the fractional seconds, so drop everything after . and calculate in seconds.
        if (value == null || "NOT_IMPLEMENTED".equals(value)) {
            trackDuration = 0;
            updateState(TRACK_DURATION, UnDefType.UNDEF);
            updateState(REL_TRACK_POSITION, UnDefType.UNDEF);
        } else {
            try {
                trackDuration = Arrays.stream(value.split("\\.")[0].split(":")).mapToInt(n -> Integer.parseInt(n))
                        .reduce(0, (n, m) -> n * 60 + m);
                updateState(TRACK_DURATION, new QuantityType<>(trackDuration, Units.SECOND));
            } catch (NumberFormatException e) {
                logger.debug("Illegal format for track duration {}", value);
                return;
            }
        }
        setExpectedTrackend();
    }

    private void onValueReceivedRelTime(@Nullable String value) {
        if (value == null || "NOT_IMPLEMENTED".equals(value)) {
            trackPosition = 0;
            updateState(TRACK_POSITION, UnDefType.UNDEF);
            updateState(REL_TRACK_POSITION, UnDefType.UNDEF);
        } else {
            try {
                trackPosition = Arrays.stream(value.split("\\.")[0].split(":")).mapToInt(n -> Integer.parseInt(n))
                        .reduce(0, (n, m) -> n * 60 + m);
                updateState(TRACK_POSITION, new QuantityType<>(trackPosition, Units.SECOND));
                int relPosition = (trackDuration != 0) ? trackPosition * 100 / trackDuration : 0;
                updateState(REL_TRACK_POSITION, new PercentType(relPosition));
            } catch (NumberFormatException e) {
                logger.trace("Illegal format for track position {}", value);
                return;
            }
        }

        if (playingNotification) {
            posAtNotificationStart = trackPosition;
        }

        setExpectedTrackend();
    }
}
