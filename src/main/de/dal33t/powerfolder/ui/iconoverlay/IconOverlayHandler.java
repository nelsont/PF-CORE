/*
 * Copyright 2004 - 2014 Christian Sprajc. All rights reserved.
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
 */
package de.dal33t.powerfolder.ui.iconoverlay;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.liferay.nativity.modules.fileicon.FileIconControlCallback;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.SyncStatus;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.FolderRepository;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FileInfoFactory;

/**
 * Decide which Overlay to add to which Icon on Windows Explorer.
 * 
 * @author <a href="mailto:krickl@powerfolder.com">Maximilian Krickl</a>
 */
public class IconOverlayHandler extends PFComponent implements FileIconControlCallback
{

    public IconOverlayHandler(Controller controller) {
        super(controller);
    }

    @Override
    public int getIconForFile(String pathName) {
        // Check for folder base paths
        FolderRepository fr = getController().getFolderRepository();
        Path path = Paths.get(pathName);

        if (fr.findExistingFolder(path) != null) {
            return IconOverlayIndex.NO_OVERLAY.ordinal();
        }

        for (Folder folder : fr.getFolders()) {
            path = Paths.get(pathName);

            if (fr.findExistingFolder(path) != null
                || !path.startsWith(folder.getLocalBase()))
            {
                continue;
            }

            FileInfo lookup = FileInfoFactory.lookupInstance(folder, path);
            SyncStatus status = SyncStatus.of(getController(), lookup);

            switch (status) {
                case SYNC_OK :
                    return IconOverlayIndex.OK_OVERLAY.getIndex();
                case SYNCING :
                    return IconOverlayIndex.SYNCING_OVERLAY.getIndex();
                case IGNORED :
                    return IconOverlayIndex.IGNORED_OVERLAY.getIndex();
                case LOCKED :
                    return IconOverlayIndex.LOCKED_OVERLAY.getIndex();
                case WARNING :
                    return IconOverlayIndex.WARNING_OVERLAY.getIndex();
                case NONE :
                    return IconOverlayIndex.NO_OVERLAY.getIndex();
                default :
                    return IconOverlayIndex.NO_OVERLAY.getIndex();
            }
        }

        return IconOverlayIndex.NO_OVERLAY.ordinal();
    }
}
