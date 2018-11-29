/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc1;

import java.util.HashMap;
import java.util.Map;

/**
 * Class {@link NhcMessageMap1} used as output from gson for cmd or event feedback from Niko Home Control where the
 * data part is a simple json string. Extends {@link NhcMessageBase1}.
 * <p>
 * Example: <code>{"cmd":"executeactions", "data":{"error":0}}</code>
 *
 * @author Mark Herwege - Initial Contribution
 */
class NhcMessageMap1 extends NhcMessageBase1 {

    private Map<String, String> data = new HashMap<>();

    Map<String, String> getData() {
        return this.data;
    }

    void setData(Map<String, String> data) {
        this.data = data;
    }
}
