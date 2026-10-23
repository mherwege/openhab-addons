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
package org.openhab.binding.mybmw.internal;

import static org.openhab.binding.mybmw.internal.MyBMWBindingConstants.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mybmw.internal.handler.AccountHandler;
import org.openhab.binding.mybmw.internal.handler.VehicleHandler;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Creates the handlers of the MyBMW binding.
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.mybmw", service = { ThingHandlerFactory.class, MyBMWHandlerFactory.class })
public class MyBMWHandlerFactory extends BaseThingHandlerFactory {

    private final HttpClient httpClient;
    private final OAuthFactory oAuthFactory;
    private final Storage<String> storage;
    private final Set<AccountHandler> accountHandlers = ConcurrentHashMap.newKeySet();

    @Activate
    public MyBMWHandlerFactory(@Reference HttpClientFactory httpClientFactory, @Reference OAuthFactory oAuthFactory,
            @Reference StorageService storageService) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.oAuthFactory = oAuthFactory;
        this.storage = storageService.getStorage(BINDING_ID);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (THING_TYPE_ACCOUNT.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            AccountHandler handler = new AccountHandler(bridge, httpClient, oAuthFactory, storage);
            accountHandlers.add(handler);
            return handler;
        }
        if (THING_TYPE_VEHICLE.equals(thingTypeUID)) {
            return new VehicleHandler(thing);
        }
        return null;
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof AccountHandler accountHandler) {
            accountHandlers.remove(accountHandler);
        }
    }

    /**
     * @return the handlers of the account things, for the authorization page
     */
    public Set<AccountHandler> getAccountHandlers() {
        return Set.copyOf(accountHandlers);
    }
}
