/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc1;

/**
 * Class {@link NhcMessageCmd1} used as input to gson to send commands to Niko Home Control. Extends
 * {@link NhcMessageBase1}.
 * <p>
 * Example: <code>{"cmd":"executeactions","id":1,"value1":0}</code>
 *
 * @author Mark Herwege - Initial Contribution
 */
@SuppressWarnings("unused")
class NhcMessageCmd1 extends NhcMessageBase1 {

    private Integer id;
    private Integer value1;
    private Integer value2;
    private Integer value3;
    private Integer mode;
    private Integer overrule;
    private String overruletime;

    NhcMessageCmd1(String cmd) {
        super.setCmd(cmd);
    }

    NhcMessageCmd1(String cmd, Integer id) {
        this(cmd);
        this.id = id;
    }

    NhcMessageCmd1(String cmd, Integer id, Integer value1) {
        this(cmd, id);
        this.value1 = value1;
    }

    NhcMessageCmd1(String cmd, Integer id, Integer value1, Integer value2, Integer value3) {
        this(cmd, id, value1);
        this.value2 = value2;
        this.value3 = value3;
    }

    NhcMessageCmd1 withMode(Integer mode) {
        this.mode = mode;
        return this;
    }

    NhcMessageCmd1 withOverrule(Integer overrule) {
        this.overrule = overrule;
        return this;
    }

    NhcMessageCmd1 withOverruletime(String overruletime) {
        this.overruletime = overruletime;
        return this;
    }
}
