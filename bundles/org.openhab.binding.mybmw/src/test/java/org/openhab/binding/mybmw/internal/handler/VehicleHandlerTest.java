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
package org.openhab.binding.mybmw.internal.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.MyBMWBindingConstants;
import org.openhab.binding.mybmw.internal.data.TelematicChannel;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Tests how {@link VehicleHandler} applies telematic values.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class VehicleHandlerTest {

    private static final ThingUID THING_UID = new ThingUID(MyBMWBindingConstants.THING_TYPE_VEHICLE, "test");
    private static final Instant T0 = Instant.parse("2026-10-21T08:00:00Z");

    private Thing thing = mock(Thing.class);
    private ThingHandlerCallback callback = mock(ThingHandlerCallback.class);
    private VehicleHandler handler = new VehicleHandler(thing);

    @BeforeEach
    void setUp() {
        when(thing.getUID()).thenReturn(THING_UID);
        when(thing.getStatus()).thenReturn(ThingStatus.ONLINE);
        handler.setCallback(callback);
    }

    private static TelematicValue value(TelematicChannel mapping, Number number, @Nullable String unit,
            Instant timestamp) {
        return new TelematicValue(mapping.getDescriptor(), new JsonPrimitive(number), unit, timestamp);
    }

    private static ChannelUID channel(String id) {
        return new ChannelUID(THING_UID, id);
    }

    @Test
    void olderValuesDoNotOverwriteNewerOnes() {
        VehicleHandler handler = this.handler;
        ThingHandlerCallback callback = this.callback;

        handler.onTelematicData(List.of(value(TelematicChannel.SOC, 70, "%", T0.plusSeconds(60))));
        // a REST refresh returning an older reading
        handler.onTelematicData(List.of(value(TelematicChannel.SOC, 65, "%", T0)));

        verify(callback).stateUpdated(channel("charge#soc"), new QuantityType<>(70, Units.PERCENT));
        verify(callback, never()).stateUpdated(channel("charge#soc"), new QuantityType<>(65, Units.PERCENT));
        verify(callback).stateUpdated(channel("status#last-update"), new DateTimeType(T0.plusSeconds(60)));
    }

    @Test
    void copiesAreAppliedOnce() {
        VehicleHandler handler = this.handler;
        ThingHandlerCallback callback = this.callback;

        handler.onTelematicData(List.of(value(TelematicChannel.SOC, 59, "%", T0)));
        // the stream delivers the same message twice
        handler.onTelematicData(List.of(value(TelematicChannel.SOC, 59, "%", T0)));
        // a new reading with the same value is applied
        handler.onTelematicData(List.of(value(TelematicChannel.SOC, 59, "%", T0.plusSeconds(40))));

        verify(callback, times(2)).stateUpdated(channel("charge#soc"), new QuantityType<>(59, Units.PERCENT));
        verify(callback).stateUpdated(channel("status#last-update"), new DateTimeType(T0));
        verify(callback).stateUpdated(channel("status#last-update"), new DateTimeType(T0.plusSeconds(40)));
    }

    @Test
    void locationIsAssembledFromParts() {
        VehicleHandler handler = this.handler;
        ThingHandlerCallback callback = this.callback;

        handler.onTelematicData(List.of(value(TelematicChannel.LATITUDE, 51.1, "degrees", T0)));
        verify(callback, never()).stateUpdated(eq(channel("location#position")), any());

        handler.onTelematicData(List.of(value(TelematicChannel.LONGITUDE, 4.3, "degrees", T0)));
        verify(callback).stateUpdated(channel("location#position"),
                new PointType(new DecimalType(new BigDecimal("51.1")), new DecimalType(new BigDecimal("4.3"))));
    }

    @Test
    void unmappedDescriptorsAreIgnored() {
        VehicleHandler handler = this.handler;
        ThingHandlerCallback callback = this.callback;

        handler.onTelematicData(List.of(new TelematicValue("vehicle.not.mapped", new JsonPrimitive(1), null, null)));
        verify(callback, never()).stateUpdated(any(), any());
    }

    @Test
    void formatsBasicDataProperties() {
        JsonArray modes = new JsonArray();
        modes.add("AC_LOW");
        modes.add("DC");
        assertEquals("AC_LOW, DC", VehicleHandler.propertyValue(modes));
        assertEquals("true", VehicleHandler.propertyValue(new JsonPrimitive(true)));
        assertEquals("BE", VehicleHandler.propertyValue(new JsonPrimitive("BE")));
        assertNull(VehicleHandler.propertyValue(new JsonPrimitive(" ")));
        assertNull(VehicleHandler.propertyValue(new JsonArray()));
        assertNull(VehicleHandler.propertyValue(null));
    }

    @Test
    void readsTelematicsCapability() {
        JsonObject basicData = new JsonObject();
        assertTrue(VehicleHandler.isTelematicsCapable(basicData));
        basicData.addProperty("isTelematicsCapable", true);
        assertTrue(VehicleHandler.isTelematicsCapable(basicData));
        basicData.addProperty("isTelematicsCapable", false);
        assertFalse(VehicleHandler.isTelematicsCapable(basicData));
    }

    @Test
    void vehicleThatCannotSendDataIsOffline() {
        when(thing.getConfiguration()).thenReturn(new Configuration(Map.of("vin", "WBA21CF0X0CN12345")));
        when(thing.getProperties()).thenReturn(Map.of(MyBMWBindingConstants.PROPERTY_TELEMATICS_CAPABLE, "false"));
        handler.initialize();
        verify(callback).statusUpdated(eq(thing),
                argThat((ThingStatusInfo info) -> info.getStatus() == ThingStatus.OFFLINE
                        && info.getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR
                        && "@text/offline.not-telematics-capable".equals(info.getDescription())));
    }

    @Test
    void removesChannelsOnlyForEquipmentReportedMissing() {
        assertEquals(List.of(), VehicleHandler.irrelevantChannelIds(Map.of()));
        assertEquals(List.of(), VehicleHandler.irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_HAS_SUNROOF,
                "", MyBMWBindingConstants.PROPERTY_HAS_NAVIGATION, "true")));
        assertEquals(List.of("windows#sunroof", "windows#sunroof-state", "location#position", "location#heading"),
                VehicleHandler.irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_HAS_SUNROOF, "false",
                        MyBMWBindingConstants.PROPERTY_HAS_NAVIGATION, "false")));
    }

    @Test
    void removesChannelsThatDoNotFitTheDrive() {
        List<String> electric = VehicleHandler
                .irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_DRIVE_TRAIN, "BEV"));
        assertTrue(electric.contains("range#fuel-level"));
        assertTrue(electric.contains("range#total-range"));
        assertTrue(electric.contains("climate#remote-engine-running"));
        assertTrue(electric.contains("range#total-fuel"));
        assertTrue(electric.contains("range#total-fuel-distance"));
        assertFalse(electric.contains("charge#soc"));
        assertFalse(electric.contains("range#electric-range"));

        List<String> combustion = VehicleHandler
                .irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_PROPULSION_TYPE, "CE"));
        assertTrue(combustion.contains("charge#soc"));
        assertTrue(combustion.contains("range#electric-range"));
        assertTrue(combustion.contains("charge#total-charged-distance"));
        assertFalse(combustion.contains("range#fuel-level"));

        assertTrue(VehicleHandler.irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_DRIVE_TRAIN, "MHEV_48V"))
                .contains("charge#soc"));
        assertEquals(List.of(),
                VehicleHandler.irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_DRIVE_TRAIN, "PHEV")));
        assertEquals(List.of(),
                VehicleHandler.irrelevantChannelIds(Map.of(MyBMWBindingConstants.PROPERTY_DRIVE_TRAIN, "SOMETHING")));
    }
}
