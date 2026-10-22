/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.mybmw.internal.stream;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.stream.StreamMessageParser.StreamMessage;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttConnectionObserver;
import org.openhab.core.io.transport.mqtt.MqttConnectionState;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Receives the CarData stream from a local MQTT broker that the
 * <a href="https://github.com/dj0abr/bmw-mqtt-bridge">bmw-mqtt-bridge</a> republishes to.
 * <p>
 * The bridge holds the single stream connection BMW allows per account and publishes every message unchanged on
 * {@code <prefix>raw/<vin>}. It reports its own connection to BMW as retained JSON on {@code <prefix>status},
 * {@code {"connected": true|false, "timestamp": ...}}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class BridgeStreamSource implements StreamSource, MqttMessageSubscriber, MqttConnectionObserver {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private final Logger logger = LoggerFactory.getLogger(BridgeStreamSource.class);

    private final String host;
    private final int port;
    private final boolean secure;
    private final String username;
    private final String password;
    private final String clientId;
    private final String dataTopic;
    private final String statusTopic;
    private final ScheduledExecutorService scheduler;
    private final StreamListener listener;

    private final Object stateLock = new Object();

    private @Nullable MqttBrokerConnection connection;
    private volatile boolean stopped = true;
    private volatile boolean localConnected = false;
    private volatile @Nullable Boolean upstreamConnected;
    private volatile @Nullable StreamState lastState;

    /**
     * @param host local broker host
     * @param port local broker port
     * @param secure whether to use TLS
     * @param username broker username, empty for none
     * @param password broker password
     * @param topicPrefix the bridge's LOCAL_PREFIX, ending with a slash
     * @param clientId MQTT client id to use
     * @param scheduler scheduler for connection timeouts
     * @param listener receiver of data and state changes
     */
    public BridgeStreamSource(String host, int port, boolean secure, String username, String password,
            String topicPrefix, String clientId, ScheduledExecutorService scheduler, StreamListener listener) {
        this.host = host;
        this.port = port;
        this.secure = secure;
        this.username = username;
        this.password = password;
        this.clientId = clientId;
        this.dataTopic = topicPrefix + "raw/#";
        this.statusTopic = topicPrefix + "status";
        this.scheduler = scheduler;
        this.listener = listener;
    }

    @Override
    public synchronized void start() {
        if (!stopped) {
            return;
        }
        stopped = false;
        localConnected = false;
        upstreamConnected = null;
        lastState = null;

        MqttBrokerConnection connection = new MqttBrokerConnection(host, port, secure, clientId);
        if (!username.isBlank()) {
            connection.setCredentials(username, password);
        }
        connection.setTimeoutExecutor(scheduler, CONNECT_TIMEOUT_MS);
        connection.addConnectionObserver(this);
        // subscriptions are registered before connecting and restored by the connection after every reconnect
        connection.subscribe(dataTopic, this);
        connection.subscribe(statusTopic, this);
        this.connection = connection;
        publishState(null);
        logger.debug("Connecting to local broker {}:{} for CarData stream topics {} and {}", host, port, dataTopic,
                statusTopic);
        connection.start().exceptionally(e -> {
            connectionStateChanged(MqttConnectionState.DISCONNECTED, e);
            return false;
        });
    }

    @Override
    public synchronized void stop() {
        synchronized (stateLock) {
            // waits for a state callback in progress, later ones see the flag
            stopped = true;
        }
        MqttBrokerConnection connection = this.connection;
        this.connection = null;
        if (connection != null) {
            connection.removeConnectionObserver(this);
            connection.unsubscribe(dataTopic, this);
            connection.unsubscribe(statusTopic, this);
            connection.stop();
        }
    }

    @Override
    public void connectionStateChanged(MqttConnectionState state, @Nullable Throwable error) {
        if (stopped) {
            return;
        }
        localConnected = state == MqttConnectionState.CONNECTED;
        if (!localConnected) {
            // the retained status message is delivered again on reconnect
            upstreamConnected = null;
        }
        publishState(error != null ? error.getMessage() : null);
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        if (stopped) {
            return;
        }
        if (topic.equals(statusTopic)) {
            Boolean connected = parseStatus(payload);
            if (connected != null) {
                upstreamConnected = connected;
                publishState(null);
            }
            return;
        }
        StreamMessage message = StreamMessageParser.parse(topic, payload);
        if (message == null) {
            logger.debug("Ignoring message on {} that is not a CarData envelope", topic);
            return;
        }
        if (!Boolean.TRUE.equals(upstreamConnected)) {
            // data is flowing, so the bridge is connected even if its status message was missed
            upstreamConnected = true;
            publishState(null);
        }
        listener.onTelematicData(message.vin(), message.values());
    }

    /**
     * Reports the current state if it changed. State changes come from several MQTT client threads; a dedicated lock
     * keeps the reported sequence consistent with the latest state. The listener must not call back into
     * {@link #stop()} synchronously, which it does not need to as {@link #stop()} uses a different lock.
     */
    private void publishState(@Nullable String detail) {
        synchronized (stateLock) {
            if (stopped) {
                return;
            }
            StreamState state;
            Boolean upstream = upstreamConnected;
            if (!localConnected) {
                state = StreamState.DISCONNECTED;
            } else if (upstream == null) {
                state = StreamState.CONNECTED_UPSTREAM_UNKNOWN;
            } else {
                state = upstream ? StreamState.CONNECTED : StreamState.UPSTREAM_DISCONNECTED;
            }
            if (state != lastState || detail != null) {
                lastState = state;
                listener.onStreamStateChanged(state, detail);
            }
        }
    }

    private static @Nullable Boolean parseStatus(byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8).strip();
        try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonObject()) {
                JsonElement connected = json.getAsJsonObject().get("connected");
                if (connected != null && connected.isJsonPrimitive()) {
                    return Boolean.parseBoolean(connected.getAsString());
                }
                return null;
            }
            if (json.isJsonPrimitive()) {
                return Boolean.parseBoolean(json.getAsString());
            }
        } catch (JsonParseException e) {
            // not JSON, fall through
        }
        return null;
    }
}
