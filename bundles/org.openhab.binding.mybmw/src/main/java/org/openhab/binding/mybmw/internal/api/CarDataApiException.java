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
package org.openhab.binding.mybmw.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An error response from the CarData REST API, or a request the binding did not send because it would exceed the
 * daily request budget.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class CarDataApiException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Daily rate limit reached on BMW's side */
    public static final String RATE_LIMIT = "CU-429";
    /** No permission for the container id, e.g. because it was deleted */
    public static final String UNKNOWN_CONTAINER = "CU-105";
    /** Maximum number of containers reached */
    public static final String TOO_MANY_CONTAINERS = "CU-124";

    private final int httpStatus;
    private final @Nullable String errorId;
    private final boolean budgetExhausted;

    public CarDataApiException(int httpStatus, @Nullable String errorId, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorId = errorId;
        this.budgetExhausted = false;
    }

    private CarDataApiException(String message) {
        super(message);
        this.httpStatus = 0;
        this.errorId = null;
        this.budgetExhausted = true;
    }

    /**
     * @return an exception for a request the binding did not send because its own request budget is used up
     */
    static CarDataApiException budgetExhausted(int used, int budget) {
        return new CarDataApiException("Request budget used up: " + used + " of " + budget + " in the last 24 hours");
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return BMW's error id, e.g. {@code CU-429}, when the response carried one
     */
    public @Nullable String getErrorId() {
        return errorId;
    }

    /**
     * @return whether no more requests can be sent today, either by BMW's limit or by the binding's own budget
     */
    public boolean isRateLimited() {
        return budgetExhausted || RATE_LIMIT.equals(errorId) || httpStatus == 429;
    }

    /**
     * @return whether the binding withheld the request to stay within its budget
     */
    public boolean isBudgetExhausted() {
        return budgetExhausted;
    }

    public boolean isUnknownContainer() {
        return UNKNOWN_CONTAINER.equals(errorId);
    }

    public boolean isUnauthorized() {
        return httpStatus == 401 || "CU-101".equals(errorId) || "CU-102".equals(errorId) || "CU-103".equals(errorId);
    }
}
