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

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.upnpcontrol.internal.handler.UpnpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpService} represents a UPnP service.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public abstract class UpnpService {

    private final Logger logger = LoggerFactory.getLogger(UpnpService.class);

    protected UpnpHandler handler;
    protected ScheduledExecutorService upnpScheduler;

    public UpnpService(UpnpHandler handler, ScheduledExecutorService upnpScheduler) {
        this.handler = handler;
        this.upnpScheduler = upnpScheduler;
    }

    protected void invokeAction(String serviceId, String actionId, Map<@Nullable String, @Nullable String> inputs) {
        handler.invokeAction(serviceId, actionId, inputs);
    }

    public abstract void onValueReceived(String variable, String value);
}
