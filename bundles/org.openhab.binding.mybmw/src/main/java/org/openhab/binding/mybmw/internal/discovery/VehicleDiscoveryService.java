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
package org.openhab.binding.mybmw.internal.discovery;

import static org.openhab.binding.mybmw.internal.MyBMWBindingConstants.*;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mybmw.internal.api.dto.VehicleMapping;
import org.openhab.binding.mybmw.internal.handler.AccountHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Discovers the vehicles mapped to a CarData account.
 * <p>
 * The account fetches the vehicle list once it is authorized and pushes it here, which publishes it when background
 * discovery is enabled. A manual scan asks the account for the list, which it serves from a recent fetch when it has
 * one, to protect the daily request budget.
 *
 * @author Mark Herwege - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = VehicleDiscoveryService.class)
@NonNullByDefault
public class VehicleDiscoveryService extends AbstractThingHandlerDiscoveryService<AccountHandler> {

    private static final int SCAN_TIMEOUT_SECONDS = 30;

    private volatile boolean background;

    public VehicleDiscoveryService() {
        super(AccountHandler.class, Set.of(THING_TYPE_VEHICLE), SCAN_TIMEOUT_SECONDS, true);
    }

    @Override
    public void initialize() {
        thingHandler.setDiscoveryService(this);
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        thingHandler.setDiscoveryService(null);
    }

    @Override
    protected void startScan() {
        publish(thingHandler.getVehicleMappings());
    }

    @Override
    protected void startBackgroundDiscovery() {
        background = true;
    }

    @Override
    protected void stopBackgroundDiscovery() {
        background = false;
    }

    /**
     * Called by the account whenever it fetched the vehicle list; publishes it when background discovery is enabled.
     *
     * @param mappings the vehicles mapped to the account
     */
    public void vehiclesFetched(List<VehicleMapping> mappings) {
        if (background) {
            publish(mappings);
        }
    }

    /**
     * Publishes discovery results for the given vehicles. Only primary users can read a vehicle's data through
     * CarData, so secondary mappings are not offered.
     *
     * @param mappings the vehicles mapped to the account
     */
    private void publish(List<VehicleMapping> mappings) {
        ThingUID bridgeUID = thingHandler.getThing().getUID();
        for (VehicleMapping mapping : mappings) {
            String vin = mapping.vin;
            if (vin == null || vin.isBlank() || !VehicleMapping.PRIMARY.equals(mapping.mappingType)) {
                continue;
            }
            ThingUID thingUID = new ThingUID(THING_TYPE_VEHICLE, bridgeUID, vin);
            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withProperty(CONFIG_VIN, vin)
                    .withRepresentationProperty(CONFIG_VIN).withLabel("@text/discovery.vehicle.label [\"" + vin + "\"]")
                    .build());
        }
    }
}
