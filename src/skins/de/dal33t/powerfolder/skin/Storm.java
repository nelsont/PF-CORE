/*
 * Copyright 2004 - 2009 Christian Sprajc. All rights reserved.
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
 * $Id: BlackMoonSkin.java 8103 2009-05-27 23:56:11Z tot $
 */
package de.dal33t.powerfolder.skin;

import java.util.Properties;

import de.dal33t.powerfolder.ui.Icons;

public class Storm extends AbstractSyntheticaSkin {
    public static String ICON_PROPERTIES_FILENAME = "de/dal33t/powerfolder/skin/powerfolder2/icons.properties";

    public static final String NAME = "Storm";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getID() {
        return "Storm";
    }

    @Override
    public Properties getIconsProperties() {
        Properties p = Icons.getIconProperties();
        Properties my = Icons
            .loadProperties("de/dal33t/powerfolder/skin/powerfolder2/icons.properties");
        p.putAll(my);
        return p;
    }

    @Override
    public String getSynthXMLFileName() {
        return "de/dal33t/powerfolder/skin/powerfolder2/synth.xml";
    }

}
