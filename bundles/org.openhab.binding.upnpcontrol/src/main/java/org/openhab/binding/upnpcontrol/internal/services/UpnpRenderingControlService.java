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
import org.openhab.binding.upnpcontrol.internal.util.UpnpXMLParser;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpRenderingControlService} represents a UPnP Rendering Control service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpRenderingControlService extends UpnpService {

    private final Logger logger = LoggerFactory.getLogger(UpnpRenderingControlService.class);

    private UpnpRenderingControlServiceConfiguration config;

    public UpnpRenderingControlService(UpnpHandler handler, ScheduledExecutorService upnpScheduler,
            UpnpRenderingControlServiceConfiguration config) {
        super(handler, upnpScheduler);

        this.config = config;
    }

    /**
     * Invoke ListPresets on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void listPresets(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "ListPresets", inputs);
    }

    /**
     * Invoke SelectPreset on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param presetName
     */
    public void selectPreset(int rcsId, String presetName) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("PresetName", presetName);

        invokeAction(RENDERING_CONTROL, "SelectPreset", inputs);
    }

    /**
     * Invoke GetBrightness on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getBrightness(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetBrightness", inputs);
    }

    /**
     * Invoke SetBrightness on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredBrightness
     */
    public void setBrightness(int rcsId, int desiredBrightness) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredBrightness", Integer.toString(desiredBrightness));

        invokeAction(RENDERING_CONTROL, "SetBrightness", inputs);
    }

    /**
     * Invoke GetContrast on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getContrast(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetContrast", inputs);
    }

    /**
     * Invoke SetContrast on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredContrast
     */
    public void setContrast(int rcsId, int desiredContrast) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredContrast", Integer.toString(desiredContrast));

        invokeAction(RENDERING_CONTROL, "SetContrast", inputs);
    }

    /**
     * Invoke GetSharpness on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getSharpness(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetSharpness", inputs);
    }

    /**
     * Invoke SetSharpness on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredSharpness
     */
    public void setSharpness(int rcsId, int desiredSharpness) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredSharpness", Integer.toString(desiredSharpness));

        invokeAction(RENDERING_CONTROL, "SetSharpness", inputs);
    }

    /**
     * Invoke GetRedVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getRedVideoGain(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetRedVideoGain", inputs);
    }

    /**
     * Invoke SetRedVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredRedVideoGain
     */
    public void setRedVideoGain(int rcsId, int desiredRedVideoGain) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredRedVideoGain", Integer.toString(desiredRedVideoGain));

        invokeAction(RENDERING_CONTROL, "SetRedVideoGain", inputs);
    }

    /**
     * Invoke GetGreenVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getGreenVideoGain(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetGreenVideoGain", inputs);
    }

    /**
     * Invoke SetGreenVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredGreenVideoGain
     */
    public void setGreenVideoGain(int rcsId, int desiredGreenVideoGain) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredGreenVideoGain", Integer.toString(desiredGreenVideoGain));

        invokeAction(RENDERING_CONTROL, "SetGreenVideoGain", inputs);
    }

    /**
     * Invoke GetBlueVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getBlueVideoGain(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetBlueVideoGain", inputs);
    }

    /**
     * Invoke SetBlueVideoGain on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredBlueVideoGain
     */
    public void setBlueVideoGain(int rcsId, int desiredBlueVideoGain) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredBlueVideoGain", Integer.toString(desiredBlueVideoGain));

        invokeAction(RENDERING_CONTROL, "SetBlueVideoGain", inputs);
    }

    /**
     * Invoke GetRedVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getRedVideoBlackLevel(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetRedVideoBlackLevel", inputs);
    }

    /**
     * Invoke SetRedVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredRedVideoBlackLevel
     */
    public void setRedVideoBlackLevel(int rcsId, int desiredRedVideoBlackLevel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredRedVideoBlackLevel", Integer.toString(desiredRedVideoBlackLevel));

        invokeAction(RENDERING_CONTROL, "SetRedVideoBlackLevel", inputs);
    }

    /**
     * Invoke GetGreenVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getGreenVideoBlackLevel(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetGreenVideoBlackLevel", inputs);
    }

    /**
     * Invoke SetGreenVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredGreenVideoBlackLevel
     */
    public void setGreenVideoBlackLevel(int rcsId, int desiredGreenVideoBlackLevel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredGreenVideoBlackLevel", Integer.toString(desiredGreenVideoBlackLevel));

        invokeAction(RENDERING_CONTROL, "SetGreenVideoBlackLevel", inputs);
    }

    /**
     * Invoke GetBlueVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getBlueVideoBlackLevel(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetBlueVideoBlackLevel", inputs);
    }

    /**
     * Invoke SetBlueVideoBlackLevel on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredBlueVideoBlackLevel
     */
    public void setBlueVideoBlackLevel(int rcsId, int desiredBlueVideoBlackLevel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredBlueVideoBlackLevel", Integer.toString(desiredBlueVideoBlackLevel));

        invokeAction(RENDERING_CONTROL, "SetBlueVideoBlackLevel", inputs);
    }

    /**
     * Invoke GetColorTemperature on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getColorTemperature(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetColorTemperature", inputs);
    }

    /**
     * Invoke SetColorTemperature on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredColorTemperature
     */
    public void setColorTemperature(int rcsId, int desiredColorTemperature) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredColorTemperature", Integer.toString(desiredColorTemperature));

        invokeAction(RENDERING_CONTROL, "SetColorTemperature", inputs);
    }

    /**
     * Invoke GetHorizontalKeystone on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getHorizontalKeystone(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetHorizontalKeystone", inputs);
    }

    /**
     * Invoke SetHorizontalKeystone on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredHorizontalKeystone
     */
    public void setHorizontalKeystone(int rcsId, int desiredHorizontalKeystone) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredHorizontalKeystone", Integer.toString(desiredHorizontalKeystone));

        invokeAction(RENDERING_CONTROL, "SetHorizontalKeystone", inputs);
    }

    /**
     * Invoke GetVerticalKeystone on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getVerticalKeystone(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetVerticalKeystone", inputs);
    }

    /**
     * Invoke SetVerticalKeystone on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param desiredVerticalKeystone
     */
    public void setVerticalKeystone(int rcsId, int desiredVerticalKeystone) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredVerticalKeystone", Integer.toString(desiredVerticalKeystone));

        invokeAction(RENDERING_CONTROL, "SetVerticalKeystone", inputs);
    }

    /**
     * Invoke getMute on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param channel
     */
    public void getMute(int rcsId, String channel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction(RENDERING_CONTROL, "GetMute", inputs);
    }

    /**
     * Invoke SetMute on UPnP Rendering Control.
     *
     * @param rcsId
     * @param channel
     * @param desiredMute
     */
    public void setMute(int rcsId, String channel, OnOffType desiredMute) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredMute", desiredMute == OnOffType.ON ? "1" : "0");

        invokeAction(RENDERING_CONTROL, "SetMute", inputs);
    }

    /**
     * Invoke GetVolume on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param channel
     */
    public void getVolume(int rcsId, String channel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction(RENDERING_CONTROL, "GetVolume", inputs);
    }

    /**
     * Invoke SetVolume on UPnP Rendering Control.
     *
     * @param rcsId
     * @param channel
     * @param desiredVolume
     */
    public void setVolume(int rcsId, String channel, PercentType desiredVolume) {
        UpnpRenderingControlServiceConfiguration config = this.config;

        long newVolume = desiredVolume.intValue() * config.maxvolume / 100;
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredVolume", String.valueOf(newVolume));

        invokeAction(RENDERING_CONTROL, "SetVolume", inputs);
    }

    /**
     * Invoke GetVolumeDB on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param channel
     */
    public void getVolumeDB(int rcsId, String channel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction(RENDERING_CONTROL, "GetVolumeDB", inputs);
    }

    /**
     * Invoke SetVolumeDB on UPnP Rendering Control.
     *
     * @param rcsId
     * @param channel
     * @param desiredVolume
     */
    public void setVolumeDB(int rcsId, String channel, int desiredVolume) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredVolume", Integer.toString(desiredVolume));

        invokeAction(RENDERING_CONTROL, "SetVolumeDB", inputs);
    }

    /**
     * Invoke getVolumeDBRange on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param channel
     */
    public void getVolumeDBRange(int rcsId, String channel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction(RENDERING_CONTROL, "getVolumeDBRange", inputs);
    }

    /**
     * Invoke GetLoudness on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param channel
     */
    public void getLoudness(int rcsId, String channel) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction(RENDERING_CONTROL, "GetLoudness", inputs);
    }

    /**
     * Invoke SetLoudness on UPnP Rendering Control.
     *
     * @param rcsId
     * @param channel
     * @param desiredLoudness
     */
    public void setLoudness(int rcsId, String channel, OnOffType desiredLoudness) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredLoudness", desiredLoudness == OnOffType.ON ? "1" : "0");

        invokeAction(RENDERING_CONTROL, "SetLoudness", inputs);
    }

    /**
     * Invoke GetStateVariables on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     * @param stateVariableList
     */
    public void getStateVariables(int rcsId, String stateVariableList) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("StateVariableList", stateVariableList);

        invokeAction(RENDERING_CONTROL, "GetStateVariables", inputs);
    }

    /**
     * Invoke SetStateVariables on UPnP Rendering Control.
     *
     * @param rcsId
     * @param deviceUdn
     * @param serviceType
     * @param serviceId
     * @param stateVariableValuePairs
     */
    public void setStateVariables(int rcsId, String deviceUdn, String serviceType, String serviceId,
            String stateVariableValuePairs) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("RenderingControlUDN", deviceUdn);
        inputs.put("ServiceType", serviceType);
        inputs.put("ServiceId", serviceId);
        inputs.put("StateVariableValuePairs", stateVariableValuePairs);

        invokeAction(RENDERING_CONTROL, "SetStateVariables", inputs);
    }

    /**
     * Invoke GetAllowedTransforms on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getAllowedTransforms(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetAllowedTransforms", inputs);
    }

    /**
     * Invoke GetTransforms on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     *
     * @param rcsId
     */
    public void getTransforms(int rcsId) {
        Map<@Nullable String, @Nullable String> inputs = Collections.singletonMap(INSTANCE_ID, Integer.toString(rcsId));

        invokeAction(RENDERING_CONTROL, "GetTransforms", inputs);
    }

    /**
     * Invoke SetTransforms on UPnP Rendering Control.
     *
     * @param rcsId
     * @param desiredTransformValues
     */
    public void setTransforms(int rcsId, String desiredTransformValues) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredTransformValues", desiredTransformValues);

        invokeAction(RENDERING_CONTROL, "SetTransforms", inputs);
    }

    /**
     * Invoke GetAllowedDefaultTransforms on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     */
    public void getAllowedDefaultTransforms() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(RENDERING_CONTROL, "GetAllowedDefaultTransforms", inputs);
    }

    /**
     * Invoke GetDefaultTransforms on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     */
    public void getDefaultTransforms() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(RENDERING_CONTROL, "GetDefaultTransforms", inputs);
    }

    /**
     * Invoke SetDefaultTransforms on UPnP Rendering Control.
     *
     * @param desiredDefaultTransformSettings
     */
    public void setDefaultTransforms(int rcsId, String desiredDefaultTransformSettings) {
        Map<@Nullable String, @Nullable String> inputs = new HashMap<>();
        inputs.put(INSTANCE_ID, Integer.toString(rcsId));
        inputs.put("DesiredDefaultTransformSettings", desiredDefaultTransformSettings);

        invokeAction(RENDERING_CONTROL, "SetDefaultTransforms", inputs);
    }

    /**
     * Invoke GetAllAvailableTransforms on UPnP Rendering Control.
     * Result is received in {@link #onValueReceived}.
     */
    public void getAllAvailableTransforms() {
        Map<@Nullable String, @Nullable String> inputs = Collections.emptyMap();

        invokeAction(RENDERING_CONTROL, "GetAllAvailableTransforms", inputs);
    }

    @Override
    public void onValueReceived(String variable, String value) {
        if (variable.endsWith("Volume")) {
            onValueReceivedVolume(variable, value);
        } else if (variable.endsWith("Mute")) {
            onValueReceivedMute(variable, value);
        } else if (variable.endsWith("Loudness")) {
            onValueReceivedLoudness(variable, value);
        } else if ("LastChange".equals(variable)) {
            onValueReceivedLastChange(value);
        }
    }

    private void onValueReceivedVolume(String variable, String value) {
        if (!value.isEmpty()) {
            long volume = Long.valueOf(value);
            volume = volume * 100 / config.maxvolume;

            String upnpChannel = variable.replace("Volume", "volume").replace("Master", "");
            handler.updateState(upnpChannel, new PercentType((int) volume));

            if (!playingNotification && "volume".equals(upnpChannel)) {
                soundVolume = new PercentType((int) volume);
            }
        }
    }

    private void onValueReceivedMute(String variable, String value) {
        if (!value.isEmpty()) {
            String upnpChannel = variable.replace("Mute", "mute").replace("Master", "");
            handler.updateState(upnpChannel,
                    ("1".equals(value) || "true".equals(value.toLowerCase())) ? OnOffType.ON : OnOffType.OFF);
        }
    }

    private void onValueReceivedLoudness(String variable, String value) {
        if (!value.isEmpty()) {
            String upnpChannel = variable.replace("Loudness", "loudness").replace("Master", "");
            handler.updateState(upnpChannel,
                    ("1".equals(value) || "true".equals(value.toLowerCase())) ? OnOffType.ON : OnOffType.OFF);
        }
    }

    private void onValueReceivedLastChange(String value) {
        // This is returned from a GENA subscription. The jupnp library does not allow receiving new GENA subscription
        // messages as long as this thread has not finished. As we may trigger long running processes based on this
        // result, we run it in a separate thread.
        upnpScheduler.submit(() -> {
            // pre-process some variables, eg XML processing
            if (!value.isEmpty()) {
                Map<String, @Nullable String> parsedValues = UpnpXMLParser.getRenderingControlFromXML(value);
                for (String parsedValue : parsedValues.keySet()) {
                    String result = parsedValues.get(parsedValue);
                    if (result != null) {
                        onValueReceived(parsedValue, result);
                    }
                }
            }
        });
    }
}
