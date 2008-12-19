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
* $Id: MembersTab.java 5457 2008-10-17 14:25:41Z harry $
*/
package de.dal33t.powerfolder.ui.information.folder.members;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFUIComponent;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.ui.information.folder.FolderInformationTab;
import de.dal33t.powerfolder.ui.action.BaseAction;
import de.dal33t.powerfolder.ui.wizard.PFWizard;
import de.dal33t.powerfolder.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.event.ActionEvent;

/**
 * UI component for the members information tab
 */
public class MembersTab extends PFUIComponent implements FolderInformationTab {

    private JPanel uiComponent;
    private MembersTableModel model;
    private JScrollPane scrollPane;
    private MyInviteAction inviteAction;
    private MyOpenChatAction openChatAction;
    private MembersTable membersTable;
    private FolderInfo folderInfo;
    private Member selectedMember;
    /**
     * Constructor
     *
     * @param controller
     */
    public MembersTab(Controller controller) {
        super(controller);
        model = new MembersTableModel(getController());
    }

    /**
     * Set the tab with details for a folder.
     *
     * @param folderInfo
     */
    public void setFolderInfo(FolderInfo folderInfo) {
        this.folderInfo = folderInfo;
        model.setFolderInfo(folderInfo);
    }

    /**
     * Gets the ui component
     *
     * @return
     */
    public JPanel getUIComponent() {
        if (uiComponent == null) {
            initialize();
            buildUIComponent();
        }
        return uiComponent;
    }

    public void initialize() {
        inviteAction = new MyInviteAction(getController());
        openChatAction = new MyOpenChatAction(getController());
        membersTable = new MembersTable(model);
        membersTable.getSelectionModel().setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        membersTable.getSelectionModel().addListSelectionListener(
                new MySelectionListener());
        scrollPane = new JScrollPane(membersTable);

        // Whitestrip
        UIUtil.whiteStripTable(membersTable);
        UIUtil.removeBorder(scrollPane);
        UIUtil.setZeroHeight(scrollPane);

        enableOnSelection();
    }

    /**
     * Bulds the ui component.
     */
    private void buildUIComponent() {
        FormLayout layout = new FormLayout("3dlu, fill:pref:grow, 3dlu",
            "3dlu, pref, 3dlu, pref , 3dlu, fill:0:grow, 3dlu");
        PanelBuilder builder = new PanelBuilder(layout);
        CellConstraints cc = new CellConstraints();
        builder.add(createToolBar(), cc.xy(2, 2));
        builder.addSeparator(null, cc.xyw(1, 4, 3));
        builder.add(scrollPane, cc.xy(2, 6));
        uiComponent = builder.getPanel();
    }

    /**
     * @return the toolbar
     */
    private JPanel createToolBar() {
        FormLayout layout = new FormLayout("pref, 3dlu, pref, fill:pref:grow",
                "pref");
        DefaultFormBuilder builder = new DefaultFormBuilder(layout);
        CellConstraints cc = new CellConstraints();
        builder.add(new JButton(inviteAction), cc.xy(1, 1));
        builder.add(new JButton(openChatAction), cc.xy(3, 1));
        return builder.getPanel();
    }

    /**
     * Enable the invite action on the table selection.
     */
    private void enableOnSelection() {
        int selectedRow = membersTable.getSelectedRow();
        if (selectedRow >= 0) {
            selectedMember = (Member) model.getValueAt(
                    membersTable.getSelectedRow(), 0);
            openChatAction.setEnabled(true);
        } else {
            selectedMember = null;
            openChatAction.setEnabled(false);
        }
    }

    ///////////////////
    // Inner Classes //
    ///////////////////

    // Action to invite friend.
    private class MyInviteAction extends BaseAction {

        private MyInviteAction(Controller controller) {
            super("action_invite_friend", controller);
        }

        public void actionPerformed(ActionEvent e) {
            PFWizard.openSendInvitationWizard(getController(), folderInfo);
        }
    }


    private class MyOpenChatAction extends BaseAction {

        private MyOpenChatAction(Controller controller) {
            super("action_open_chat", controller);
        }

        public void actionPerformed(ActionEvent e) {
            getController().getUIController().openChat(selectedMember.getInfo());
        }
    }

    /**
     * Class to detect table selection changes.
     */
    private class MySelectionListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            enableOnSelection();
        }
    }
}