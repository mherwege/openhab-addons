/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.upnpcontrol.internal.discovery;

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.UpnpService;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDN;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants;
import org.openhab.binding.upnpcontrol.internal.util.UpnpControlUtil;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Mark Herwege - Initial contribution
 */
@Component(service = DiscoveryService.class, configurationPid = "discovery.upnpcontrol")
@NonNullByDefault
public class UpnpControlDiscoveryService extends AbstractDiscoveryService implements RegistryListener {

    private final Logger logger = LoggerFactory.getLogger(UpnpControlDiscoveryService.class);

    private long removalGracePeriod = 50L;

    /*
     * Map of scheduled tasks to remove devices from the Inbox
     */
    private final Map<UDN, Future<?>> deviceRemovalTasks = new ConcurrentHashMap<>();

    private Set<ThingTypeUID> supportedThingTypes;
    private final UpnpService upnpService;

    @Activate
    public UpnpControlDiscoveryService(final @Nullable Map<String, Object> configProperties, //
            final @Reference UpnpService upnpService, //
            final @Reference TranslationProvider i18nProvider, //
            final @Reference LocaleProvider localeProvider) {
        super(5);

        this.supportedThingTypes = UpnpControlBindingConstants.SUPPORTED_THING_TYPES_UIDS;

        this.upnpService = upnpService;
        this.i18nProvider = i18nProvider;
        this.localeProvider = localeProvider;

        activateOrModifyService(configProperties);
        super.activate(configProperties);

        startScan();
    }

    @Override
    @Modified
    protected void modified(@Nullable Map<String, Object> configProperties) {
        activateOrModifyService(configProperties);
        super.modified(configProperties);
    }

