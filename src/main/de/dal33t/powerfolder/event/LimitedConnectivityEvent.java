/*
* Copyright 2004 - 2008 Christian Sprajc. All rights reserved.
*
* This file is part of PowerFolder.
*
* PowerFolder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation.
*
* PowerFolder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with PowerFolder. If not, see <http://www.gnu.org/licenses/>.
*
* $Id: TransferManagerListener.java 4282 2008-06-16 03:25:09Z tot $
*/
package de.dal33t.powerfolder.event;

public class LimitedConnectivityEvent {

    private final boolean oldMode;
    private final boolean newMode;

    public LimitedConnectivityEvent(boolean oldMode, boolean newMode) {
        this.oldMode = oldMode;
        this.newMode = newMode;
    }

    public boolean isOldMode() {
        return oldMode;
    }

    public boolean isNewMode() {
        return newMode;
    }
}