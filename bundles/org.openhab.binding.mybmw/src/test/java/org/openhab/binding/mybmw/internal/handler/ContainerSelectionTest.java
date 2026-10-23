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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.mybmw.internal.api.dto.Container;

/**
 * Tests for the container selection of {@link AccountHandler}.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
class ContainerSelectionTest {

    private static final String PURPOSE = "openHAB MyBMW binding 1a2b3c";

    private static Container container(String id, String name, String purpose, String state) {
        Container container = new Container();
        container.containerId = id;
        container.name = name;
        container.purpose = purpose;
        container.state = state;
        return container;
    }

    @Test
    void reusesActiveContainerAndDeletesObsoleteOnes() {
        List<String> obsolete = new ArrayList<>();
        String selected = AccountHandler.selectContainer(List.of( //
                container("old", "openHAB", "openHAB MyBMW binding 99ff", "ACTIVE"), //
                container("current", "openHAB", PURPOSE, "ACTIVE"), //
                container("duplicate", "openHAB", PURPOSE, "ACTIVE"), //
                container("inactive", "openHAB", PURPOSE, "INACTIVE")), PURPOSE, obsolete);
        assertEquals("current", selected);
        assertEquals(List.of("old", "duplicate", "inactive"), obsolete);
    }

    @Test
    void leavesOtherContainersAlone() {
        List<String> obsolete = new ArrayList<>();
        String selected = AccountHandler.selectContainer(List.of( //
                container("other-app", "Home Assistant", "openHAB MyBMW binding 99ff", "ACTIVE"), //
                container("other-binding", "openHAB", "openHAB BMW CarData binding rev 5", "ACTIVE"), //
                container("deleted", "openHAB", "openHAB MyBMW binding 99ff", "DELETED")), PURPOSE, obsolete);
        assertNull(selected);
        assertEquals(List.of(), obsolete);
    }
}
