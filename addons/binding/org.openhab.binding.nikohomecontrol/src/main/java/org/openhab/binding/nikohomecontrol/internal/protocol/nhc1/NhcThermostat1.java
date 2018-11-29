/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc1;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcThermostat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcThermostat1} class represents the thermostat Niko Home Control I communication object. It contains all
 * fields representing a Niko Home Control thermostat and has methods to set the thermostat in Niko Home Control and
 * receive thermostat updates.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcThermostat1 extends NhcThermostat {

    private final Logger logger = LoggerFactory.getLogger(NhcThermostat1.class);

    NhcThermostat1(String id, String name, @Nullable String location) {
        super(id, name, location);
    }

    /**
     * Sends thermostat mode to Niko Home Control.
     *
     * @param mode
     */
    @Override
    public void executeMode(int mode) {
        logger.debug("Niko Home Control: execute thermostat mode {} for {}", mode, this.id);

        if (nhcComm != null) {
            nhcComm.executeThermostat(this.id, mode);
        }
    }

    /**
     * Sends thermostat setpoint to Niko Home Control.
     *
     * @param overrule temperature to overrule the setpoint in 0.1°C multiples
     * @param time     time duration in min for overrule
     */
    @Override
    public void executeOverrule(int overrule, int overruletime) {
        logger.debug("Niko Home Control: execute thermostat overrule {} during {} min for {}", overrule, overruletime,
                this.id);

        if (nhcComm != null) {
            nhcComm.executeThermostat(this.id, overrule, overruletime);
        }
    }
}
