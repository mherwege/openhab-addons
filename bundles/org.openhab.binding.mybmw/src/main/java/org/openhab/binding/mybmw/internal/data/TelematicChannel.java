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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.unit.SIUnits;
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
    LIGHTS("vehicle.body.lights.isRunningOn", "status", "lights", Kind.SWITCH),
    MOVING("vehicle.isMoving", "status", "moving", Kind.SWITCH),
    // despite their names, BMW's catalogue describes isActive as the ignition and isIgnitionOn as the engine
    IGNITION("vehicle.drivetrain.engine.isActive", "status", "ignition", Kind.SWITCH),
    ENGINE("vehicle.drivetrain.engine.isIgnitionOn", "status", "engine", Kind.SWITCH),
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
    TARGET_RANGE("vehicle.powertrain.electric.range.target", "range", "target-range", Kind.QUANTITY,
            CarDataUnits.KILOMETRE),
    // lifetime totals rather than BMW's averages in l/100 km, which openHAB has no unit for; these convert to imperial
    TOTAL_FUEL("vehicle.drivetrain.fuelSystem.consumptionOverLifeTime.overall.fuel", "range", "total-fuel",
            Kind.QUANTITY, Units.LITRE),
    TOTAL_FUEL_DISTANCE("vehicle.drivetrain.fuelSystem.consumptionOverLifeTime.overall.referenceDistance", "range",
            "total-fuel-distance", Kind.QUANTITY, CarDataUnits.KILOMETRE),

    // charge
    SOC("vehicle.drivetrain.batteryManagement.header", "charge", "soc", Kind.QUANTITY, Units.PERCENT),
    SOC_DISPLAYED("vehicle.powertrain.electric.battery.stateOfCharge.displayed", "charge", "soc-displayed",
            Kind.QUANTITY, Units.PERCENT),
    TARGET_SOC("vehicle.powertrain.electric.battery.stateOfCharge.target", "charge", "target-soc", Kind.QUANTITY,
            Units.PERCENT),
    CHARGING_STATUS("vehicle.drivetrain.electricEngine.charging.status", "charge", "status", Kind.STRING),
    PLUG_STATUS("vehicle.body.chargingPort.status", "charge", "plug", Kind.STRING),
    DC_PLUG_STATUS("vehicle.body.chargingPort.dcStatus", "charge", "dc-plug", Kind.STRING),
    CHARGING_POWER("vehicle.powertrain.electric.battery.charging.power", "charge", "power", Kind.QUANTITY, Units.WATT),
    TIME_TO_FULL("vehicle.drivetrain.electricEngine.charging.timeToFullyCharged", "charge", "remaining-time",
            Kind.QUANTITY, Units.MINUTE),
    CHARGING_TIME_REMAINING("vehicle.drivetrain.electricEngine.charging.timeRemaining", "charge",
            "charging-time-remaining", Kind.QUANTITY, Units.MINUTE),
    // lifetime totals rather than BMW's averages in kWh/100 km, which openHAB has no unit for
    TOTAL_CHARGED_ENERGY("vehicle.drivetrain.electricEngine.charging.consumptionOverLifeTime.overall.gridEnergy",
            "charge", "total-charged-energy", Kind.QUANTITY, Units.KILOWATT_HOUR),
    TOTAL_CHARGED_DISTANCE(
            "vehicle.drivetrain.electricEngine.charging.consumptionOverLifeTime.overall.referenceDistance", "charge",
            "total-charged-distance", Kind.QUANTITY, CarDataUnits.KILOMETRE),
    ENERGY_TO_FULL("vehicle.drivetrain.electricEngine.charging.smeEnergyDeltaFullyCharged", "charge", "energy-to-full",
            Kind.QUANTITY, Units.KILOWATT_HOUR),
    AC_CURRENT("vehicle.drivetrain.electricEngine.charging.acAmpere", "charge", "ac-current", Kind.QUANTITY,
            Units.AMPERE),
    AC_VOLTAGE("vehicle.drivetrain.electricEngine.charging.acVoltage", "charge", "ac-voltage", Kind.QUANTITY,
            Units.VOLT),
    PHASES("vehicle.drivetrain.electricEngine.charging.phaseNumber", "charge", "phases", Kind.STRING),
    CURRENT_LIMIT("vehicle.powertrain.electric.battery.charging.acLimit.selected", "charge", "current-limit",
            Kind.QUANTITY, Units.AMPERE),
    CURRENT_LIMIT_MAX("vehicle.powertrain.electric.battery.charging.acLimit.max", "charge", "current-limit-max",
            Kind.QUANTITY, Units.AMPERE),
    CURRENT_LIMIT_MIN("vehicle.powertrain.electric.battery.charging.acLimit.min", "charge", "current-limit-min",
            Kind.QUANTITY, Units.AMPERE),
    CURRENT_LIMIT_ACTIVE("vehicle.powertrain.electric.battery.charging.acLimit.isActive", "charge",
            "current-limit-active", Kind.SWITCH),
    // batteryManagement.batterySizeMax is sent as 0.0 by at least the iX, maxEnergy holds the capacity
    BATTERY_CAPACITY("vehicle.drivetrain.batteryManagement.maxEnergy", "charge", "battery-capacity", Kind.QUANTITY,
            Units.KILOWATT_HOUR),
    CHARGING_METHOD("vehicle.drivetrain.electricEngine.charging.method", "charge", "method", Kind.STRING),
    CHARGING_MODE("vehicle.drivetrain.electricEngine.charging.chargingMode", "charge", "mode", Kind.STRING),
    CONNECTION_TYPE("vehicle.drivetrain.electricEngine.charging.connectionType", "charge", "connection-type",
            Kind.STRING),
    SMART_CHARGING("vehicle.powertrain.electric.battery.charging.preferenceSmartCharging", "charge", "smart-charging",
            Kind.STRING),
    FLAP_LOCKED("vehicle.body.flap.isLocked", "charge", "flap-locked", Kind.SWITCH),
    PLUG_EVENT("vehicle.body.chargingPort.plugEventId", "charge", "plug-event", Kind.NUMBER),

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
    TRUNK_LOCKED("vehicle.body.trunk.isLocked", "doors", "trunk-locked", Kind.SWITCH),
    DOOR_DRIVER_FRONT_POSITION("vehicle.cabin.door.row1.driver.position", "doors", "driver-front-position",
            Kind.QUANTITY, Units.PERCENT),
    DOOR_PASSENGER_FRONT_POSITION("vehicle.cabin.door.row1.passenger.position", "doors", "passenger-front-position",
            Kind.QUANTITY, Units.PERCENT),
    DOOR_DRIVER_REAR_POSITION("vehicle.cabin.door.row2.driver.position", "doors", "driver-rear-position", Kind.QUANTITY,
            Units.PERCENT),
    DOOR_PASSENGER_REAR_POSITION("vehicle.cabin.door.row2.passenger.position", "doors", "passenger-rear-position",
            Kind.QUANTITY, Units.PERCENT),

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
    TIRE_FRONT_LEFT_TEMPERATURE("vehicle.chassis.axle.row1.wheel.left.tire.temperature", "tires",
            "front-left-temperature", Kind.QUANTITY, SIUnits.CELSIUS),
    TIRE_FRONT_RIGHT_TEMPERATURE("vehicle.chassis.axle.row1.wheel.right.tire.temperature", "tires",
            "front-right-temperature", Kind.QUANTITY, SIUnits.CELSIUS),
    TIRE_REAR_LEFT_TEMPERATURE("vehicle.chassis.axle.row2.wheel.left.tire.temperature", "tires",
            "rear-left-temperature", Kind.QUANTITY, SIUnits.CELSIUS),
    TIRE_REAR_RIGHT_TEMPERATURE("vehicle.chassis.axle.row2.wheel.right.tire.temperature", "tires",
            "rear-right-temperature", Kind.QUANTITY, SIUnits.CELSIUS),

    // climate
    PRECONDITIONING("vehicle.vehicle.preConditioning.activity", "climate", "activity", Kind.STRING),
    PRECONDITIONING_REMAINING("vehicle.vehicle.preConditioning.remainingTime", "climate", "remaining-time",
            Kind.QUANTITY, Units.MINUTE),
    PRECONDITIONING_ERROR("vehicle.vehicle.preConditioning.error", "climate", "error", Kind.STRING),
    REMOTE_ENGINE_RUNNING("vehicle.vehicle.preConditioning.isRemoteEngineRunning", "climate", "remote-engine-running",
            Kind.SWITCH),

    // last trip
    TRIP_END_TIME("vehicle.trip.segment.end.time", "trip", "end-time", Kind.DATE_TIME),
    TRIP_END_MILEAGE("vehicle.trip.segment.end.travelledDistance", "trip", "end-mileage", Kind.QUANTITY,
            CarDataUnits.KILOMETRE),
    TRIP_END_SOC("vehicle.trip.segment.end.drivetrain.batteryManagement.hvSoc", "trip", "end-soc", Kind.QUANTITY,
            Units.PERCENT);

    /**
     * The drive a channel needs to apply to a vehicle.
     */
    public enum Drive {
        ANY,
        // An electric motor with a high-voltage battery that can be charged: electric and plug-in hybrid vehicles
        ELECTRIC,
        // A combustion engine: combustion, mild hybrid and plug-in hybrid vehicles
        COMBUSTION
    }

    private static final Set<TelematicChannel> ELECTRIC_ONLY = EnumSet.of(ELECTRIC_RANGE, TARGET_RANGE, SOC, TARGET_SOC,
            CHARGING_STATUS, PLUG_STATUS, CHARGING_POWER, TIME_TO_FULL, ENERGY_TO_FULL, AC_CURRENT, AC_VOLTAGE, PHASES,
            CURRENT_LIMIT, CURRENT_LIMIT_MAX, CURRENT_LIMIT_MIN, CURRENT_LIMIT_ACTIVE, BATTERY_CAPACITY,
            CHARGING_METHOD, CHARGING_MODE, CONNECTION_TYPE, SMART_CHARGING, FLAP_LOCKED, PLUG_EVENT, SOC_DISPLAYED,
            DC_PLUG_STATUS, CHARGING_TIME_REMAINING, TOTAL_CHARGED_ENERGY, TOTAL_CHARGED_DISTANCE, TRIP_END_SOC);

    // lastRemainingRange is meant for vehicles with a combustion engine, according to BMW's catalogue
    private static final Set<TelematicChannel> COMBUSTION_ONLY = EnumSet.of(FUEL_LEVEL, FUEL_REMAINING, TOTAL_RANGE,
            TOTAL_FUEL, TOTAL_FUEL_DISTANCE, REMOTE_ENGINE_RUNNING);

    /**
     * How a telematic value is turned into an openHAB state.
     */
    public enum Kind {
        QUANTITY,
        // A plain number without unit, such as a counter
        NUMBER,
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
    /**
     * @return the drive the channel needs to apply to a vehicle
     */
    public Drive getDrive() {
        if (ELECTRIC_ONLY.contains(this)) {
            return Drive.ELECTRIC;
        }
        return COMBUSTION_ONLY.contains(this) ? Drive.COMBUSTION : Drive.ANY;
    }

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
