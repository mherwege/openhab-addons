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

import java.util.Locale;
import java.util.Map;

import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.measure.quantity.Pressure;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.MetricPrefix;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;

/**
 * Translates the unit strings BMW sends with telematic values into openHAB units.
 * <p>
 * BMW is not consistent in how it writes units: the same descriptor arrives as {@code kPa}, {@code kpa} or {@code bar}
 * depending on the car, angles as {@code degrees} or {@code °}, volumes as {@code L} or {@code l}. Lookup is therefore
 * case-insensitive over a fixed table rather than through the generic unit parser.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public final class CarDataUnits {

    public static final Unit<Length> KILOMETRE = MetricPrefix.KILO(SIUnits.METRE);
    public static final Unit<Pressure> KILOPASCAL = MetricPrefix.KILO(SIUnits.PASCAL);

    private static final Map<String, Unit<?>> UNITS = Map.ofEntries( //
            Map.entry("km", KILOMETRE), //
            Map.entry("mi", ImperialUnits.MILE), //
            Map.entry("m", SIUnits.METRE), //
            Map.entry("ft", ImperialUnits.FOOT), //
            Map.entry("%", Units.PERCENT), //
            Map.entry("percent", Units.PERCENT), //
            Map.entry("v", Units.VOLT), //
            Map.entry("a", Units.AMPERE), //
            Map.entry("w", Units.WATT), //
            Map.entry("kw", MetricPrefix.KILO(Units.WATT)), //
            Map.entry("kwh", Units.KILOWATT_HOUR), //
            Map.entry("s", Units.SECOND), //
            Map.entry("min", Units.MINUTE), //
            Map.entry("h", Units.HOUR), //
            Map.entry("weeks", Units.WEEK), //
            Map.entry("months", Units.MONTH), //
            Map.entry("kpa", KILOPASCAL), //
            Map.entry("bar", Units.BAR), //
            Map.entry("°c", SIUnits.CELSIUS), //
            Map.entry("°", Units.DEGREE_ANGLE), //
            Map.entry("degrees", Units.DEGREE_ANGLE), //
            Map.entry("l", Units.LITRE), //
            Map.entry("gal", ImperialUnits.GALLON_LIQUID_US), //
            Map.entry("km/h", SIUnits.KILOMETRE_PER_HOUR), //
            Map.entry("mph", ImperialUnits.MILES_PER_HOUR));

    private CarDataUnits() {
        // utility class
    }

    /**
     * @param bmwUnit unit as sent by BMW, may be {@code null} or empty
     * @return the matching openHAB unit, or {@code null} when the unit is absent or unknown
     */
    public static @Nullable Unit<?> parse(@Nullable String bmwUnit) {
        if (bmwUnit == null) {
            return null;
        }
        String key = bmwUnit.strip().toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : UNITS.get(key);
    }
}
