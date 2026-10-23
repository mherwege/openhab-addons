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
package org.openhab.binding.mybmw.internal.handler;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mybmw.internal.LogRedaction;
import org.openhab.binding.mybmw.internal.api.CarDataApi;
import org.openhab.binding.mybmw.internal.api.CarDataApiException;
import org.openhab.binding.mybmw.internal.api.RequestBudget;
import org.openhab.binding.mybmw.internal.api.dto.Container;
import org.openhab.binding.mybmw.internal.api.dto.VehicleMapping;
import org.openhab.binding.mybmw.internal.auth.CarDataAuthException;
import org.openhab.binding.mybmw.internal.auth.CarDataAuthenticator;
import org.openhab.binding.mybmw.internal.auth.CarDataTokens;
import org.openhab.binding.mybmw.internal.auth.DeviceAuthorization;
import org.openhab.binding.mybmw.internal.auth.TokenSet;
import org.openhab.binding.mybmw.internal.data.TelematicChannel;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.binding.mybmw.internal.discovery.VehicleDiscoveryService;
import org.openhab.binding.mybmw.internal.stream.BridgeStreamSource;
import org.openhab.binding.mybmw.internal.stream.StreamListener;
import org.openhab.binding.mybmw.internal.stream.StreamSource;
import org.openhab.binding.mybmw.internal.stream.StreamState;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.storage.Storage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Handler of a CarData account, the bridge for its vehicles.
 * <p>
 * It authorizes against BMW with the device code flow, hands the tokens to openHAB's OAuth client service, which
 * stores them encrypted and refreshes them, spends the account's small daily REST budget on mappings, basic data and
 * occasional telematic refreshes, and dispatches stream data to the vehicle handlers. In this version the stream is
 * received from a local broker fed by the bmw-mqtt-bridge.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class AccountHandler extends BaseBridgeHandler implements StreamListener {

    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);
    private static final Duration RETRY_INITIAL_DELAY = Duration.ofMinutes(1);
    private static final Duration RETRY_MAX_DELAY = Duration.ofMinutes(30);
    private static final Duration CONFIGURATION_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration BOOTSTRAP_DELAY = Duration.ofSeconds(10);
    private static final Duration INITIAL_TELEMATICS_DELAY = Duration.ofMinutes(2);
    private static final Duration CATCH_UP_AFTER_OUTAGE = Duration.ofMinutes(5);
    private static final Duration CATCH_UP_MIN_SPACING = Duration.ofMinutes(30);
    private static final Duration MAPPINGS_MAX_AGE = Duration.ofHours(1);
    private static final String BASIC_DATA_KEY = "/basicData/";
    private static final Duration SLOW_DOWN_INCREMENT = Duration.ofSeconds(5);

    private static final String CONTAINER_NAME = "openHAB";
    private static final String CONTAINER_PURPOSE = "openHAB MyBMW binding ";

    private enum AuthState {
        STARTING,
        AUTHORIZING,
        AUTHORIZED,
        COMMUNICATION_ERROR,
        CONFIGURATION_ERROR
    }

    private final Logger logger = LoggerFactory.getLogger(AccountHandler.class);

    private final HttpClient httpClient;
    private final OAuthFactory oAuthFactory;
    private final Storage<String> storage;
    private final AccessTokenRefreshListener refreshListener = this::onAccessTokenResponse;

    // Incremented on every initialize and dispose; scheduled work of an older generation does nothing.
    private final AtomicInteger generation = new AtomicInteger();
    private final Map<String, VehicleHandler> vehicles = new ConcurrentHashMap<>();

    private AccountConfiguration config = new AccountConfiguration();
    private @Nullable CarDataAuthenticator authenticator;
    private volatile @Nullable OAuthClientService oAuthService;
    private @Nullable CarDataApi api;
    private @Nullable StreamSource stream;
    private volatile @Nullable VehicleDiscoveryService discoveryService;

    // Latest tokens, updated whenever core's OAuth client service imports or refreshes them.
    private volatile @Nullable TokenSet tokens;

    // Serializes REST work, so concurrent triggers do not spend the budget twice.
    private final Object restLock = new Object();
    private volatile List<VehicleMapping> mappings = List.of();
    private volatile @Nullable Instant mappingsFetched;

    private final Object jobLock = new Object();
    private final Object statusLock = new Object();
    private @Nullable ScheduledFuture<?> authJob;
    private @Nullable ScheduledFuture<?> bootstrapJob;
    private @Nullable ScheduledFuture<?> telematicsJob;

    private volatile AuthState authState = AuthState.STARTING;
    private volatile @Nullable String authDescription;
    // the device authorization the user has to confirm, shown on the authorization page
    private volatile @Nullable DeviceAuthorization pendingAuthorization;
    private volatile StreamState streamState = StreamState.CONNECTING;
    private volatile @Nullable String streamDetail;
    private volatile @Nullable Instant streamDownSince;
    private volatile @Nullable Instant lastCatchUp;
    private volatile boolean scopeHintLogged;
    // Whether the first telematic refresh ran; vehicles added later get their own refresh
    private volatile boolean telematicsStarted;

    // Delay before retrying a failed authorization step; reset when authorized
    private final RetryBackoff authBackoff = new RetryBackoff(RETRY_INITIAL_DELAY, RETRY_MAX_DELAY);

    public AccountHandler(Bridge bridge, HttpClient httpClient, OAuthFactory oAuthFactory, Storage<String> storage) {
        super(bridge);
        this.httpClient = httpClient;
        this.oAuthFactory = oAuthFactory;
        this.storage = storage;
    }

    @Override
    public void initialize() {
        AccountConfiguration config = getConfigAs(AccountConfiguration.class);
        this.config = config;
        int gen = generation.incrementAndGet();
        authState = AuthState.STARTING;
        streamState = StreamState.CONNECTING;
        telematicsStarted = false;
        scopeHintLogged = false;
        authBackoff.reset();

        if (config.clientId.isBlank()) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.no-client-id");
            return;
        }
        if (config.brokerHost.isBlank()) {
            updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, "@text/offline.no-broker");
            return;
        }

        String key = getThing().getUID().getAsString();
        String clientId = config.clientId.strip();
        CarDataAuthenticator authenticator = new CarDataAuthenticator(httpClient, clientId);
        this.authenticator = authenticator;

        String authorizedClientId = storage.get(clientIdKey());
        if (authorizedClientId != null && !authorizedClientId.equals(clientId)) {
            logger.debug("The client ID changed, the existing authorization is discarded");
            deleteAuthorization();
        }
        // no scope: BMW does not expect one on refresh, the scopes come from the device authorization
        OAuthClientService oAuthService = oAuthFactory.createOAuthClientService(key, CarDataAuthenticator.TOKEN_URL,
                null, clientId, null, null, false);
        oAuthService.addAccessTokenRefreshListener(refreshListener);
        this.oAuthService = oAuthService;

        // per client ID rather than per thing, as BMW counts per account, also when the account thing is recreated
        storage.remove(key + "/budget");
        RequestBudget budget = new RequestBudget(storage, "budget/" + clientId, config.requestBudget,
                Clock.systemUTC());
        api = new CarDataApi(httpClient, this::getAccessToken, () -> accessTokenRejected(gen), budget);

        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> startAuthorization(gen));
    }

    @Override
    public void dispose() {
        generation.incrementAndGet();
        synchronized (jobLock) {
            authJob = cancel(authJob);
            bootstrapJob = cancel(bootstrapJob);
            telematicsJob = cancel(telematicsJob);
        }
        stopStream();
        OAuthClientService oAuthService = this.oAuthService;
        this.oAuthService = null;
        if (oAuthService != null) {
            oAuthService.removeAccessTokenRefreshListener(refreshListener);
            oAuthFactory.ungetOAuthService(getThing().getUID().getAsString());
        }
        tokens = null;
        pendingAuthorization = null;
        mappings = List.of();
        mappingsFetched = null;
    }

    /**
     * @return the device authorization the user has to confirm, {@code null} when none is pending
     */
    public @Nullable DeviceAuthorization getPendingAuthorization() {
        @Nullable
        DeviceAuthorization authorization = pendingAuthorization;
        if (authorization == null || authState != AuthState.AUTHORIZING || authorization.isExpired(Instant.now())) {
            return null;
        }
        return authorization;
    }

    /**
     * @return whether the account is authorized at BMW
     */
    public boolean isAuthorized() {
        return authState == AuthState.AUTHORIZED;
    }

    @Override
    public void handleRemoval() {
        String key = getThing().getUID().getAsString();
        deleteAuthorization();
        storage.remove(key + "/container");
        for (String storageKey : List.copyOf(storage.getKeys())) {
            if (storageKey.startsWith(key + BASIC_DATA_KEY)) {
                storage.remove(storageKey);
            }
        }
        // the request budget is kept: BMW keeps counting the requests for this account
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // the account has no channels
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(VehicleDiscoveryService.class);
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof VehicleHandler vehicle && !vehicle.getVin().isEmpty()) {
            vehicles.put(vehicle.getVin(), vehicle);
            int gen = generation.get();
            if (authState == AuthState.AUTHORIZED) {
                scheduler.execute(() -> {
                    if (vehicle.needsBasicData()) {
                        fetchBasicData(gen, List.of(vehicle));
                    }
                    if (telematicsStarted) {
                        // added after the first refresh, which did not include it
                        refreshTelematics(gen, "vehicle added", List.of(vehicle));
                    }
                });
            }
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        vehicles.values().remove(childHandler);
    }

    public void setDiscoveryService(@Nullable VehicleDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
        if (discoveryService != null && mappingsFetched != null) {
            // fetched before the discovery service was set up
            discoveryService.vehiclesFetched(mappings);
        }
    }

    /**
     * @return the vehicles mapped to the account; from a recent fetch when available, to protect the request budget
     */
    public List<VehicleMapping> getVehicleMappings() {
        if (authState != AuthState.AUTHORIZED) {
            return mappings;
        }
        synchronized (restLock) {
            try {
                return refreshMappings();
            } catch (IOException | CarDataApiException e) {
                logger.debug("Could not fetch vehicle mappings: {}", LogRedaction.vins(e.getMessage()));
            } catch (CarDataAuthException e) {
                handleAuthException(generation.get(), e);
            }
        }
        return mappings;
    }

    private void startAuthorization(int gen) {
        if (!isCurrent(gen)) {
            return;
        }
        OAuthClientService oAuthService = this.oAuthService;
        if (oAuthService == null) {
            return;
        }
        AccessTokenResponse stored;
        try {
            // refreshes when the stored token has expired
            stored = oAuthService.getAccessTokenResponse();
        } catch (OAuthResponseException e) {
            handleAuthException(gen, CarDataTokens.toAuthException(e));
            return;
        } catch (OAuthException e) {
            handleAuthException(gen, CarDataTokens.toAuthException(e));
            return;
        } catch (IOException e) {
            logger.debug("Token refresh failed: {}", e.getMessage());
            setAuthState(gen, AuthState.COMMUNICATION_ERROR, e.getMessage());
            scheduleAuthJob(gen, authBackoff.next(), () -> refreshTokens(gen));
            return;
        }
        if (stored == null || stored.getRefreshToken() == null) {
            // never authorized, or the authorization was discarded
            startDeviceFlow(gen);
            return;
        }
        // confirm the stored refresh token still works and start from a fresh set
        refreshTokens(gen);
    }

    private void startDeviceFlow(int gen) {
        CarDataAuthenticator authenticator = this.authenticator;
        if (!isCurrent(gen) || authenticator == null) {
            return;
        }
        try {
            DeviceAuthorization authorization = authenticator.startDeviceAuthorization();
            pendingAuthorization = authorization;
            setAuthState(gen, AuthState.AUTHORIZING, "@text/offline.authorize");
            scheduleAuthJob(gen, authorization.interval(),
                    () -> pollDeviceFlow(gen, authorization, authorization.interval()));
        } catch (IOException e) {
            setAuthState(gen, AuthState.COMMUNICATION_ERROR, e.getMessage());
            scheduleAuthJob(gen, authBackoff.next(), () -> startDeviceFlow(gen));
        } catch (CarDataAuthException e) {
            // e.g. an unknown client ID or a client not subscribed to the services
            setAuthState(gen, AuthState.CONFIGURATION_ERROR, e.getMessage());
            scheduleAuthJob(gen, authBackoff.next(CONFIGURATION_RETRY_DELAY), () -> startDeviceFlow(gen));
        }
    }

    private void pollDeviceFlow(int gen, DeviceAuthorization authorization, Duration interval) {
        CarDataAuthenticator authenticator = this.authenticator;
        if (!isCurrent(gen) || authenticator == null) {
            return;
        }
        if (authorization.isExpired(Instant.now())) {
            logger.debug("Device authorization expired without confirmation, requesting a new code");
            startDeviceFlow(gen);
            return;
        }
        try {
            TokenSet newTokens = authenticator.pollForToken(authorization);
            importTokens(newTokens);
            authBackoff.reset();
            logger.debug("Device authorization confirmed, tokens obtained");
            onAuthorized(gen);
        } catch (CarDataAuthException e) {
            if (e.isPending()) {
                Duration next = CarDataAuthException.SLOW_DOWN.equals(e.getError()) ? interval.plus(SLOW_DOWN_INCREMENT)
                        : interval;
                scheduleAuthJob(gen, next, () -> pollDeviceFlow(gen, authorization, next));
            } else if (CarDataAuthException.EXPIRED_TOKEN.equals(e.getError())) {
                startDeviceFlow(gen);
            } else {
                // BMW's device authorization is known to reject confirmations intermittently; offer a new code
                logger.debug("Device authorization failed: {}", e.getMessage());
                setAuthState(gen, AuthState.CONFIGURATION_ERROR,
                        "@text/offline.authorization-failed [\"" + e.getError() + "\"]");
                scheduleAuthJob(gen, authBackoff.next(), () -> startDeviceFlow(gen));
            }
        } catch (IOException e) {
            logger.debug("Polling for device authorization failed: {}", e.getMessage());
            scheduleAuthJob(gen, interval, () -> pollDeviceFlow(gen, authorization, interval));
        } catch (OAuthException e) {
            // the service was closed, the handler is being disposed
            logger.debug("Could not store the tokens: {}", e.getMessage());
        }
    }

    private void refreshTokens(int gen) {
        OAuthClientService oAuthService = this.oAuthService;
        if (!isCurrent(gen) || oAuthService == null) {
            return;
        }
        try {
            onAccessTokenResponse(oAuthService.refreshToken());
            if (tokens == null) {
                throw new CarDataAuthException(CarDataAuthException.INVALID_GRANT, "incomplete token response");
            }
            authBackoff.reset();
            onAuthorized(gen);
        } catch (CarDataAuthException e) {
            handleAuthException(gen, e);
        } catch (OAuthResponseException e) {
            handleAuthException(gen, CarDataTokens.toAuthException(e));
        } catch (OAuthException e) {
            handleAuthException(gen, CarDataTokens.toAuthException(e));
        } catch (IOException e) {
            CarDataAuthException unauthorized = CarDataTokens.fromUnauthorizedResponse(e);
            if (unauthorized != null) {
                handleAuthException(gen, unauthorized);
                return;
            }
            logger.debug("Token refresh failed: {}", e.getMessage());
            TokenSet current = tokens;
            if (current != null && current.isValid(Instant.now(), Duration.ZERO)) {
                // the current tokens still work for a while, try again before they expire
                if (authState != AuthState.AUTHORIZED) {
                    onAuthorized(gen);
                }
                Duration delay = authBackoff.next();
                Duration remaining = Duration.between(Instant.now(), current.expiresAt());
                scheduleAuthJob(gen, delay.compareTo(remaining) < 0 ? delay : remaining, () -> refreshTokens(gen));
            } else {
                setAuthState(gen, AuthState.COMMUNICATION_ERROR, e.getMessage());
                scheduleAuthJob(gen, authBackoff.next(), () -> refreshTokens(gen));
            }
        }
    }

    /**
     * Hands the tokens of a completed device authorization to core's OAuth client service, which stores them
     * encrypted and refreshes them from then on.
     */
    private void importTokens(TokenSet newTokens) throws OAuthException {
        OAuthClientService oAuthService = this.oAuthService;
        if (oAuthService == null) {
            throw new OAuthException("OAuth client service not available");
        }
        oAuthService.importAccessTokenResponse(CarDataTokens.toAccessTokenResponse(newTokens, Instant.now()));
        // not secret: lets a changed client ID be detected, and keeps the stream username if a refresh omits it
        storage.put(clientIdKey(), newTokens.clientId());
        storage.put(gcidKey(), newTokens.gcid());
        tokens = newTokens;
    }

    /**
     * Called by core's OAuth client service after every successful refresh, also when a REST call triggered it.
     */
    private void onAccessTokenResponse(AccessTokenResponse response) {
        TokenSet refreshed = CarDataTokens.fromAccessTokenResponse(config.clientId.strip(), response, tokens,
                storage.get(gcidKey()));
        if (refreshed == null) {
            logger.debug("Refreshed token response lacks the id_token or gcid needed for the stream");
            return;
        }
        storage.put(gcidKey(), refreshed.gcid());
        tokens = refreshed;
    }

    /**
     * Called by the REST client when BMW rejects the access token before it expired, e.g. after the authorization was
     * revoked in the portal. A refresh either yields a working token or leads to a new authorization.
     */
    private void accessTokenRejected(int gen) {
        if (isCurrent(gen) && authState == AuthState.AUTHORIZED) {
            logger.debug("CarData rejected the access token, refreshing it");
            scheduleAuthJob(gen, Duration.ZERO, () -> refreshTokens(gen));
        }
    }

    /**
     * Supplies the REST client with a valid access token. Core refreshes it first when it has expired.
     */
    private String getAccessToken() throws IOException, CarDataAuthException {
        OAuthClientService oAuthService = this.oAuthService;
        if (oAuthService == null) {
            throw new CarDataAuthException(CarDataAuthException.INVALID_GRANT, "not authorized");
        }
        try {
            AccessTokenResponse response = oAuthService.getAccessTokenResponse();
            String accessToken = response != null ? response.getAccessToken() : null;
            if (accessToken == null) {
                throw new CarDataAuthException(CarDataAuthException.INVALID_GRANT, "not authorized");
            }
            return accessToken;
        } catch (OAuthResponseException e) {
            throw CarDataTokens.toAuthException(e);
        } catch (OAuthException e) {
            throw CarDataTokens.toAuthException(e);
        } catch (IOException e) {
            // core's refresh of an expired token failed
            CarDataAuthException unauthorized = CarDataTokens.fromUnauthorizedResponse(e);
            if (unauthorized != null) {
                throw unauthorized;
            }
            throw e;
        }
    }

    /**
     * Discards the stored authorization, so the next start requires a new device authorization.
     */
    private void deleteAuthorization() {
        oAuthFactory.deleteServiceAndAccessToken(getThing().getUID().getAsString());
        storage.remove(clientIdKey());
        storage.remove(gcidKey());
        tokens = null;
    }

    private String clientIdKey() {
        return getThing().getUID().getAsString() + "/clientId";
    }

    private String gcidKey() {
        return getThing().getUID().getAsString() + "/gcid";
    }

    private void handleAuthException(int gen, CarDataAuthException e) {
        if (!isCurrent(gen)) {
            return;
        }
        if (e.requiresReauthorization()) {
            if (authState == AuthState.AUTHORIZING) {
                // a device authorization is already pending, keep the code the user may be entering
                return;
            }
            logger.debug("Authorization no longer valid ({}), a new authorization is needed", e.getError());
            OAuthClientService oAuthService = this.oAuthService;
            if (oAuthService != null) {
                try {
                    // clear the stored token, but keep the service for the import after the new authorization
                    oAuthService.importAccessTokenResponse(new AccessTokenResponse());
                } catch (OAuthException ex) {
                    // closed, the handler is being disposed
                }
            }
            tokens = null;
            stopStream();
            scheduleAuthJob(gen, Duration.ZERO, () -> startDeviceFlow(gen));
        } else {
            setAuthState(gen, AuthState.COMMUNICATION_ERROR, e.getMessage());
            scheduleAuthJob(gen, authBackoff.next(), () -> refreshTokens(gen));
        }
    }

    private void onAuthorized(int gen) {
        if (!isCurrent(gen)) {
            return;
        }
        TokenSet current = tokens;
        if (current == null) {
            return;
        }
        if (!scopeHintLogged && !current.scope().isBlank() && !current.hasScope(CarDataAuthenticator.SCOPE_API)) {
            scopeHintLogged = true;
            // not fatal for the stream, but REST calls will be refused
            logger.info(
                    "The CarData authorization does not include {} (granted: {}). "
                            + "Subscribe the client ID to the CarData API in the portal and re-authorize.",
                    CarDataAuthenticator.SCOPE_API, current.scope());
        }

        setAuthState(gen, AuthState.AUTHORIZED, null);
        Duration untilRefresh = Duration.between(Instant.now(), current.expiresAt().minus(TOKEN_REFRESH_MARGIN));
        scheduleAuthJob(gen, untilRefresh.isNegative() ? Duration.ZERO : untilRefresh, () -> refreshTokens(gen));
        startStream();
        scheduleDataJobs(gen);
    }

    private void setAuthState(int gen, AuthState state, @Nullable String description) {
        if (!isCurrent(gen)) {
            // work of a disposed generation finishing late
            return;
        }
        authState = state;
        authDescription = description;
        if (state != AuthState.AUTHORIZING) {
            pendingAuthorization = null;
        }
        updateAccountStatus();
    }

    private void startStream() {
        synchronized (jobLock) {
            if (stream != null) {
                return;
            }
            AccountConfiguration config = this.config;
            StreamSource stream = new BridgeStreamSource(config.brokerHost.strip(), config.brokerPort,
                    config.brokerSecure, config.brokerUsername, config.brokerPassword, config.normalizedTopicPrefix(),
                    "openhab-mybmw-" + getThing().getUID().getId(), scheduler, this);
            this.stream = stream;
            streamDownSince = Instant.now();
            stream.start();
        }
    }

    private void stopStream() {
        StreamSource stream;
        synchronized (jobLock) {
            stream = this.stream;
            this.stream = null;
        }
        if (stream != null) {
            stream.stop();
        }
        streamState = StreamState.CONNECTING;
    }

    @Override
    public void onTelematicData(String vin, List<TelematicValue> values) {
        VehicleHandler vehicle = vehicles.get(vin);
        if (vehicle != null) {
            vehicle.onTelematicData(values);
        } else {
            logger.trace("Stream data for vehicle {} without a thing", LogRedaction.vin(vin));
        }
    }

    @Override
    public void onStreamStateChanged(StreamState state, @Nullable String detail) {
        streamState = state;
        streamDetail = detail;
        Instant now = Instant.now();
        if (state == StreamState.CONNECTED) {
            Instant downSince = streamDownSince;
            streamDownSince = null;
            Instant last = lastCatchUp;
            if (downSince != null && Duration.between(downSince, now).compareTo(CATCH_UP_AFTER_OUTAGE) >= 0
                    && (last == null || Duration.between(last, now).compareTo(CATCH_UP_MIN_SPACING) >= 0)) {
                // the stream does not replay what happened while it was down
                lastCatchUp = now;
                int gen = generation.get();
                scheduler.execute(() -> refreshTelematics(gen, "stream outage"));
            }
        } else if (streamDownSince == null) {
            streamDownSince = now;
        }
        updateAccountStatus();
    }

    /**
     * Derives the thing status from the authorization and stream states. Serialized so concurrent changes are reported
     * in order; the framework's own monitor on the handler is not used for this.
     */
    private void updateAccountStatus() {
        synchronized (statusLock) {
            switch (authState) {
                case STARTING -> updateStatus(ThingStatus.UNKNOWN);
                case AUTHORIZING -> updateStatus(ThingStatusDetail.AUTHORIZATION_PENDING, authDescription);
                case COMMUNICATION_ERROR -> updateStatus(ThingStatusDetail.COMMUNICATION_ERROR, authDescription);
                case CONFIGURATION_ERROR -> updateStatus(ThingStatusDetail.CONFIGURATION_ERROR, authDescription);
                case AUTHORIZED -> {
                    switch (streamState) {
                        case CONNECTING -> updateStatus(ThingStatus.UNKNOWN, "@text/unknown.broker-connecting");
                        case DISCONNECTED -> {
                            String detail = streamDetail;
                            updateStatus(ThingStatusDetail.COMMUNICATION_ERROR,
                                    detail != null
                                            ? "@text/offline.broker-detail [\"" + detail.replace("\"", "'") + "\"]"
                                            : "@text/offline.broker");
                        }
                        case UPSTREAM_DISCONNECTED ->
                            updateStatus(ThingStatus.ONLINE, "@text/online.bridge-disconnected");
                        case CONNECTED_UPSTREAM_UNKNOWN ->
                            updateStatus(ThingStatus.ONLINE, "@text/online.bridge-unknown");
                        case CONNECTED -> updateStatus(ThingStatus.ONLINE);
                    }
                }
            }
        }
    }

    private void scheduleDataJobs(int gen) {
        synchronized (jobLock) {
            if (bootstrapJob == null) {
                bootstrapJob = scheduler.schedule(() -> bootstrap(gen), BOOTSTRAP_DELAY.toSeconds(), TimeUnit.SECONDS);
            }
            if (telematicsJob == null) {
                if (config.refreshInterval > 0) {
                    long period = TimeUnit.HOURS.toSeconds(config.refreshInterval);
                    telematicsJob = scheduler.scheduleWithFixedDelay(() -> refreshTelematics(gen, "periodic"),
                            INITIAL_TELEMATICS_DELAY.toSeconds(), period, TimeUnit.SECONDS);
                } else {
                    telematicsJob = scheduler.schedule(() -> refreshTelematics(gen, "startup"),
                            INITIAL_TELEMATICS_DELAY.toSeconds(), TimeUnit.SECONDS);
                }
            }
        }
    }

    /**
     * Fetches the vehicle list for discovery when the account has no vehicles yet, and the basic data of vehicles that
     * do not have it yet. An account with vehicles does not spend a request on the vehicle list at every start; a scan
     * fetches it when the user looks for another vehicle.
     */
    private void bootstrap(int gen) {
        if (!isCurrent(gen)) {
            return;
        }
        synchronized (restLock) {
            try {
                if (getThing().getThings().isEmpty()) {
                    refreshMappings();
                }
            } catch (IOException | CarDataApiException e) {
                logger.debug("Could not fetch vehicle mappings: {}", LogRedaction.vins(e.getMessage()));
            } catch (CarDataAuthException e) {
                handleAuthException(gen, e);
                return;
            }
        }
        fetchBasicData(gen, vehicles.values().stream().filter(VehicleHandler::needsBasicData).toList());
    }

    /**
     * Must be called holding the REST lock.
     */
    private List<VehicleMapping> refreshMappings() throws IOException, CarDataAuthException, CarDataApiException {
        Instant fetched = mappingsFetched;
        if (fetched != null && Duration.between(fetched, Instant.now()).compareTo(MAPPINGS_MAX_AGE) < 0) {
            return mappings;
        }
        CarDataApi api = this.api;
        if (api == null) {
            return mappings;
        }
        List<VehicleMapping> fresh = api.getMappings();
        mappings = fresh;
        mappingsFetched = Instant.now();
        cacheBasicData(api, fresh);
        for (VehicleMapping mapping : fresh) {
            String vin = mapping.vin;
            String mappingType = mapping.mappingType;
            VehicleHandler vehicle = vin != null ? vehicles.get(vin) : null;
            if (vehicle != null && mappingType != null) {
                vehicle.updateMappingType(mappingType);
            }
        }
        VehicleDiscoveryService discoveryService = this.discoveryService;
        if (discoveryService != null) {
            discoveryService.vehiclesFetched(fresh);
        }
        return fresh;
    }

    /**
     * Fetches the basic data of the primary vehicles that are not in the cache yet, so discovery can leave out
     * vehicles that cannot send data and label the others with their model. A vehicle thing created later reuses the
     * cached data. Must be called holding the REST lock.
     */
    private void cacheBasicData(CarDataApi api, List<VehicleMapping> mappings) throws CarDataAuthException {
        for (VehicleMapping mapping : mappings) {
            String vin = mapping.vin;
            if (vin == null || vin.isBlank() || !VehicleMapping.PRIMARY.equals(mapping.mappingType)
                    || storage.containsKey(basicDataKey(vin))) {
                continue;
            }
            try {
                basicData(api, vin);
            } catch (IOException | CarDataApiException e) {
                logger.debug("Could not fetch basic data of vehicle {}: {}", LogRedaction.vin(vin),
                        LogRedaction.vins(e.getMessage()));
                if (e instanceof CarDataApiException apiException && apiException.isRateLimited()) {
                    return;
                }
            }
        }
    }

    /**
     * @param vin vehicle identification number
     * @return the vehicle's basic data if it was fetched before, {@code null} otherwise
     */
    public @Nullable JsonObject getCachedBasicData(String vin) {
        String cached = storage.get(basicDataKey(vin));
        if (cached != null) {
            try {
                JsonElement json = JsonParser.parseString(cached);
                if (json.isJsonObject()) {
                    return json.getAsJsonObject();
                }
            } catch (JsonParseException e) {
                // fetched again below
            }
            storage.remove(basicDataKey(vin));
        }
        return null;
    }

    /**
     * Returns the vehicle's basic data from the cache, or fetches and caches it. Must be called holding the REST lock.
     */
    private JsonObject basicData(CarDataApi api, String vin)
            throws IOException, CarDataAuthException, CarDataApiException {
        JsonObject cached = getCachedBasicData(vin);
        if (cached != null) {
            return cached;
        }
        JsonObject fetched = api.getBasicData(vin);
        storage.put(basicDataKey(vin), fetched.toString());
        return fetched;
    }

    private String basicDataKey(String vin) {
        return getThing().getUID().getAsString() + BASIC_DATA_KEY + vin;
    }

    private void fetchBasicData(int gen, List<VehicleHandler> targets) {
        CarDataApi api = this.api;
        if (!isCurrent(gen) || api == null || targets.isEmpty()) {
            return;
        }
        synchronized (restLock) {
            for (VehicleHandler vehicle : targets) {
                if (!vehicle.needsBasicData()) {
                    continue;
                }
                try {
                    vehicle.updateBasicData(basicData(api, vehicle.getVin()));
                } catch (IOException | CarDataApiException e) {
                    logger.debug("Could not fetch basic data of vehicle {}: {}", LogRedaction.vin(vehicle.getVin()),
                            LogRedaction.vins(e.getMessage()));
                    if (e instanceof CarDataApiException apiException && apiException.isRateLimited()) {
                        return;
                    }
                } catch (CarDataAuthException e) {
                    handleAuthException(gen, e);
                    return;
                }
            }
        }
    }

    /**
     * Reads the current values of all mapped descriptors for every vehicle, one request per vehicle.
     */
    private void refreshTelematics(int gen, String reason) {
        CarDataApi api = this.api;
        if (!isCurrent(gen) || api == null || authState != AuthState.AUTHORIZED) {
            return;
        }
        telematicsStarted = true;
        refreshTelematics(gen, reason, new ArrayList<>(vehicles.values()));
    }

    /**
     * Reads the current values of all mapped descriptors for the given vehicles, one request per vehicle.
     */
    private void refreshTelematics(int gen, String reason, List<VehicleHandler> targets) {
        CarDataApi api = this.api;
        if (!isCurrent(gen) || api == null || authState != AuthState.AUTHORIZED || targets.isEmpty()) {
            return;
        }
        synchronized (restLock) {
            logger.debug("Refreshing telematic data ({})", reason);
            try {
                String containerId = ensureContainer(api);
                for (VehicleHandler vehicle : targets) {
                    try {
                        List<TelematicValue> values = api.getTelematicData(vehicle.getVin(), containerId);
                        vehicle.onTelematicData(values);
                    } catch (CarDataApiException e) {
                        if (e.isUnknownContainer()) {
                            // deleted in the portal, create a new one next time
                            storage.remove(containerKey());
                            throw e;
                        }
                        if (e.isRateLimited()) {
                            throw e;
                        }
                        logger.debug("Could not refresh vehicle {}: {}", LogRedaction.vin(vehicle.getVin()),
                                LogRedaction.vins(e.getMessage()));
                    }
                }
            } catch (CarDataApiException e) {
                if (CarDataApiException.TOO_MANY_CONTAINERS.equals(e.getErrorId())) {
                    logger.warn("Cannot create a CarData container, the account already has the maximum of 10. "
                            + "Delete an unused container in the CarData portal.");
                } else {
                    logger.debug("Telematic refresh stopped: {}", LogRedaction.vins(e.getMessage()));
                }
            } catch (IOException e) {
                logger.debug("Telematic refresh failed: {}", LogRedaction.vins(e.getMessage()));
            } catch (CarDataAuthException e) {
                handleAuthException(gen, e);
            }
        }
    }

    /**
     * Returns the id of the binding's container, reusing one that exists for the current descriptor set, so restarts
     * or a lost storage do not pile up containers against BMW's limit of 10. Containers of this binding for another
     * descriptor set, left by an earlier version, are deleted first. Must be called holding the REST lock.
     */
    private String ensureContainer(CarDataApi api) throws IOException, CarDataAuthException, CarDataApiException {
        List<String> descriptors = TelematicChannel.descriptors();
        String signature = Integer.toHexString(descriptors.hashCode());
        String stored = storage.get(containerKey());
        if (stored != null && stored.endsWith("|" + signature)) {
            return stored.substring(0, stored.length() - signature.length() - 1);
        }
        String purpose = CONTAINER_PURPOSE + signature;
        List<String> obsolete = new ArrayList<>();
        String id = selectContainer(api.listContainers(), purpose, obsolete);
        // before creating, as BMW refuses an 11th container
        deleteContainers(api, obsolete);
        if (id == null) {
            Container created = api.createContainer(new Container.CreateRequest(CONTAINER_NAME, purpose, descriptors));
            id = created.containerId;
            if (id == null) {
                throw new IOException("Container creation returned no id");
            }
            logger.debug("Created CarData container {} with {} descriptors", id, descriptors.size());
        }
        storage.put(containerKey(), id + "|" + signature);
        return id;
    }

    /**
     * Picks the container to use from the account's containers. Containers of other applications are left alone.
     *
     * @param containers the containers of the account
     * @param purpose the purpose of the container for the current descriptor set
     * @param obsolete receives the ids of this binding's containers that are not needed: those for another
     *            descriptor set, inactive ones and duplicates
     * @return the id of an active container for the current descriptor set, {@code null} when there is none
     */
    static @Nullable String selectContainer(List<Container> containers, String purpose, List<String> obsolete) {
        String selected = null;
        for (Container container : containers) {
            String id = container.containerId;
            String containerPurpose = container.purpose;
            if (id == null || !CONTAINER_NAME.equals(container.name) || containerPurpose == null
                    || !containerPurpose.startsWith(CONTAINER_PURPOSE)
                    || Container.STATE_DELETED.equals(container.state)) {
                continue;
            }
            if (selected == null && purpose.equals(containerPurpose)
                    && Container.STATE_ACTIVE.equals(container.state)) {
                selected = id;
            } else {
                obsolete.add(id);
            }
        }
        return selected;
    }

    /**
     * Deletes containers, as far as the request budget allows; a container that is not deleted now is deleted the next
     * time the binding needs a new container. Must be called holding the REST lock.
     */
    private void deleteContainers(CarDataApi api, List<String> ids) throws CarDataAuthException {
        for (String id : ids) {
            try {
                api.deleteContainer(id);
                logger.debug("Deleted obsolete CarData container {}", id);
            } catch (IOException | CarDataApiException e) {
                logger.debug("Could not delete obsolete CarData container {}: {}", id, e.getMessage());
                if (e instanceof CarDataApiException apiException && apiException.isRateLimited()) {
                    return;
                }
            }
        }
    }

    private String containerKey() {
        return getThing().getUID().getAsString() + "/container";
    }

    private boolean isCurrent(int gen) {
        return gen == generation.get();
    }

    private void scheduleAuthJob(int gen, Duration delay, Runnable job) {
        synchronized (jobLock) {
            if (!isCurrent(gen)) {
                return;
            }
            cancel(authJob);
            authJob = scheduler.schedule(job, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
        }
    }

    private static @Nullable ScheduledFuture<?> cancel(@Nullable ScheduledFuture<?> job) {
        if (job != null) {
            job.cancel(false);
        }
        return null;
    }
}
