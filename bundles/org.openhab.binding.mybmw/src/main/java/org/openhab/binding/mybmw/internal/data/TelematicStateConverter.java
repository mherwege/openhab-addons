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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Converts telematic values into openHAB states.
 * <p>
 * BMW's values are loosely typed: booleans arrive as JSON booleans, as {@code "true"}, or as ASN words such as
 * {@code "ASN_isTrue"}; openings report {@code OPEN}/{@code INTERMEDIATE}/{@code CLOSED}; the REST endpoint sends
 * numbers as strings. {@code INVALID} and {@code -NA-} mean the car has no valid value. Conversion is therefore
 * tolerant of these variants and maps anything it does not recognize to {@link UnDefType#UNDEF} rather than guessing.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public final class TelematicStateConverter {

    private static final Set<String> INVALID_MARKERS = Set.of("", "INVALID", "-NA-", "NA", "N/A");

    private static final Set<String> TRUE_WORDS = Set.of("TRUE", "ISTRUE", "ON", "YES", "ACTIVE");
    private static final Set<String> FALSE_WORDS = Set.of("FALSE", "ISFALSE", "OFF", "NO", "INACTIVE");
    private static final Set<String> OPEN_WORDS = Set.of("OPEN", "INTERMEDIATE", "TRUE", "ISTRUE");
    private static final Set<String> CLOSED_WORDS = Set.of("CLOSED", "FALSE", "ISFALSE");

    /** Date formats BMW documents for the legal inspection date, besides ISO-8601 instants. */
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC'", Locale.ROOT),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm 'UTC'", Locale.ROOT));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT),
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ROOT), DateTimeFormatter.ISO_LOCAL_DATE);

    private TelematicStateConverter() {
        // utility class
    }

    /**
     * Converts a value for a channel that maps one descriptor to one state. Location parts are not handled here, see
     * {@link #toNumber(JsonElement)}.
     *
     * @param mapping the channel mapping of the value's descriptor
     * @param value the telematic value
     * @return the state, {@link UnDefType#UNDEF} when the value is invalid or cannot be interpreted
     */
    public static State toState(TelematicChannel mapping, TelematicValue value) {
        JsonElement raw = value.value();
        if (isInvalid(raw)) {
            return UnDefType.UNDEF;
        }
        return switch (mapping.getKind()) {
            case QUANTITY -> toQuantity(raw, value.unit(), mapping.getDefaultUnit());
            case SWITCH -> toSwitch(raw);
            case CONTACT, OPENING -> toContact(raw);
            case STRING -> toStringState(raw);
            case DATE_TIME -> toDateTime(raw);
            case LOCK_STATE -> toLockState(raw);
            case LATITUDE, LONGITUDE, ALTITUDE -> UnDefType.UNDEF;
        };
    }

    /**
     * Converts a value for the detail channel of an opening, see {@link TelematicChannel#getDetailChannelId()}.
     *
     * @param value the telematic value
     * @return the reported state, e.g. {@code INTERMEDIATE}, or {@link UnDefType#UNDEF} when invalid
     */
    public static State toDetailState(TelematicValue value) {
        JsonElement raw = value.value();
        if (isInvalid(raw)) {
            return UnDefType.UNDEF;
        }
        String word = word(raw);
        return word != null ? new StringType(word) : UnDefType.UNDEF;
    }

    /**
     * @param raw a value
     * @return the numeric value of a JSON number or numeric string, {@code null} otherwise
     */
    public static @Nullable BigDecimal toNumber(JsonElement raw) {
        if (!raw.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = raw.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsBigDecimal();
        }
        if (primitive.isString()) {
            try {
                return new BigDecimal(primitive.getAsString().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * @param raw a value
     * @return whether the value carries no information: JSON null or one of BMW's invalid markers
     */
    public static boolean isInvalid(JsonElement raw) {
        if (raw.isJsonNull()) {
            return true;
        }
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString()) {
            return INVALID_MARKERS.contains(raw.getAsString().strip().toUpperCase(Locale.ROOT));
        }
        return false;
    }

    /**
     * Normalizes a BMW word for comparison: upper case, without the {@code ASN_} prefix and without punctuation, so
     * that {@code ASN_selective-Locked}, {@code SELECTIVE_LOCKED} and {@code SELECTIVELOCKED} compare equal.
     *
     * @param raw a value
     * @return the normalized word, or {@code null} when the value is not a string
     */
    static @Nullable String word(JsonElement raw) {
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            return null;
        }
        String word = raw.getAsString().strip().toUpperCase(Locale.ROOT);
        if (word.startsWith("ASN_")) {
            word = word.substring(4);
        }
        word = word.replaceAll("[^A-Z0-9]", "");
        return word.isEmpty() ? null : word;
    }

    private static State toQuantity(JsonElement raw, @Nullable String bmwUnit, @Nullable Unit<?> defaultUnit) {
        BigDecimal number = toNumber(raw);
        if (number == null || defaultUnit == null) {
            return UnDefType.UNDEF;
        }
        Unit<?> unit = CarDataUnits.parse(bmwUnit);
        if (unit == null || !unit.isCompatible(defaultUnit)) {
            unit = defaultUnit;
        }
        return new QuantityType<>(number, unit);
    }

    private static State toSwitch(JsonElement raw) {
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) {
            return OnOffType.from(raw.getAsBoolean());
        }
        String word = word(raw);
        if (word != null && TRUE_WORDS.contains(word)) {
            return OnOffType.ON;
        }
        if (word != null && FALSE_WORDS.contains(word)) {
            return OnOffType.OFF;
        }
        return UnDefType.UNDEF;
    }

    private static State toContact(JsonElement raw) {
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) {
            return raw.getAsBoolean() ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
        }
        String word = word(raw);
        if (word != null && OPEN_WORDS.contains(word)) {
            return OpenClosedType.OPEN;
        }
        if (word != null && CLOSED_WORDS.contains(word)) {
            return OpenClosedType.CLOSED;
        }
        return UnDefType.UNDEF;
    }

    private static State toStringState(JsonElement raw) {
        if (raw.isJsonPrimitive()) {
            return new StringType(raw.getAsString().strip());
        }
        return new StringType(raw.toString());
    }

    /**
     * The overall lock state arrives either as a single word or as an object with the sub-fields
     * {@code oldDoorStatus}, {@code newDoorStatus}, {@code allDoorsLocked} and {@code trunkLocked}.
     */
    private static State toLockState(JsonElement raw) {
        String word;
        if (raw.isJsonObject()) {
            JsonObject object = raw.getAsJsonObject();
            JsonElement allLocked = object.get("allDoorsLocked");
            String allLockedWord = allLocked != null ? word(allLocked) : null;
            if ("ISTRUE".equals(allLockedWord)) {
                word = "LOCKED";
            } else {
                JsonElement newStatus = object.get("newDoorStatus");
                word = newStatus != null ? word(newStatus) : null;
                if (word == null && "ISFALSE".equals(allLockedWord)) {
                    word = "UNLOCKED";
                }
            }
        } else {
            word = word(raw);
        }
        return word != null ? new StringType(word) : UnDefType.UNDEF;
    }

    private static State toDateTime(JsonElement raw) {
        if (!raw.isJsonPrimitive()) {
            return UnDefType.UNDEF;
        }
        String text = raw.getAsString().strip();
        try {
            return new DateTimeType(Instant.parse(text));
        } catch (DateTimeParseException e) {
            // try the documented local formats below
        }
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                return new DateTimeType(LocalDateTime.parse(text, format).toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException e) {
                // try next format
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return new DateTimeType(LocalDate.parse(text, format).atStartOfDay().toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException e) {
                // try next format
            }
        }
        return UnDefType.UNDEF;
    }
}
