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
package org.openhab.binding.mybmw.internal.api.dto;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A telematic data container: a named set of descriptors whose current values can be fetched in one REST request.
 *
 * @author Mark Herwege - Initial contribution
 */
public class Container {

    public static final String STATE_ACTIVE = "ACTIVE";

    public @Nullable String containerId;
    public @Nullable String name;
    public @Nullable String purpose;
    // ACTIVE, INACTIVE or DELETED
    public @Nullable String state;
    public @Nullable String created;
    // Only filled in container details, not in the container list
    public @Nullable List<String> technicalDescriptors;

    /**
     * Wrapper of {@code GET /customers/containers}.
     */
    public static class ContainerList {
        public @Nullable List<Container> containers;
    }

    /**
     * Body of {@code POST /customers/containers}.
     */
    public static class CreateRequest {
        public final String name;
        public final String purpose;
        public final List<String> technicalDescriptors;

        public CreateRequest(String name, String purpose, List<String> technicalDescriptors) {
            this.name = name;
            this.purpose = purpose;
            this.technicalDescriptors = technicalDescriptors;
        }
    }
}
