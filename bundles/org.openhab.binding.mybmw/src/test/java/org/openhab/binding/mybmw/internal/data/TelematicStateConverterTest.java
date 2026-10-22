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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * Tests for {@link TelematicStateConverter}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class TelematicStateConverterTest {

    private static State convert(TelematicChannel mapping, String json, @Nullable String unit) {
        JsonElement value = JsonParser.parseString(json);
        return TelematicStateConverter.toState(mapping,
                new TelematicValue(mapping.getDescriptor(), value, unit, Instant.EPOCH));
    }

    @Test
    void quantityUsesUnitFromMessage() {
        State state = convert(TelematicChannel.MILEAGE, "12345", "km");
        assertEquals(new QuantityType<>(12345, CarDataUnits.KILOMETRE), state);

        state = convert(TelematicChannel.MILEAGE, "100", "mi");
        assertEquals(new QuantityType<>(100, ImperialUnits.MILE), state);
    }

    @Test
    void quantityAcceptsUnitVariants() {
        // BMW sends kPa, kpa or bar for tire pressure depending on the car
        assertEquals(new QuantityType<>(250, CarDataUnits.KILOPASCAL),
                convert(TelematicChannel.TIRE_FRONT_LEFT, "250", "kpa"));
        assertEquals(new QuantityType<>(2.5, Units.BAR), convert(TelematicChannel.TIRE_FRONT_LEFT, "2.5", "bar"));
        assertEquals(new QuantityType<>(42, Units.LITRE), convert(TelematicChannel.FUEL_REMAINING, "42", "l"));
    }

    @Test
    void quantityFallsBackToDefaultUnit() {
        // REST sends numbers as strings, and units are sometimes missing
        assertEquals(new QuantityType<>(80, Units.PERCENT), convert(TelematicChannel.TARGET_SOC, "\"80\"", null));
        // an incompatible unit is ignored rather than producing a quantity of the wrong dimension
        assertEquals(new QuantityType<>(7400, Units.WATT), convert(TelematicChannel.CHARGING_POWER, "7400", "km"));
    }

    @Test
    void invalidMarkersBecomeUndef() {
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.FUEL_LEVEL, "\"INVALID\"", "%"));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.AC_CURRENT, "\"-NA-\"", "A"));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.WINDOW_DRIVER_FRONT, "\"INVALID\"", null));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.MILEAGE, "null", "km"));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.MILEAGE, "\"far\"", "km"));
    }

    @Test
    void switchAcceptsBooleanVariants() {
        assertEquals(OnOffType.ON, convert(TelematicChannel.MOVING, "true", null));
        assertEquals(OnOffType.OFF, convert(TelematicChannel.MOVING, "\"false\"", null));
        assertEquals(OnOffType.ON, convert(TelematicChannel.IGNITION, "\"ASN_isTrue\"", null));
        assertEquals(OnOffType.OFF, convert(TelematicChannel.IGNITION, "\"ASN_isFalse\"", null));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.IGNITION, "\"ASN_isUnknown\"", null));
    }

    @Test
    void windowIsOpenWhenPartlyOpen() {
        assertEquals(OpenClosedType.OPEN, convert(TelematicChannel.WINDOW_DRIVER_FRONT, "\"INTERMEDIATE\"", null));
        assertEquals(OpenClosedType.OPEN, convert(TelematicChannel.SUNROOF, "\"OPEN\"", null));
        assertEquals(OpenClosedType.CLOSED, convert(TelematicChannel.WINDOW_DRIVER_REAR, "\"CLOSED\"", null));

        TelematicValue partly = new TelematicValue(TelematicChannel.SUNROOF.getDescriptor(),
                JsonParser.parseString("\"intermediate\""), null, Instant.EPOCH);
        assertEquals(new StringType("INTERMEDIATE"), TelematicStateConverter.toDetailState(partly));
        TelematicValue invalid = new TelematicValue(TelematicChannel.SUNROOF.getDescriptor(),
                JsonParser.parseString("\"INVALID\""), null, Instant.EPOCH);
        assertEquals(UnDefType.UNDEF, TelematicStateConverter.toDetailState(invalid));
    }

    @Test
    void contactAcceptsOpeningWords() {
        assertEquals(OpenClosedType.OPEN, convert(TelematicChannel.DOOR_DRIVER_FRONT, "\"OPEN\"", null));
        assertEquals(OpenClosedType.CLOSED, convert(TelematicChannel.DOOR_DRIVER_FRONT, "\"CLOSED\"", null));
        assertEquals(OpenClosedType.OPEN, convert(TelematicChannel.TRUNK, "true", null));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.HOOD, "\"UNKNOWN\"", null));
    }

    @Test
    void lockStateFromWordOrObject() {
        assertEquals(new StringType("SELECTIVELOCKED"),
                convert(TelematicChannel.DOOR_LOCK, "\"ASN_selective-Locked\"", null));
        assertEquals(new StringType("SECURED"), convert(TelematicChannel.DOOR_LOCK, "\"SECURED\"", null));
        assertEquals(new StringType("LOCKED"), convert(TelematicChannel.DOOR_LOCK,
                "{\"newDoorStatus\":\"ASN_unlocked\",\"allDoorsLocked\":\"ASN_isTrue\"}", null));
        assertEquals(new StringType("UNLOCKED"), convert(TelematicChannel.DOOR_LOCK,
                "{\"newDoorStatus\":\"ASN_unlocked\",\"allDoorsLocked\":\"ASN_isFalse\"}", null));
        assertEquals(new StringType("UNLOCKED"),
                convert(TelematicChannel.DOOR_LOCK, "{\"allDoorsLocked\":\"ASN_isFalse\"}", null));
    }

    @Test
    void stringKeepsValue() {
        assertEquals(new StringType("CHARGINGACTIVE"),
                convert(TelematicChannel.CHARGING_STATUS, "\"CHARGINGACTIVE\"", null));
        assertEquals(new StringType("doorsOnly"), convert(TelematicChannel.ALARM_ARMED, "\"doorsOnly\"", null));
    }

    @Test
    void inspectionDateFormats() {
        DateTimeType expected = new DateTimeType(Instant.parse("2027-03-01T00:00:00Z"));
        assertEquals(expected, convert(TelematicChannel.INSPECTION_DATE, "\"01.03.2027 00:00 UTC\"", null));
        assertEquals(expected, convert(TelematicChannel.INSPECTION_DATE, "\"03/01/2027 00:00 UTC\"", null));
        assertEquals(expected, convert(TelematicChannel.INSPECTION_DATE, "\"2027-03-01T00:00:00Z\"", null));
        assertEquals(expected, convert(TelematicChannel.INSPECTION_DATE, "\"2027-03-01\"", null));
        assertEquals(UnDefType.UNDEF, convert(TelematicChannel.INSPECTION_DATE, "\"next spring\"", null));
    }
}
