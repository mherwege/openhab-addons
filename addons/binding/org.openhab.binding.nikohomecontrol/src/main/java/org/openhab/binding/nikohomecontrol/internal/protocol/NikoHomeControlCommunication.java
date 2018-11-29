/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc1.NhcLocation1;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc1.NhcSystemInfo1;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc1.NikoHomeControlCommunication1;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NikoHomeControlCommunication2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NikoHomeControlCommunication} class is an abstract class representing the communication objects with the
 * Niko Home Control System. {@link NikoHomeControlCommunication1} or {@link NikoHomeControlCommunication2} should be
 * used for the respective
 * version of Niko Home Control.
 * <ul>
 * <li>Start and stop communication with the Niko Home Control System.
 * <li>Read all setup and status information from the Niko Home Control Controller.
 * <li>Execute Niko Home Control commands.
 * <li>Listen to events from Niko Home Control.
 * </ul>
 *
 * Only switch, dimmer and rollershutter actions are currently implemented.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public abstract class NikoHomeControlCommunication {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlCommunication.class);

    protected final NhcSystemInfo1 systemInfo = new NhcSystemInfo1();
    protected final Map<String, NhcLocation1> locations = new HashMap<>();
    protected final Map<String, NhcAction> actions = new HashMap<>();
    protected final Map<String, NhcThermostat> thermostats = new HashMap<>();

    // handler representing the callback interface {@link NhcControllerEvent} for configuration parameters and events
    @NonNullByDefault({}) // this handler must be set in the derived classes constructors, therefore will not be null,
                          // but IDE gives error
    protected NhcControllerEvent handler;

    /**
     * Start Communication with Niko Home Control system.
     */
    public abstract void startCommunication();

    /**
     * Stop Communication with Niko Home Control system.
     */
    public abstract void stopCommunication();

    /**
     * Close and restart communication with Niko Home Control system.
     */
    public synchronized void restartCommunication() {
        stopCommunication();

        logger.debug("Niko Home Control: restart communication from thread {}", Thread.currentThread().getId());

        startCommunication();
    }

    /**
     * Method to check if communication with Niko Home Control is active.
     *
     * @return True if active
     */
    public abstract boolean communicationActive();

    /**
     * Return all actions in the Niko Home Control Controller.
     *
     * @return <code>Map&ltInteger, {@link NhcAction}></code>
     */
    public Map<String, NhcAction> getActions() {
        return this.actions;
    }

    /**
     * Return all thermostats in the Niko Home Control Controller.
     *
     * @return <code>Map&ltInteger, {@link NhcThermostat}></code>
     */
    public Map<String, NhcThermostat> getThermostats() {
        return this.thermostats;
    }

    /**
     * Execute an action command by sending it to Niko Home Control.
     *
     * @param actionId
     * @param value
     */
    public abstract void executeAction(String actionId, String value);

    /**
     * Execute a thermostat command by sending it to Niko Home Control.
     *
     * @param thermostatId
     * @param mode
     */
    public abstract void executeThermostat(String thermostatId, int mode);

    /**
     * Execute a thermostat command by sending it to Niko Home Control.
     *
     * @param thermostatId
     * @param overruleTemp
     * @param overruleTime
     */
    public abstract void executeThermostat(String thermostatId, int overruleTemp, int overruleTime);
}
