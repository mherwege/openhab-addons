/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc2;

import java.util.List;

/**
 * {@link NhcDevice2} represents a Niko Home Control II device. It is used when parsing the device response json and
 * when creating the state update json to send to the Connected Controller.
 *
 * @author Mark Herwege - Initial Contribution
 */
class NhcDevice2 {
    class NhcProperty {
        String status;
        String brightness;
        String aligned;
        String basicState;
    }

    class NhcTrait {

    }

    class NhcParameter {
        String locationId;
        String locationName;
        String locationIcon;
    }

    String name;
    String uuid;
    String technology;
    String identifier;
    String model;
    String type;
    String online;
    List<NhcProperty> properties;
    List<NhcTrait> traits;
    List<NhcParameter> parameters;
}
