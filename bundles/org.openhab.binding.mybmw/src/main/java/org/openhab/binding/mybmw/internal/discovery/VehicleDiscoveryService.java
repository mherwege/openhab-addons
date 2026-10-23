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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mybmw.internal.LogRedaction;
import org.openhab.binding.mybmw.internal.api.dto.VehicleMapping;
import org.openhab.binding.mybmw.internal.handler.AccountHandler;
import org.openhab.binding.mybmw.internal.handler.VehicleHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

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

    private final Logger logger = LoggerFactory.getLogger(VehicleDiscoveryService.class);

    public VehicleDiscoveryService() {
        // no background discovery: the vehicle list costs a request of the daily budget
        super(AccountHandler.class, Set.of(THING_TYPE_VEHICLE), SCAN_TIMEOUT_SECONDS, false);
    }

    // core only resolves the @text labels of the discovery results when both providers are set
    @Reference(unbind = "-")
    public void bindTranslationProvider(TranslationProvider translationProvider) {
        this.i18nProvider = translationProvider;
    }

    @Reference(unbind = "-")
    public void bindLocaleProvider(LocaleProvider localeProvider) {
        this.localeProvider = localeProvider;
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

    /**
     * Called by the account whenever it fetched the vehicle list, which it does by itself only once it is authorized
     * and has no vehicle things yet. Publishing every fetch therefore fills the inbox when a new account comes online,
     * without background discovery.
     *
     * @param mappings the vehicles mapped to the account
     */
    public void vehiclesFetched(List<VehicleMapping> mappings) {
        publish(mappings);
    }

    /**
     * Publishes discovery results for the given vehicles. Only primary users can read a vehicle's data through
     * CarData, so secondary mappings are not offered, nor are vehicles that BMW reports cannot send data. Those can
     * still be added manually.
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
            JsonObject basicData = thingHandler.getCachedBasicData(vin);
            if (basicData != null && !VehicleHandler.isTelematicsCapable(basicData)) {
                logger.debug("Not offering vehicle {}, it cannot send data to CarData", LogRedaction.vin(vin));
                continue;
            }
            ThingUID thingUID = new ThingUID(THING_TYPE_VEHICLE, bridgeUID, vin);
            thingDiscovered(DiscoveryResultBuilder.create(thingUID).withBridge(bridgeUID).withProperty(CONFIG_VIN, vin)
                    .withRepresentationProperty(CONFIG_VIN).withLabel(label(vin, basicData)).build());
        }
    }

    /**
     * @return the model name and the shortened VIN, or the shortened VIN alone when the model is not known
     */
    private static String label(String vin, @Nullable JsonObject basicData) {
        String model = basicData != null ? VehicleHandler.propertyValue(basicData.get("modelName")) : null;
        String shortVin = LogRedaction.vin(vin);
        return model != null
                ? "@text/discovery.vehicle.model-label [\"" + model.replace("\"", "'") + "\", \"" + shortVin + "\"]"
                : "@text/discovery.vehicle.label [\"" + shortVin + "\"]";
    }
}
