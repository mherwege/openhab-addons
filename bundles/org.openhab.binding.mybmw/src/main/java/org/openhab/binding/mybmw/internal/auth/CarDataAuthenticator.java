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
package org.openhab.binding.mybmw.internal.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.openhab.binding.mybmw.internal.LogRedaction;
import org.openhab.binding.mybmw.internal.api.CarDataApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * OAuth 2.0 device authorization grant (RFC 8628) with PKCE (RFC 7636) against the BMW customer identity service.
 * <p>
 * This class only obtains the first set of tokens. openHAB core's RFC 8628 client cannot do that, because BMW requires
 * {@code response_type}, a PKCE {@code code_challenge} on the device code request and the matching
 * {@code code_verifier} on the token request, which that client does not send. Once obtained, the tokens are imported
 * into core's OAuth client service, which stores them and refreshes them using {@link #TOKEN_URL}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class CarDataAuthenticator {

    public static final String SCOPE_API = "cardata:api:read";
    public static final String SCOPE_STREAMING = "cardata:streaming:read";

    private static final String BASE_URL = "https://customer.bmwgroup.com";
    private static final String DEVICE_CODE_PATH = "/gcdm/oauth/device/code";
    private static final String TOKEN_PATH = "/gcdm/oauth/token";
    public static final String TOKEN_URL = BASE_URL + TOKEN_PATH;
    private static final String SCOPES = "authenticate_user openid " + SCOPE_API + " " + SCOPE_STREAMING;
    private static final String GRANT_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_CODE_LIFETIME = Duration.ofMinutes(5);
    private static final long TIMEOUT_SECONDS = 30;

    private final Logger logger = LoggerFactory.getLogger(CarDataAuthenticator.class);

    private final HttpClient httpClient;
    private final String clientId;
    private final String deviceCodeUrl;
    private final String tokenUrl;

    public CarDataAuthenticator(HttpClient httpClient, String clientId) {
        this(httpClient, clientId, BASE_URL);
    }

    // for tests against a local server
    CarDataAuthenticator(HttpClient httpClient, String clientId, String baseUrl) {
        this.httpClient = httpClient;
        this.clientId = clientId;
        this.deviceCodeUrl = baseUrl + DEVICE_CODE_PATH;
        this.tokenUrl = baseUrl + TOKEN_PATH;
    }

    /**
     * Starts a device authorization. The user has to open {@link DeviceAuthorization#userLink()} and confirm, after
     * which {@link #pollForToken(DeviceAuthorization)} returns the tokens.
     *
     * @return the pending authorization
     * @throws IOException on communication errors
     * @throws CarDataAuthException when BMW rejects the request, e.g. for an unknown client ID
     */
    public DeviceAuthorization startDeviceAuthorization() throws IOException, CarDataAuthException {
        String verifier = Pkce.generateVerifier();
        Fields fields = new Fields();
        fields.put("client_id", clientId);
        fields.put("response_type", "device_code");
        fields.put("scope", SCOPES);
        fields.put("code_challenge", Pkce.challenge(verifier));
        fields.put("code_challenge_method", "S256");

        JsonObject json = post(deviceCodeUrl, fields);
        String deviceCode = string(json, "device_code");
        String userCode = string(json, "user_code");
        String verificationUri = string(json, "verification_uri");
        if (deviceCode == null || userCode == null || verificationUri == null) {
            throw new IOException("Incomplete device code response");
        }
        Duration lifetime = seconds(json, "expires_in", DEFAULT_CODE_LIFETIME);
        Duration interval = seconds(json, "interval", DEFAULT_INTERVAL);
        DeviceAuthorization authorization = new DeviceAuthorization(deviceCode, userCode, verificationUri,
                string(json, "verification_uri_complete"), Instant.now().plus(lifetime), interval, verifier);
        logger.debug("Started device authorization {}", authorization);
        return authorization;
    }

    /**
     * Polls the token endpoint once for a pending device authorization.
     *
     * @param authorization the pending authorization
     * @return the tokens, once the user has confirmed
     * @throws CarDataAuthException with {@link CarDataAuthException#isPending()} while the user has not confirmed yet,
     *             or with another error when the authorization failed or expired
     * @throws IOException on communication errors
     */
    public TokenSet pollForToken(DeviceAuthorization authorization) throws IOException, CarDataAuthException {
        Fields fields = new Fields();
        fields.put("client_id", clientId);
        fields.put("device_code", authorization.deviceCode());
        fields.put("grant_type", GRANT_DEVICE_CODE);
        fields.put("code_verifier", authorization.codeVerifier());
        return toTokenSet(post(tokenUrl, fields));
    }

    private TokenSet toTokenSet(JsonObject json) throws IOException {
        String accessToken = string(json, "access_token");
        String refreshToken = string(json, "refresh_token");
        String idToken = string(json, "id_token");
        String gcid = string(json, "gcid");
        String scope = string(json, "scope");
        if (accessToken == null || refreshToken == null || idToken == null || gcid == null) {
            throw new IOException("Incomplete token response");
        }
        Duration lifetime = seconds(json, "expires_in", Duration.ofHours(1));
        return new TokenSet(clientId, gcid, accessToken, idToken, refreshToken, Instant.now().plus(lifetime),
                scope != null ? scope : "");
    }

    private JsonObject post(String url, Fields fields) throws IOException, CarDataAuthException {
        Request request = httpClient.newRequest(url).method(HttpMethod.POST)
                .header(HttpHeader.ACCEPT, "application/json").content(new FormContentProvider(fields))
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (logger.isTraceEnabled()) {
            logger.trace("BMW identity request POST {} form: {}", url, redact(fields));
        }
        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while contacting BMW identity service", e);
        } catch (ExecutionException e) {
            if (CarDataApi.isUnauthorizedResponse(e)) {
                // the error in the body is lost, see CarDataApi.isUnauthorizedResponse
                logger.trace("BMW identity response POST {} -> HTTP 401", url);
                throw new CarDataAuthException(CarDataAuthException.INVALID_CLIENT,
                        "BMW identity service answered HTTP 401");
            }
            throw new IOException("Failed to contact BMW identity service: " + e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new IOException("Failed to contact BMW identity service: " + e.getMessage(), e);
        }

        String content = response.getContentAsString();
        int status = response.getStatus();
        if (logger.isTraceEnabled()) {
            logger.trace("BMW identity response POST {} -> HTTP {}, body: {}", url, status,
                    LogRedaction.payload(content));
        }
        JsonObject json = parse(content);
        if (status == HttpStatus.OK_200) {
            return json;
        }
        String error = string(json, "error");
        if (error != null) {
            throw new CarDataAuthException(error, string(json, "error_description"));
        }
        throw new IOException("BMW identity service returned HTTP " + status);
    }

    private static String redact(Fields fields) {
        StringBuilder text = new StringBuilder();
        for (Fields.Field field : fields) {
            if (!text.isEmpty()) {
                text.append('&');
            }
            text.append(field.getName()).append('=').append(LogRedaction.value(field.getName(), field.getValue()));
        }
        return text.toString();
    }

    private static JsonObject parse(String content) throws IOException {
        try {
            JsonElement element = JsonParser.parseString(content);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (JsonParseException e) {
            throw new IOException("Unreadable response from BMW identity service", e);
        }
        return new JsonObject();
    }

    private static @Nullable String string(JsonObject json, String member) {
        JsonElement element = json.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.isBlank() ? null : value;
    }

    private static Duration seconds(JsonObject json, String member, Duration fallback) {
        JsonElement element = json.get(member);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            long seconds = element.getAsLong();
            if (seconds > 0) {
                return Duration.ofSeconds(seconds);
            }
        }
        return fallback;
    }
}
