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
package de.dal33t.powerfolder.util.ui;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.binding.value.ValueModel;
import com.jgoodies.binding.value.ValueHolder;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PreferencesEntry;
import de.dal33t.powerfolder.ui.UIController;
import de.dal33t.powerfolder.util.Translation;
import de.dal33t.powerfolder.util.Help;
import de.dal33t.powerfolder.util.Reject;

import javax.swing.*;
import java.io.File;

/**
 * Provides some convenient one method access to some dialogs.
 * <p>
 * 
 * @author <A HREF="mailto:schaatser@powerfolder.com">Jan van Oosterom</A>
 * @author <A HREF="mailto:harry@powerfolder.com">Harry</A>
 * @version $Revision: 1.3 $
 */
public class DialogFactory {

    /**
     * Opens a DirectoryChooser with the current dir and returns the new
     * selection. Returns null if operation is cancelled.
     *
     * @param uiController
     *            the ui controller, used to get the parent frame
     * @param initialDirectoryName
     *            optional name of the initial selected directory
     * @return the chosen directory
     */
    public static File chooseDirectory(UIController uiController,
        String initialDirectoryName) {
        Reject.ifNull(initialDirectoryName, "Must supply an initial directory");
        Reject.ifBlank(initialDirectoryName, "Must supply an initial directory");
        return chooseDirectory(uiController, new File(initialDirectoryName));
    }
    /**
     * Opens a DirectoryChooser with the current dir and returns the new
     * selection. Returns null if operation is cancelled.
     *
     * @param uiController
     *            the ui controller, used to get the parent frame
     * @param initialDirectory
     *            optional initial selected directory
     * @return the chosen directory
     */
    public static File chooseDirectory(UIController uiController,
                                       File initialDirectory) {
        Reject.ifNull(initialDirectory, "Must supply an initial directory");
        Reject.ifFalse(initialDirectory.exists(),
                "Must supply a real initial directory: " +
                        initialDirectory.getAbsolutePath());
        Reject.ifFalse(initialDirectory.isDirectory(),
                "Initial directory is a file: " +
                        initialDirectory.getAbsolutePath());

        if (PreferencesEntry.PF_DIRECTORY_CHOOSER.getValueBoolean(
                uiController.getController())) {
            // Use PF chooser
            ValueModel vm = new ValueHolder();
            vm.setValue(initialDirectory);
            DirectoryChooser dc = new DirectoryChooser(
                    uiController.getController(), vm);
            dc.open();
            return (File) vm.getValue();
        } else {
            // Use standard chooser. This is a fall back in case there are ever
            // any problems with PF dir chooser.
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setMultiSelectionEnabled(false);
            chooser.setCurrentDirectory(initialDirectory);
            int i = chooser.showDialog(uiController.getActiveFrame(),
                    Translation.getTranslation("general.select"));
            if (i == JFileChooser.APPROVE_OPTION) {
                return chooser.getSelectedFile();
            } else {
                return null;
            }
        }
    }

    /**
     * The prefered way to create a FileChooser in PowerFolder. 
     * 
     * @return a file chooser
     */
    public static JFileChooser createFileChooser() {
        return new JFileChooser();
    }

    // //////////////////////////////////////////////////////////////////
    // GenericDialog provides a standard dialog look for PowerFolder. //
    // If possible, use this instead of JOptionPanel, etc //
    // //////////////////////////////////////////////////////////////////

    /**
     * Generic dialog with message and OK button.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param message
     *            the message to display in the dialog
     * @param type
     *            a {@link GenericDialogType}
     */
    public static void genericDialog(Controller controller, String title,
        String message, GenericDialogType type)
    {

        genericDialog(controller, title, message, new String[]{Translation
            .getTranslation("general.ok")}, 0, type);
    }

    /**
     * Generic dialog with message and throwable and OK button. The throwable is
     * only shown in verbose mode.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param message
     *            the message to display in the dialog
     * @param verbose
     *            whether the full stack trace should be displayed if in verbose
     *            mode
     * @param throwable
     *            the throwable that is to be displayed in verbose mode
     */
    public static void genericDialog(Controller controller, String title,
        String message, boolean verbose, Throwable throwable)
    {

        String innerText;
        if (verbose && throwable != null) {
            innerText = message + "\nReason: " + throwable.toString();
        } else {
            innerText = message;
        }

        genericDialog(controller, title, innerText, new String[]{Translation
            .getTranslation("general.ok")}, 0, GenericDialogType.ERROR);
    }

