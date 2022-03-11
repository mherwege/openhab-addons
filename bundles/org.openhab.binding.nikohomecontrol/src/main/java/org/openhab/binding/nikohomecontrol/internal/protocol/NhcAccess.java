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
package org.openhab.binding.nikohomecontrol.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.AccessType;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcAccess2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcAccess} class represents the access control Niko Home Control communication object. It contains all
 * fields representing a Niko Home Control access control device and has methods to unlock the door in Niko Home Control
 * and receive bell signals. A specific implementation is {@link NhcAccess2}.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public abstract class NhcAccess {
    private final Logger logger = LoggerFactory.getLogger(NhcAccess.class);

    protected NikoHomeControlCommunication nhcComm;

    protected String id;
    protected String name;
    protected AccessType type;
    protected @Nullable String location;
    protected volatile boolean bellRinging;
    protected volatile boolean ringAndComeIn;
    protected volatile boolean locked;

    protected @Nullable NhcVideo nhcVideo;
    protected @Nullable String buttonId;
    protected int buttonIndex = 1;

    @Nullable
    private NhcAccessEvent eventHandler;

    protected NhcAccess(String id, String name, @Nullable String location, AccessType type, @Nullable String buttonId,
            NikoHomeControlCommunication nhcComm) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
        this.nhcComm = nhcComm;

        this.buttonId = buttonId;
        try {
            if (buttonId != null) {
                int index = Integer.parseInt(buttonId.split("_")[1]);
                buttonIndex = index;
            }
        } catch (NumberFormatException e) {
            logger.debug("cannot retrieve button index from butto id {}", buttonId);
        }
    }

    /**
     * This method should be called when an object implementing the {@NhcAccessEvent} interface is initialized.
     * It keeps a record of the event handler in that object so it can be updated when the access control device
     * receives an update from the Niko Home Control IP-interface.
     *
     * @param eventHandler
     */
    public void setEventHandler(NhcAccessEvent eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * This method should be called when an object implementing the {@NhcAccessEvent} interface is disposed.
     * It resets the reference, so no updates go to the handler anymore.
     *
     */
    public void unsetEventHandler() {
        this.eventHandler = null;
    }

    public void setNhcVideo(@Nullable NhcVideo nhcVideo) {
        this.nhcVideo = nhcVideo;
        NhcAccessEvent handler = eventHandler;
        if (handler != null) {
            handler.updateVideoDeviceProperties();
        }
    }

    /**
     * Get the id of the access control device.
     *
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Get name of the access control device.
     *
     * @return access control name
     */
    public String getName() {
        return name;
    }

    /**
     * Set name of the access control device.
     *
     * @param access control name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get type of access control identified.
     * <p>
     * AccessType can be GENERIC (only doorlock), RINGANDCOMEIN (doorlock and ring and come in on/off) or BELLBUTTON
     * (doorlock and bell ringing).
     *
     * @return {@link ActionType}
     */
    public AccessType getType() {
        return type;
    }

    /**
     * Get location name of access control device.
     *
     * @return location name
     */
    public @Nullable String getLocation() {
        return location;
    }

    /**
     * Set location of the access control device.
     *
     * @param access control location
     */
    public void setLocation(@Nullable String location) {
        this.location = location;
    }

    /**
     * @return buttonId of button connected to access control action, null if no button connected
     */
    public @Nullable String getButtonId() {
        return buttonId;
    }

    public int getButtonIndex() {
        return buttonIndex;
    }

    public boolean supportsVideoStream() {
        NhcVideo video = nhcVideo;
        return (video != null) ? video.supportsVideoStream() : false;
    }

    public @Nullable String getIpAddress() {
        NhcVideo video = nhcVideo;
        return (video != null) ? video.getIpAddress() : null;
    }

    public @Nullable String getMjpegUri() {
        NhcVideo video = nhcVideo;
        return (video != null) ? video.getMjpegUri() : null;
    }

    public @Nullable String getTnUri() {
        NhcVideo video = nhcVideo;
        return (video != null) ? video.getTnUri() : null;
    }

    /**
     * Get state of the access control device.
     *
     * @return action state
     */
    public boolean getBellState() {
        return bellRinging;
    }

    public void updateBellState(boolean state) {
        bellRinging = state;
        NhcAccessEvent eventHandler = this.eventHandler;
        if (eventHandler != null) {
            logger.debug("update channel state for {} with {}", id, state);
            eventHandler.accessBellEvent(state);
        }
    }

    /**
     * Get state of ring and come in.
     *
     * @return ring and come in state, true if enabled
     */
    public boolean getRingAndComeInState() {
        return ringAndComeIn;
    }

    public void updateRingAndComeInState(boolean state) {
        ringAndComeIn = state;
        NhcAccessEvent eventHandler = this.eventHandler;
        if (eventHandler != null) {
            logger.debug("update channel state for {} with {}", id, state);
            eventHandler.accessRingAndComeInEvent(state);
        }
    }

    /**
     * Get state of the access control device.
     *
     * @return action state
     */
    public boolean getDoorLockState() {
        return locked;
    }

    public void updateDoorLockState(boolean state) {
        locked = state;
        NhcAccessEvent eventHandler = this.eventHandler;
        if (eventHandler != null) {
            logger.debug("update channel state for {} with {}", id, state);
            eventHandler.accessDoorLockEvent(state);
        }
    }

    /**
     * Method called when access control device is removed from the Niko Home Control Controller.
     */
    public void accessDeviceRemoved() {
        logger.debug("access control device removed {}, {}", id, name);
        NhcAccessEvent eventHandler = this.eventHandler;
        if (eventHandler != null) {
            eventHandler.deviceRemoved();
            unsetEventHandler();
        }

        NhcVideo video = nhcVideo;
        if (video != null) {
            video.removeNhcAccess(buttonIndex);
        }
        nhcVideo = null;
    }

    public void executeBell() {
        NhcVideo video = nhcVideo;
        if (type.equals(AccessType.BELLBUTTON)) {
            logger.debug("execute bell for {}", id);
            nhcComm.executeAccessBell(id);
        } else if (video != null) {
            video.executeBell(buttonIndex);
        }
    }

    public void executeRingAndComeIn(boolean ringAndComeIn) {
        logger.debug("switch ring and come in for {} to {}", id, ringAndComeIn);
        nhcComm.executeAccessRingAndComeIn(id, ringAndComeIn);
    }

    public void executeUnlock() {
        logger.debug("execute unlock for {}", id);
        nhcComm.executeAccessUnlock(id);
    }
}
