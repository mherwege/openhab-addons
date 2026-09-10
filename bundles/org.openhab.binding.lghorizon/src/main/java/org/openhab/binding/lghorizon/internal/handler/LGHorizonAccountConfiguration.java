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
package org.openhab.binding.lghorizon.internal.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Configuration for the {@code account} bridge thing.
 *
 * @author Mark - Initial contribution
 */
@NonNullByDefault
public class LGHorizonAccountConfiguration {

    // Selects a known provider from {@link org.openhab.binding.lghorizon.internal.api.ProviderPresets},
    // e.g. {@code telenet}. When set, it determines {@link #country}, {@link #apiUrl} and
    // {@link #useRefreshToken} automatically. Leave empty to configure an unlisted provider (a new
    // white-label deployment, or a provider's preprod/test backend) manually via those three
    // advanced fields instead.
    public @Nullable String provider;

    // Two-letter locale code used in the service-discovery URL path (e.g. {@code be}, {@code nl},
    // {@code ch}) - not necessarily the same region as the API URL itself. Known automatically
    // when {@link #provider} is set; required if it is left empty.
    public String country = "";

    // Base URL of the provider's "spark" REST API, e.g. {@code https://spark-prod-be.gnp.cloud.telenet.tv}. Known
    // automatically when {@link #provider} is set; required if it is left empty.
    public String apiUrl = "";

    // * Whether the provider requires refresh-token auth instead of username/password. Known
    // automatically when {@link #provider} is set; only consulted if it is left empty.
    public boolean useRefreshToken = false;

    // Only used for password-based providers (e.g. Ziggo NL).
    public @Nullable String username;

    // Only used for password-based providers (e.g. Ziggo NL).
    public @Nullable String password;

    // Only used for token-based providers (e.g. Telenet BE, UPC/Sunrise CH, Virgin Media GB).
    // Refresh token extracted from the provider's web player. The binding will keep this up to date automatically once
    // it starts rotating tokens, but the very first value has to be supplied manually.
    public String refreshToken = "";

    // Poll interval (seconds) for the lightweight REST-based reachability check.
    public int refreshInterval = 300;
}
