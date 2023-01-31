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
package org.openhab.binding.mybmw.internal.dto.charge;

import java.util.List;

/**
 * The {@link ChargingProfile} Data Transfer Object
 *
 * @author Bernd Weymann - Initial contribution
 * @author Norbert Truchsess - edit & send of charge profile
 * @author Martin Grassl - refactored to Java Bean
 */
public class ChargingProfile {
    private ChargingWindow reductionOfChargeCurrent;
    private String chargingMode;// ": "immediateCharging",
    private String chargingPreference;// ": "chargingWindow",
    private String chargingControlType;// ": "weeklyPlanner",
    private List<Timer> departureTimes;
    private boolean climatisationOn;// ": false,
    private ChargingSettings chargingSettings;

    public Timer getTimerId(int id) {
        if (departureTimes != null) {
            for (Timer t : departureTimes) {
                if (t.id == id) {
                    return t;
                }
            }
        }
        return new Timer();
    }

    public ChargingWindow getReductionOfChargeCurrent() {
        return reductionOfChargeCurrent;
    }

    public String getChargingMode() {
        return chargingMode;
    }

    public String getChargingPreference() {
        return chargingPreference;
    }

    public String getChargingControlType() {
        return chargingControlType;
    }

    public List<Timer> getDepartureTimes() {
        return departureTimes;
    }

    public boolean isClimatisationOn() {
        return climatisationOn;
    }

    public ChargingSettings getChargingSettings() {
        return chargingSettings;
    }

    @Override
    public String toString() {
        return "ChargingProfile [reductionOfChargeCurrent=" + reductionOfChargeCurrent + ", chargingMode="
                + chargingMode + ", chargingPreference=" + chargingPreference + ", chargingControlType="
                + chargingControlType + ", departureTimes=" + departureTimes + ", climatisationOn=" + climatisationOn
                + ", chargingSettings=" + chargingSettings + "]";
    }
}
