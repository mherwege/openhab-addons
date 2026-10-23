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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An OAuth error response from the BMW customer identity service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class CarDataAuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public static final String AUTHORIZATION_PENDING = "authorization_pending";
    public static final String SLOW_DOWN = "slow_down";
    public static final String EXPIRED_TOKEN = "expired_token";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String INVALID_GRANT = "invalid_grant";
    public static final String INVALID_CLIENT = "invalid_client";

    private final String error;

    public CarDataAuthException(String error, @Nullable String description) {
        super(description == null || description.isBlank() ? error : error + ": " + description);
        this.error = error;
    }

    /**
     * @return the OAuth error code, e.g. {@code authorization_pending}
     */
    public String getError() {
        return error;
    }

    /**
     * @return whether the user still has to complete the authorization; polling should continue
     */
    public boolean isPending() {
        return AUTHORIZATION_PENDING.equals(error) || SLOW_DOWN.equals(error);
    }

    /**
     * @return whether the grant used is no longer valid and the user has to authorize again
     */
    public boolean requiresReauthorization() {
        return INVALID_GRANT.equals(error) || EXPIRED_TOKEN.equals(error) || ACCESS_DENIED.equals(error)
                || "unauthorized_client".equals(error) || INVALID_CLIENT.equals(error);
    }
}