    private void activateOrModifyService(final @Nullable Map<String, Object> configProperties) {
        if (configProperties != null) {
            String removalGracePeriodPropertyValue = (String) configProperties
                    .get(CONFIG_PROPERTY_REMOVAL_GRACE_PERIOD);
            if (removalGracePeriodPropertyValue != null && !removalGracePeriodPropertyValue.isBlank()) {
                try {
                    removalGracePeriod = Long.parseLong(removalGracePeriodPropertyValue);
                } catch (NumberFormatException e) {
                    logger.warn("Configuration property '{}' has invalid value: {}",
                            CONFIG_PROPERTY_REMOVAL_GRACE_PERIOD, removalGracePeriodPropertyValue);
                }
            }
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        return supportedThingTypes;
    }

    @Override
    protected void startBackgroundDiscovery() {
        upnpService.getRegistry().addListener(this);
    }

    @Override
    protected void stopBackgroundDiscovery() {
        upnpService.getRegistry().removeListener(this);
    }

    @Override
    protected void startScan() {
        for (RemoteDevice device : upnpService.getRegistry().getRemoteDevices()) {
            remoteDeviceAdded(upnpService.getRegistry(), device);
        }
        upnpService.getRegistry().addListener(this);
        upnpService.getControlPoint().search();
    }

    @Override
    protected synchronized void stopScan() {
        removeOlderResults(getTimestampOfLastScan());
        super.stopScan();
        if (!isBackgroundDiscoveryEnabled()) {
            upnpService.getRegistry().removeListener(this);
        }
    }

    @Override
    public void remoteDeviceAdded(@Nullable Registry registry, @Nullable RemoteDevice device) {
        if (device != null) {
            for (RemoteDevice subDevice : UpnpControlUtil.getDevices(device)) {
                DiscoveryResult result = createDiscoveryResult(subDevice);
                if (result != null) {
                    thingDiscovered(result);
                }
            }
        }
    }

    /*
     * If the device has been scheduled to be removed, cancel its respective removal task
     */
    private void cancelRemovalTask(UDN udn) {
        Future<?> deviceRemovalTask = deviceRemovalTasks.remove(udn);
        if (deviceRemovalTask != null) {
            deviceRemovalTask.cancel(false);
        }
    }

    @Override
    public void remoteDeviceRemoved(@Nullable Registry registry, @Nullable RemoteDevice device) {
        if (device != null) {
            long gracePeriod = getRemovalGracePeriodSeconds(device);
            if (gracePeriod <= 0) {
                thingRemoved(device);
            } else {
                UDN udn = device.getIdentity().getUdn();
                cancelRemovalTask(udn);
                deviceRemovalTasks.put(udn, scheduler.schedule(() -> {
                    thingRemoved(device);
                    cancelRemovalTask(udn);
                }, gracePeriod, TimeUnit.SECONDS));
            }
        }
    }

    private void thingRemoved(RemoteDevice device) {
        for (RemoteDevice subDevice : UpnpControlUtil.getDevices(device)) {
            ThingUID thingUID = getThingUID(subDevice);
            if (thingUID != null) {
                thingRemoved(thingUID);
            }
        }
    }

    private long getRemovalGracePeriodSeconds(RemoteDevice device) {
        return removalGracePeriod;
    }

    private @Nullable DiscoveryResult createDiscoveryResult(RemoteDevice device) {
        DiscoveryResult result = null;
        ThingUID thingUid = getThingUID(device);
        if (thingUid != null) {
            String label = device.getDetails().getFriendlyName().isEmpty() ? device.getDisplayString()
                    : device.getDetails().getFriendlyName();
            Map<String, Object> properties = new HashMap<>();
            URL descriptorURL = device.getIdentity().getDescriptorURL();
            properties.put("ipAddress", descriptorURL.getHost());
            properties.put("udn", device.getIdentity().getUdn().getIdentifierString());
            properties.put("deviceDescrURL", descriptorURL.toString());
            URL baseURL = device.getDetails().getBaseURL();
            if (baseURL != null) {
                properties.put("baseURL", device.getDetails().getBaseURL().toString());
            }
            for (RemoteService service : device.getServices()) {
                properties.put(service.getServiceType().getType() + "DescrURI", service.getDescriptorURI().toString());
            }
            result = DiscoveryResultBuilder.create(thingUid).withLabel(label).withProperties(properties)
                    .withRepresentationProperty("udn").build();
        }
        return result;
    }

    private @Nullable ThingUID getThingUID(RemoteDevice device) {
        ThingUID result = null;
        String deviceType = device.getType().getType();
        String manufacturer = device.getDetails().getManufacturerDetails().getManufacturer();
        String model = device.getDetails().getModelDetails().getModelName();
        String serialNumber = device.getDetails().getSerialNumber();
        String udn = device.getIdentity().getUdn().getIdentifierString();
        boolean hasSubDevices = device.hasEmbeddedDevices();
        int expireMin = device.getIdentity().getMaxAgeSeconds() / 60;

        logger.debug("Device type {}, manufacturer {}, model {}, SN# {}, UDN {}, has subdevices {}, expires in {} min",
                deviceType, manufacturer, model, serialNumber, udn, hasSubDevices, expireMin);

        if ("MediaRenderer".equalsIgnoreCase(deviceType)) {
            logger.debug("Media renderer found: {}, {}", manufacturer, model);
            ThingTypeUID thingTypeUID = THING_TYPE_RENDERER;
            result = new ThingUID(thingTypeUID, device.getIdentity().getUdn().getIdentifierString());
        } else if ("MediaServer".equalsIgnoreCase(deviceType)) {
            logger.debug("Media server found: {}, {}", manufacturer, model);
            ThingTypeUID thingTypeUID = THING_TYPE_SERVER;
            result = new ThingUID(thingTypeUID, device.getIdentity().getUdn().getIdentifierString());
        }
        return result;
    }

    @Override
    public void remoteDeviceUpdated(@Nullable Registry registry, @Nullable RemoteDevice device) {
    }

    @Override
    public void localDeviceAdded(@Nullable Registry registry, @Nullable LocalDevice device) {
    }

    @Override
    public void localDeviceRemoved(@Nullable Registry registry, @Nullable LocalDevice device) {
    }

    @Override
    public void beforeShutdown(@Nullable Registry registry) {
    }

    @Override
    public void afterShutdown() {
    }

    @Override
    public void remoteDeviceDiscoveryStarted(@Nullable Registry registry, @Nullable RemoteDevice device) {
    }

    @Override
    public void remoteDeviceDiscoveryFailed(@Nullable Registry registry, @Nullable RemoteDevice device,
            @Nullable Exception ex) {
    }
}
