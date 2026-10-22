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

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.measure.Unit;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.MyBMWBindingConstants;
import org.openhab.core.library.unit.Units;
import org.openhab.core.semantics.SemanticTag;
import org.openhab.core.semantics.model.DefaultSemanticTags;
import org.openhab.core.thing.DefaultSystemChannelTypeProvider;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.types.util.UnitUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks that the descriptor mapping and the channel definitions in {@code thing-types.xml} agree.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class TelematicChannelTest {

    // group#channel to item type, as declared in thing-types.xml
    private static final Map<String, String> CHANNEL_ITEM_TYPES = new HashMap<>();

    // channel type id to its tags, for the binding's own channel types
    private static final Map<String, List<String>> CHANNEL_TYPE_TAGS = new HashMap<>();

    private static final Set<String> POINT_TAGS = Stream
            .of(DefaultSemanticTags.Point.ALARM, DefaultSemanticTags.Point.CALCULATION,
                    DefaultSemanticTags.Point.CONTROL, DefaultSemanticTags.Point.SWITCH,
                    DefaultSemanticTags.Point.FORECAST, DefaultSemanticTags.Point.MEASUREMENT,
                    DefaultSemanticTags.Point.SETPOINT, DefaultSemanticTags.Point.STATUS)
            .map(SemanticTag::getName).collect(Collectors.toSet());

    // System channel types the binding uses, by the type id used in thing-types.xml. They are defined in core, not in
    // the binding's XML; add a type here when the binding starts using it.
    private static final Map<String, ChannelType> SYSTEM_CHANNEL_TYPES = Stream
            .of(DefaultSystemChannelTypeProvider.SYSTEM_CHANNEL_BATTERY_LEVEL,
                    DefaultSystemChannelTypeProvider.SYSTEM_ELECTRIC_CURRENT,
                    DefaultSystemChannelTypeProvider.SYSTEM_ELECTRICAL_ENERGY,
                    DefaultSystemChannelTypeProvider.SYSTEM_ELECTRIC_POWER,
                    DefaultSystemChannelTypeProvider.SYSTEM_ELECTRIC_VOLTAGE,
                    DefaultSystemChannelTypeProvider.SYSTEM_LOCATION)
            .collect(Collectors.toMap(type -> "system." + type.getUID().getId(), Function.identity()));

    @BeforeAll
    static void readThingTypes() throws Exception {
        Document document;
        try (InputStream in = TelematicChannelTest.class.getResourceAsStream("/OH-INF/thing/thing-types.xml")) {
            assertNotNull(in, "thing-types.xml not on the test class path");
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        Map<String, String> channelTypes = new HashMap<>();
        NodeList types = document.getElementsByTagName("channel-type");
        for (int i = 0; i < types.getLength(); i++) {
            Element type = (Element) types.item(i);
            channelTypes.put(type.getAttribute("id"),
                    type.getElementsByTagName("item-type").item(0).getTextContent().strip());
            List<String> tags = new ArrayList<>();
            NodeList tagNodes = type.getElementsByTagName("tag");
            for (int t = 0; t < tagNodes.getLength(); t++) {
                tags.add(tagNodes.item(t).getTextContent().strip());
            }
            CHANNEL_TYPE_TAGS.put(type.getAttribute("id"), tags);
        }
        NodeList groups = document.getElementsByTagName("channel-group-type");
        for (int i = 0; i < groups.getLength(); i++) {
            Element group = (Element) groups.item(i);
            NodeList channels = group.getElementsByTagName("channel");
            for (int j = 0; j < channels.getLength(); j++) {
                Element channel = (Element) channels.item(j);
                String typeId = channel.getAttribute("typeId");
                String itemType;
                if (typeId.startsWith("system.")) {
                    ChannelType systemType = SYSTEM_CHANNEL_TYPES.get(typeId);
                    assertNotNull(systemType, "system channel type " + typeId + " missing in SYSTEM_CHANNEL_TYPES");
                    itemType = systemType.getItemType();
                } else {
                    itemType = channelTypes.get(typeId);
                }
                assertNotNull(itemType, "unknown channel type " + typeId);
                CHANNEL_ITEM_TYPES.put(group.getAttribute("id") + "#" + channel.getAttribute("id"), itemType);
            }
        }
    }

    @Test
    void everyMappingHasAChannelOfTheRightType() {
        for (TelematicChannel mapping : TelematicChannel.values()) {
            String itemType = CHANNEL_ITEM_TYPES.get(mapping.getChannelId());
            assertNotNull(itemType, mapping + " points to missing channel " + mapping.getChannelId());
            switch (mapping.getKind()) {
                case QUANTITY -> {
                    Unit<?> unit = mapping.getDefaultUnit();
                    assertNotNull(unit, mapping + " needs a default unit");
                    if ("Number".equals(itemType)) {
                        // a plain number item drops the unit, which is only meaningful for percentages, as used by
                        // system.battery-level
                        assertEquals(Units.PERCENT, unit, mapping + " maps to a plain Number");
                        continue;
                    }
                    assertTrue(itemType.startsWith("Number:"), mapping + " maps to " + itemType);
                    String dimension = itemType.substring("Number:".length());
                    assertNotNull(UnitUtils.parseDimension(dimension), "unknown dimension in " + itemType);
                    assertEquals(dimension, UnitUtils.getDimensionName(unit),
                            mapping + " unit does not fit " + itemType);
                }
                case SWITCH -> assertEquals("Switch", itemType, mapping.toString());
                case CONTACT -> assertEquals("Contact", itemType, mapping.toString());
                case OPENING -> {
                    assertEquals("Contact", itemType, mapping.toString());
                    String detailChannelId = mapping.getDetailChannelId();
                    assertNotNull(detailChannelId, mapping + " needs a detail channel");
                    assertEquals("String", CHANNEL_ITEM_TYPES.get(detailChannelId),
                            mapping + " detail channel " + detailChannelId);
                }
                case STRING, LOCK_STATE -> assertEquals("String", itemType, mapping.toString());
                case DATE_TIME -> assertEquals("DateTime", itemType, mapping.toString());
                case LATITUDE, LONGITUDE, ALTITUDE -> assertEquals("Location", itemType, mapping.toString());
            }
        }
    }

    @Test
    void everyChannelTypeHasSemanticTags() {
        CHANNEL_TYPE_TAGS.forEach((type, tags) -> {
            assertEquals(1, tags.stream().filter(POINT_TAGS::contains).count(),
                    "channel type " + type + " needs exactly one point tag, has " + tags);
            assertTrue(tags.size() <= 2, "channel type " + type + " has more than a point and a property: " + tags);
        });
    }

    @Test
    void everyChannelIsFed() {
        Set<String> fed = new HashSet<>();
        for (TelematicChannel mapping : TelematicChannel.values()) {
            fed.add(mapping.getChannelId());
            String detailChannelId = mapping.getDetailChannelId();
            if (detailChannelId != null) {
                fed.add(detailChannelId);
            }
        }
        fed.add(MyBMWBindingConstants.GROUP_STATUS + "#" + MyBMWBindingConstants.CHANNEL_LAST_UPDATE);
        assertEquals(CHANNEL_ITEM_TYPES.keySet(), fed);
    }

    @Test
    void descriptorsAreUniqueAndLookedUp() {
        Set<String> seen = new HashSet<>();
        for (TelematicChannel mapping : TelematicChannel.values()) {
            assertTrue(seen.add(mapping.getDescriptor()), "duplicate descriptor " + mapping.getDescriptor());
            assertSame(mapping, TelematicChannel.fromDescriptor(mapping.getDescriptor()));
        }
        assertEquals(seen.size(), TelematicChannel.descriptors().size());
        assertNull(TelematicChannel.fromDescriptor("vehicle.not.mapped"));
    }
}
