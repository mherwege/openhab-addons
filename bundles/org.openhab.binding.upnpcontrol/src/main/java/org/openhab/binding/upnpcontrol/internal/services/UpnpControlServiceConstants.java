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

import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link UpnpControlServiceConstants} class defines common constants, which are
 * used for UPnP communication.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpControlServiceConstants {

    public static final String CONNECTION_MANAGER = "ConnectionManager";
    public static final String AV_TRANSPORT = "AVTransport";
    public static final String RENDERING_CONTROL = "RenderingControl";
    public static final String CONTENT_DIRECTORY = "ContentDirectory";

    public static final String INSTANCE_ID = "InstanceID";
    public static final String CONNECTION_ID = "ConnectionID";
    public static final String AV_TRANSPORT_ID = "AVTransportID";
    public static final String RCS_ID = "RcsID";

    public static final Pattern PROTOCOL_PATTERN = Pattern.compile("(?:.*):(?:.*):(.*):(?:.*)");
}
