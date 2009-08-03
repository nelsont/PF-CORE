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
 * $Id$
 */
package de.dal33t.powerfolder.ui.friends;

import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.ui.Icons;
import de.dal33t.powerfolder.util.Format;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.ui.UIUtil;
import de.dal33t.powerfolder.util.ui.ColorUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Helper class which renders the search results
 * 
 * @author <A HREF="mailto:schaatser@powerfolder.com">Jan van Oosterom</A>
 */
class MemberTableCellRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value,
        boolean isSelected, boolean hasFocus, int row, int column)
    {
        int actualColumn = UIUtil.toModel(table, column);
        setHorizontalAlignment(LEFT);
        setIcon(null);
        if (value instanceof String) {// no user found
            if (actualColumn != 0) {
                value = "";
            }
        } else if (value instanceof Member) {
            Member member = (Member) value;
            switch (actualColumn) {
                case 0 : {
                    value = member.getNick();
                    setIcon(Icons.getIconFor(member));
                    break;
                }
                case 1 : {
                    value = renderAccount(member.getAccountInfo());
                    break;
                }
                case 2 : {
                    if (member.isCompleteyConnected()) {
                        value = Translation
                            .getTranslation("friends_panel.connected");
                    } else if (member.isConnectedToNetwork()) {
                        if (member.isUnableToConnect()) {
                            value = Translation
                                .getTranslation("friends_panel.unable_to_connect");
                        } else {
                            value = Translation
                                .getTranslation("friends_panel.currently_online");
                        }

                    } else {
                        value = Format.formatDate(member.getLastConnectTime());
                    }
                    if (member.getController().isVerbose()) {
                        String lastMsg = member.getLastProblem() != null
                            ? member.getLastProblem().message
                            : "n/a";
                        value = value + " (" + lastMsg + ")";
                    }
                    setHorizontalAlignment(RIGHT);
                    break;
                }
                    // case 2 : {
                    // // FIXME This may cause DNS reverselookup executed in
                    // EDT!
                    // value = replaceNullWithNA(member.getHostName());
                    // setHorizontalAlignment(SwingConstants.RIGHT);
                    // break;
                    // }
                case 3 : {
                    value = replaceNullWithNA(member.getIP());
                    int port = member.getPort();
                    if (port != 1337) {
                        value = value + ":" + port;
                    }
                    setHorizontalAlignment(RIGHT);
                    break;
                }
                case 4 : {
                    JCheckBox box = new JCheckBox("", member.isOnLAN());
                    box.setBackground(row % 2 == 0
                        ? ColorUtil.EVEN_TABLE_ROW_COLOR
                        : ColorUtil.ODD_TABLE_ROW_COLOR);
                    box.setHorizontalAlignment(CENTER);
                    return box;
                }

            }
        } else {
            throw new IllegalStateException("don't know how to render this");
        }

        if (!isSelected) {
            setBackground(row % 2 == 0
                ? ColorUtil.EVEN_TABLE_ROW_COLOR
                : ColorUtil.ODD_TABLE_ROW_COLOR);
        }

        return super.getTableCellRendererComponent(table, value, isSelected,
            hasFocus, row, column);
    }

    private String renderAccount(AccountInfo aInfo) {
        if (aInfo != null) {
            return aInfo.getScrabledUsername();
        } else {
            return "";
        }
    }

    private static final String replaceNullWithNA(String original) {
        return original == null ? Translation
            .getTranslation("friends_panel.n_a") : original;
    }
}