    /**
     * Generic dialog with message.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param message
     *            the message to display in the dialog
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param type
     *            a {@link GenericDialogType}
     * @return the index of the selected option button, -1 if dialog cancelled
     */
    public static int genericDialog(Controller controller, String title,
        String message, String[] options, int defaultOption,
        GenericDialogType type)
    {
        return genericDialog(controller, title, message, options,
            defaultOption, null, type);
    }

    /**
     * Generic dialog with message.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param message
     *            the message to display in the dialog
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param helpLink
     *            Help class link
     * @param type
     *            a {@link GenericDialogType}
     * @return the index of the selected option button, -1 if dialog cancelled
     */
    public static int genericDialog(Controller controller, String title,
        String message, String[] options, int defaultOption, String helpLink,
        GenericDialogType type)
    {

        PanelBuilder panelBuilder = LinkedTextBuilder
            .build(controller, message);
        return genericDialog(controller, title, panelBuilder.getPanel(),
            options, defaultOption, helpLink, type);
    }

    /**
     * Generic dialog with custom panel.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param panel
     *            a panel that will be the displayed section of the dialog right
     *            of icon and above buttons
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param type
     *            a {@link GenericDialogType}
     * @return the index of the selected option button, -1 if dialog cancelled
     */
    public static int genericDialog(Controller controller, String title,
        JPanel panel, String[] options, int defaultOption,
        GenericDialogType type)
    {
        return genericDialog(controller, title, panel, options, defaultOption,
            null, type);
    }

    /**
     * Generic dialog with custom panel.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param panel
     *            a panel that will be the displayed section of the dialog right
     *            of icon and above buttons
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param type
     *            a {@link GenericDialogType}
     * @return the index of the selected option button, -1 if dialog cancelled
     */
    public static int genericDialog(Controller controller, String title,
        JPanel panel, String[] options, int defaultOption, String helpLink,
        GenericDialogType type)
    {
        JButton helpButton = null;
        if (helpLink != null) {
            helpButton = Help.createWikiLinkButton(controller, helpLink);
        }
        GenericDialog dialog = new GenericDialog(controller.getUIController()
            .getActiveFrame(), title, panel, type, options, defaultOption,
            null, helpButton);
        return dialog.display();
    }

    /**
     * Generic dialog with 'never ask again' checkbox.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param message
     *            the message to display in the dialog
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param type
     *            a {@link GenericDialogType}
     * @param neverAskAgainMessage
     *            the message to display in the 'never ask again' checkbox
     * @return {@link NeverAskAgainResponse} with 'never ask again' checkbox
     *         selection and selected button index (-1 if dialog cancelled)
     */
    public static NeverAskAgainResponse genericDialog(Controller controller,
        String title, String message, String[] options, int defaultOption,
        GenericDialogType type, String neverAskAgainMessage)
    {

        PanelBuilder panelBuilder = LinkedTextBuilder
            .build(controller, message);
        return genericDialog(controller, title, panelBuilder.getPanel(),
            options, defaultOption, type, neverAskAgainMessage);
    }

    /**
     * Generic dialog with custom panle and 'never ask again' checkbox.
     * 
     * @param controller
     * @param title
     *            the title for the dialog
     * @param panel
     *            a panel that will be the displayed section of the dialog right
     *            of icon and above buttons
     * @param options
     *            array of strings that will be displayed on a sequential bar of
     *            buttons
     * @param defaultOption
     *            the index of the option that is the default highlighted button
     * @param type
     *            a {@link GenericDialogType}
     * @param neverAskAgainMessage
     *            the message to display in the 'never ask again' checkbox
     * @return {@link NeverAskAgainResponse} with 'never ask again' checkbox
     *         selection and selected button index (-1 if dialog cancelled)
     */
    public static NeverAskAgainResponse genericDialog(Controller controller,
        String title, JPanel panel, String[] options, int defaultOption,
        GenericDialogType type, String neverAskAgainMessage)
    {
        GenericDialog dialog = new GenericDialog(controller.getUIController()
            .getActiveFrame(), title, panel, type, options, defaultOption,
            neverAskAgainMessage, null);

        return new NeverAskAgainResponse(dialog.display(), dialog
            .isNeverAskAgain());
    }
}