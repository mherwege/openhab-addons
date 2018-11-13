/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.handler;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NikoHomeControlCommunication2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NikoHomeControlBridgeHandler2} is the handler for a Niko Home Control II Connected Controller and connects it
 * to the framework.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlBridgeHandler2 extends NikoHomeControlBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlBridgeHandler2.class);

    public NikoHomeControlBridgeHandler2(Bridge nikoHomeControlBridge) {
        super(nikoHomeControlBridge);
    }

    @Override
    public void initialize() {
        logger.debug("Niko Home Control: initializing NHC II bridge handler");

        String profile = getProfile();
        logger.debug("Niko Home Control: touch profile {}", profile);
        if (profile.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Niko Home Control: profile name not set.");
            return;
        }

        String password = getPassword();
        if (password.isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Niko Home Control: password for profile cannot be empty.");
            return;
        }

        try {
            nhcComm = new NikoHomeControlCommunication2(this);
            startCommunication(nhcComm);
        } catch (CertificateException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    // this should not happen unless there is a programming error
                    "Niko Home Control: not able to set SSL context");
            return;
        }
    }

    @Override
    protected void updateProperties() {
        Map<String, String> properties = new HashMap<>();

        NikoHomeControlCommunication2 comm = (NikoHomeControlCommunication2) nhcComm;
        if (comm != null) {
            properties.put("nhcVersion", comm.getSystemInfo().getNhcVersion());
            properties.put("cocoImage", comm.getSystemInfo().getCocoImage());
            properties.put("language", comm.getSystemInfo().getLanguage());
            properties.put("currency", comm.getSystemInfo().getCurrency());
            properties.put("units", comm.getSystemInfo().getUnits());
            properties.put("lastConfig", comm.getSystemInfo().getLastConfig());
            properties.put("electricityTariff", comm.getSystemInfo().getElectricityTariff());
            properties.put("gasTariff", comm.getSystemInfo().getGasTariff());
            properties.put("waterTariff", comm.getSystemInfo().getWaterTariff());
            properties.put("timeZone", comm.getTimeInfo().getTimeZone());
            properties.put("isDST", comm.getTimeInfo().getIsDst());
            properties.put("services", comm.getServices());

            thing.setProperties(properties);
        }
    }

    @Override
    public String getProfile() {
        Configuration config = this.getConfig();
        String profile = (String) config.get(CONFIG_PROFILE);
        if (profile == null) {
            return "";
        }
        return profile;
    }

    @Override
    public String getPassword() {
        Configuration config = this.getConfig();
        String password = (String) config.get(CONFIG_PASSWORD);
        if ((password == null) || password.isEmpty()) {
            logger.debug("Niko Home Control: no password set.");
            return "";
        }
        return password;
    }

    @Override
    public @Nullable InetAddress getAddr() {
        Configuration config = this.getConfig();
        InetAddress addr = null;
        try {
            addr = InetAddress.getByName((String) config.get(CONFIG_HOST_NAME));
        } catch (UnknownHostException e) {
            logger.debug("Niko Home Control: Cannot resolve hostname {} to IP adress", config.get(CONFIG_HOST_NAME));
        }
        return addr;
    }

    @Override
    public @Nullable Integer getPort() {
        Configuration config = this.getConfig();
        return ((Number) config.get(CONFIG_PORT)).intValue();
    }
}
