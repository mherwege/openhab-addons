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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.MyBMWBindingConstants;
import org.openhab.binding.mybmw.internal.data.TelematicChannel;
import org.openhab.binding.mybmw.internal.data.TelematicValue;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PointType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

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
}
