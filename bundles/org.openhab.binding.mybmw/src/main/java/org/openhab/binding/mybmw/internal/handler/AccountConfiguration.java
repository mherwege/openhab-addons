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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Configuration of the CarData account bridge.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class AccountConfiguration {

    /** CarData client ID generated in the MyBMW / CarData customer portal. */
    public String clientId = "";

    /** Local MQTT broker the bmw-mqtt-bridge republishes the CarData stream to. */
    public String brokerHost = "";
    public int brokerPort = 1883;
    public boolean brokerSecure = false;
    public String brokerUsername = "";
    public String brokerPassword = "";

    /** Topic prefix configured as LOCAL_PREFIX in the bridge. */
    public String topicPrefix = "bmw/";

    /** Hours between REST telematic data refreshes, 0 disables the periodic refresh. */
    public int refreshInterval = 6;

    /**
     * Maximum number of REST requests the binding spends per rolling 24 hours, limited to
     * {@link org.openhab.binding.mybmw.internal.api.RequestBudget#BMW_DAILY_LIMIT}.
     */
    public int requestBudget = 40;

    /**
     * @return the topic prefix with exactly one trailing slash, falling back to the bridge default when empty
     */
    public String normalizedTopicPrefix() {
        String prefix = topicPrefix.strip();
        if (prefix.isEmpty()) {
            return "bmw/";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }
}
