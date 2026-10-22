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
package org.openhab.binding.mybmw.internal.data;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.unit.Units;

/**
 * Maps CarData technical descriptors to channels of the vehicle thing.
 * <p>
 * Each constant ties one descriptor to one channel in a channel group, together with the kind of state it produces.
 * Quantity channels carry the unit to use when BMW sends a value without a unit, or with a unit that does not belong to
 * the channel's dimension. Descriptor names, types and units follow BMW's telematic data catalogue.
 * <p>
 * Latitude, longitude and altitude feed the single location channel, which the vehicle handler assembles; they are
 * listed here so that both the stream and the REST container cover them.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public enum TelematicChannel {

    // status
    MILEAGE("vehicle.vehicle.travelledDistance", "status", "mileage", Kind.QUANTITY, CarDataUnits.KILOMETRE),
    DOOR_LOCK("vehicle.cabin.door.status", "status", "lock", Kind.LOCK_STATE),
    MOVING("vehicle.isMoving", "status", "moving", Kind.SWITCH),
    IGNITION("vehicle.drivetrain.engine.isIgnitionOn", "status", "ignition", Kind.SWITCH),
    ALARM_ARMED("vehicle.vehicle.antiTheftAlarmSystem.alarm.armStatus", "status", "alarm-arm-state", Kind.STRING),
    ALARM_ACTIVE("vehicle.vehicle.antiTheftAlarmSystem.alarm.isOn", "status", "alarm-active", Kind.SWITCH),
    DEEP_SLEEP("vehicle.vehicle.deepSleepModeActive", "status", "deep-sleep", Kind.SWITCH),
    BATTERY_12V_LEVEL("vehicle.electricalSystem.battery.stateOfCharge", "status", "battery-12v-level", Kind.QUANTITY,
            Units.PERCENT),
    BATTERY_12V_VOLTAGE("vehicle.electricalSystem.battery.voltage", "status", "battery-12v-voltage", Kind.QUANTITY,
            Units.VOLT),
    SERVICE_DISTANCE("vehicle.status.serviceDistance.next", "status", "service-distance", Kind.QUANTITY,
            CarDataUnits.KILOMETRE),
    INSPECTION_DATE("vehicle.status.serviceTime.inspectionDateLegal", "status", "inspection-date", Kind.DATE_TIME),

    // range
    FUEL_LEVEL("vehicle.drivetrain.fuelSystem.level", "range", "fuel-level", Kind.QUANTITY, Units.PERCENT),
    FUEL_REMAINING("vehicle.drivetrain.fuelSystem.remainingFuel", "range", "fuel-remaining", Kind.QUANTITY,
            Units.LITRE),
    TOTAL_RANGE("vehicle.drivetrain.lastRemainingRange", "range", "total-range", Kind.QUANTITY, CarDataUnits.KILOMETRE),
    ELECTRIC_RANGE("vehicle.drivetrain.electricEngine.kombiRemainingElectricRange", "range", "electric-range",
            Kind.QUANTITY, CarDataUnits.KILOMETRE),

    // charge
    SOC("vehicle.drivetrain.batteryManagement.header", "charge", "soc", Kind.QUANTITY, Units.PERCENT),
    TARGET_SOC("vehicle.powertrain.electric.battery.stateOfCharge.target", "charge", "target-soc", Kind.QUANTITY,
            Units.PERCENT),
    CHARGING_STATUS("vehicle.drivetrain.electricEngine.charging.status", "charge", "status", Kind.STRING),
    PLUG_STATUS("vehicle.body.chargingPort.status", "charge", "plug", Kind.STRING),
    CHARGING_POWER("vehicle.powertrain.electric.battery.charging.power", "charge", "power", Kind.QUANTITY, Units.WATT),
    TIME_TO_FULL("vehicle.drivetrain.electricEngine.charging.timeToFullyCharged", "charge", "remaining-time",
            Kind.QUANTITY, Units.MINUTE),
    ENERGY_TO_FULL("vehicle.drivetrain.electricEngine.charging.smeEnergyDeltaFullyCharged", "charge", "energy-to-full",
            Kind.QUANTITY, Units.KILOWATT_HOUR),
    AC_CURRENT("vehicle.drivetrain.electricEngine.charging.acAmpere", "charge", "ac-current", Kind.QUANTITY,
            Units.AMPERE),
    AC_VOLTAGE("vehicle.drivetrain.electricEngine.charging.acVoltage", "charge", "ac-voltage", Kind.QUANTITY,
            Units.VOLT),
    PHASES("vehicle.drivetrain.electricEngine.charging.phaseNumber", "charge", "phases", Kind.STRING),
    CURRENT_LIMIT("vehicle.powertrain.electric.battery.charging.acLimit.selected", "charge", "current-limit",
            Kind.QUANTITY, Units.AMPERE),
    BATTERY_CAPACITY("vehicle.drivetrain.batteryManagement.batterySizeMax", "charge", "battery-capacity", Kind.QUANTITY,
            Units.KILOWATT_HOUR),

    // location
    LATITUDE("vehicle.cabin.infotainment.navigation.currentLocation.latitude", "location", "position", Kind.LATITUDE),
    LONGITUDE("vehicle.cabin.infotainment.navigation.currentLocation.longitude", "location", "position",
            Kind.LONGITUDE),
    ALTITUDE("vehicle.cabin.infotainment.navigation.currentLocation.altitude", "location", "position", Kind.ALTITUDE),
    HEADING("vehicle.cabin.infotainment.navigation.currentLocation.heading", "location", "heading", Kind.QUANTITY,
            Units.DEGREE_ANGLE),

    // doors
    DOOR_DRIVER_FRONT("vehicle.cabin.door.row1.driver.isOpen", "doors", "driver-front", Kind.CONTACT),
    DOOR_PASSENGER_FRONT("vehicle.cabin.door.row1.passenger.isOpen", "doors", "passenger-front", Kind.CONTACT),
    DOOR_DRIVER_REAR("vehicle.cabin.door.row2.driver.isOpen", "doors", "driver-rear", Kind.CONTACT),
    DOOR_PASSENGER_REAR("vehicle.cabin.door.row2.passenger.isOpen", "doors", "passenger-rear", Kind.CONTACT),
    HOOD("vehicle.body.hood.isOpen", "doors", "hood", Kind.CONTACT),
    TRUNK("vehicle.body.trunk.isOpen", "doors", "trunk", Kind.CONTACT),

    // windows
    WINDOW_DRIVER_FRONT("vehicle.cabin.window.row1.driver.status", "windows", "driver-front", Kind.OPENING),
    WINDOW_PASSENGER_FRONT("vehicle.cabin.window.row1.passenger.status", "windows", "passenger-front", Kind.OPENING),
    WINDOW_DRIVER_REAR("vehicle.cabin.window.row2.driver.status", "windows", "driver-rear", Kind.OPENING),
    WINDOW_PASSENGER_REAR("vehicle.cabin.window.row2.passenger.status", "windows", "passenger-rear", Kind.OPENING),
    SUNROOF("vehicle.cabin.sunroof.status", "windows", "sunroof", Kind.OPENING),

    // tires
    TIRE_FRONT_LEFT("vehicle.chassis.axle.row1.wheel.left.tire.pressure", "tires", "front-left", Kind.QUANTITY,
            CarDataUnits.KILOPASCAL),
    TIRE_FRONT_LEFT_TARGET("vehicle.chassis.axle.row1.wheel.left.tire.pressureTarget", "tires", "front-left-target",
            Kind.QUANTITY, CarDataUnits.KILOPASCAL),
    TIRE_FRONT_RIGHT("vehicle.chassis.axle.row1.wheel.right.tire.pressure", "tires", "front-right", Kind.QUANTITY,
            CarDataUnits.KILOPASCAL),
    TIRE_FRONT_RIGHT_TARGET("vehicle.chassis.axle.row1.wheel.right.tire.pressureTarget", "tires", "front-right-target",
            Kind.QUANTITY, CarDataUnits.KILOPASCAL),
    TIRE_REAR_LEFT("vehicle.chassis.axle.row2.wheel.left.tire.pressure", "tires", "rear-left", Kind.QUANTITY,
            CarDataUnits.KILOPASCAL),
    TIRE_REAR_LEFT_TARGET("vehicle.chassis.axle.row2.wheel.left.tire.pressureTarget", "tires", "rear-left-target",
            Kind.QUANTITY, CarDataUnits.KILOPASCAL),
    TIRE_REAR_RIGHT("vehicle.chassis.axle.row2.wheel.right.tire.pressure", "tires", "rear-right", Kind.QUANTITY,
            CarDataUnits.KILOPASCAL),
    TIRE_REAR_RIGHT_TARGET("vehicle.chassis.axle.row2.wheel.right.tire.pressureTarget", "tires", "rear-right-target",
            Kind.QUANTITY, CarDataUnits.KILOPASCAL),

    // climate
    PRECONDITIONING("vehicle.vehicle.preConditioning.activity", "climate", "activity", Kind.STRING),
    PRECONDITIONING_REMAINING("vehicle.vehicle.preConditioning.remainingTime", "climate", "remaining-time",
            Kind.QUANTITY, Units.MINUTE);

    /**
     * How a telematic value is turned into an openHAB state.
     */
    public enum Kind {
        QUANTITY,
        SWITCH,
        CONTACT,
        // An opening that can be partly open (CLOSED, INTERMEDIATE, OPEN): a contact that is
        // open when partly open, plus a detail channel with the reported state
        OPENING,
        STRING,
        DATE_TIME,
        // Overall door lock state, a lock word or a structured object
        LOCK_STATE,
        // Part of the composite location channel
        LATITUDE,
        LONGITUDE,
        ALTITUDE
    }

    private static final Map<String, TelematicChannel> BY_DESCRIPTOR = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TelematicChannel::getDescriptor, Function.identity()));

    private final String descriptor;
    private final String group;
    private final String channel;
    private final Kind kind;
    private final @Nullable Unit<?> defaultUnit;

    TelematicChannel(String descriptor, String group, String channel, Kind kind) {
        this(descriptor, group, channel, kind, null);
    }

    TelematicChannel(String descriptor, String group, String channel, Kind kind, @Nullable Unit<?> defaultUnit) {
        this.descriptor = descriptor;
        this.group = group;
        this.channel = channel;
        this.kind = kind;
        this.defaultUnit = defaultUnit;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public String getGroup() {
        return group;
    }

    public String getChannel() {
        return channel;
    }

    /**
     * @return the channel id within the thing, {@code group#channel}
     */
    public String getChannelId() {
        return group + "#" + channel;
    }

    /**
     * @return the id of the channel with the detailed state, {@code group#channel-state}, for openings; {@code null}
     *         for all other kinds
     */
    public @Nullable String getDetailChannelId() {
        return kind == Kind.OPENING ? getChannelId() + "-state" : null;
    }

    public Kind getKind() {
        return kind;
    }

    public @Nullable Unit<?> getDefaultUnit() {
        return defaultUnit;
    }

    /**
     * @param descriptor technical descriptor
     * @return the mapping for this descriptor, or {@code null} when the binding does not map it to a channel
     */
    public static @Nullable TelematicChannel fromDescriptor(String descriptor) {
        return BY_DESCRIPTOR.get(descriptor);
    }

    /**
     * @return all mapped descriptors, the set the binding requests in its REST telematic data container
     */
    public static List<String> descriptors() {
        return Arrays.stream(values()).map(TelematicChannel::getDescriptor).sorted().toList();
    }
}
