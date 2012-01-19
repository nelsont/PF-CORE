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
 * $Id: AddLicenseHeader.java 4282 2008-06-16 03:25:09Z tot $
 */
package de.dal33t.powerfolder.test.folder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.FileArchiver;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.transfer.DownloadManager;
import de.dal33t.powerfolder.util.test.Condition;
import de.dal33t.powerfolder.util.test.ConditionWithMessage;
import de.dal33t.powerfolder.util.test.TestHelper;
import de.dal33t.powerfolder.util.test.TwoControllerTestCase;

/**
 * Tests the correct synchronization of file deletions.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @author <a href="mailto:schaatser@powerfolder.com">Jan van Oosterom</a>
 * @version $Revision: 1.5 $
 */
public class DeletionSyncTest extends TwoControllerTestCase {

    @Override
    protected void setUp() throws Exception {
        System.out.println("DeletionSyncTest.setUp()");
        super.setUp();
        ConfigurationEntry.UPLOAD_AUTO_CLEANUP_FREQUENCY.setValue(
            getContollerBart(), Integer.MAX_VALUE);
        ConfigurationEntry.DOWNLOAD_AUTO_CLEANUP_FREQUENCY.setValue(
            getContollerBart(), Integer.MAX_VALUE);
        connectBartAndLisa();
        getContollerBart().setSilentMode(true);
        getContollerLisa().setSilentMode(true);
        // Note: Don't make friends, SYNC_PC profile should sync even if PCs are
        // not friends.
        joinTestFolder(SyncProfile.HOST_FILES);
    }

    public void testDeleteAndRestoreMultiple() throws Exception {
        for (int i = 0; i < 20; i++) {
            testDeleteAndRestore();
            tearDown();
            setUp();
        }
    }

    /**
     * TRAC #394
     */
    public void testDeleteAndRestore() {
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_DOWNLOAD);
        final Member lisaAtBart = getContollerBart().getNodeManager().getNode(
            getContollerLisa().getMySelf().getInfo());

        // Create a file with version = 1
        final File testFileBart = TestHelper.createRandomFile(getFolderAtBart()
            .getLocalBase());

        scanFolder(getFolderAtBart());
        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        assertEquals(0, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());
        TestHelper.changeFile(testFileBart);
        TestHelper.waitMilliSeconds(500);
        scanFolder(getFolderAtBart());

        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        assertEquals(1, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());

        // Let Lisa download the file via auto-dl and broadcast the change to
        // bart
        TestHelper.waitForCondition(20, new ConditionWithMessage() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() >= 1
                    && getFolderAtBart().getFilesAsCollection(lisaAtBart)
                        .size() >= 1;
            }

            public String message() {
                return "Know files at lisa: "
                    + getFolderAtLisa().getKnownItemCount()
                    + ", Lisas filelist at Bart: "
                    + getFolderAtBart().getFilesAsCollection(lisaAtBart).size();
            }
        });
        assertEquals(1, getFolderAtLisa().getKnownFiles().iterator().next()
            .getVersion());

        // Now delete the file @ bart. This should NOT be mirrored to Lisa! (She
        // has only auto-dl, no deletion sync)
        assertTrue(testFileBart.exists());
        assertTrue(testFileBart.canWrite());
        TestHelper.waitForCondition(10, new Condition() {
            public boolean reached() {
                return testFileBart.delete();
            }
        });
        scanFolder(getFolderAtBart());
        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        assertEquals(2, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());

        // @ Lisa, still the "old" version (=1).
        File testFileLisa = getFolderAtLisa().getKnownFiles().iterator().next()
            .getDiskFile(getContollerLisa().getFolderRepository());
        assertEquals(1, getFolderAtLisa().getKnownFiles().iterator().next()
            .getVersion());
        assertFileMatch(testFileLisa, getFolderAtLisa().getKnownFiles()
            .iterator().next(), getContollerLisa());

        // Now let Bart re-download the file! -> Manually triggerd
        FileInfo testfInfoBart = getFolderAtBart().getKnownFiles().iterator()
            .next();
        assertTrue("" + getFolderAtBart().getFilesAsCollection(lisaAtBart),
            lisaAtBart.hasFile(testfInfoBart));
        assertTrue(lisaAtBart.isCompletelyConnected());
        Collection<Member> sources = getContollerBart().getTransferManager()
            .getSourcesForAnyVersion(testfInfoBart);
        assertNotNull(sources);
        assertEquals("No sources found for " + testfInfoBart.toDetailString(),
            1, sources.size());
        // assertEquals(1, getFolderAtBart().getConnectedMembers()[0]
        // .getRelativeFile(testfInfoBart).getVersion());

        DownloadManager source = getContollerBart().getTransferManager()
            .downloadNewestVersion(testfInfoBart);
        assertNull("Download source is not null", source);

        // Barts version is 2 (del) and Lisa has 1, so it shouldn't revert back

        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        TestHelper.waitMilliSeconds(200);
        TestHelper.waitForCondition(20, new Condition() {
            public boolean reached() {
                return 2 == getFolderAtLisa().getKnownFiles().iterator().next()
                    .getVersion();
            }
        });

        // Check the file.
        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        // TODO: Discuss: The downloaded version should be 3 (?).
        // Version 3 of the file = restored.
        // As agreed on IRC, downloadNewestVersion shouldn't download an older
        // version even if a newer
        // one was deleted.
        assertEquals(2, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());
        assertTrue(getFolderAtLisa().getKnownFiles().iterator().next()
            .isDeleted());
    }

    /**
     * Tests the synchronization of file deletions of one file.
     */
    public void testSingleFileDeleteSync() {
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        File testFileBart = TestHelper.createRandomFile(getFolderAtBart()
            .getLocalBase());
        scanFolder(getFolderAtBart());
        long size = testFileBart.length();

        FileInfo fInfoBart = getFolderAtBart().getKnownFiles().iterator()
            .next();

        TestHelper.waitForCondition(10, new ConditionWithMessage() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() >= 1;
            }

            public String message() {
                return "Know files at lisa: "
                    + getFolderAtLisa().getKnownItemCount();
            }
        });
        assertEquals(1, getFolderAtLisa().getKnownItemCount());
        FileInfo fInfoLisa = getFolderAtLisa().getKnownFiles().iterator()
            .next();
        File testFileLisa = fInfoLisa.getDiskFile(getContollerLisa()
            .getFolderRepository());

        assertTrue(fInfoLisa.isVersionDateAndSizeIdentical(fInfoBart));
        assertEquals(testFileBart.length(), testFileLisa.length());

        // Now delete the file at lisa
        assertTrue(testFileLisa.delete());
        scanFolder(getFolderAtLisa());

        FileInfo fInfoLisaDeleted = getFolderAtLisa().getKnownFiles()
            .iterator().next();
        assertEquals(1, getFolderAtLisa().getKnownItemCount());
        assertEquals(1, fInfoLisaDeleted.getVersion());
        assertTrue(fInfoLisaDeleted.isDeleted());
        assertEquals(size, fInfoLisaDeleted.getSize());

        TestHelper.waitForCondition(10, new Condition() {
            public boolean reached() {
                return getFolderAtBart().getKnownFiles().iterator().next()
                    .isDeleted();
            }
        });

        FileInfo fInfoBartDeleted = getFolderAtBart().getKnownFiles()
            .iterator().next();
        assertEquals(1, getFolderAtBart().getKnownItemCount());
        assertEquals(1, fInfoBartDeleted.getVersion());
        assertTrue(fInfoBartDeleted.isDeleted());
        assertEquals(size, fInfoBartDeleted.getSize());

        // Assume only 1 file (=PowerFolder system dir)
        assertEquals(1, getFolderAtBart().getLocalBase().list().length);
    }

    /**
     * Tests the synchronization of file deletions of one file.
     */
    public void testMultipleFileDeleteSync() {
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        final int nFiles = 35;
        for (int i = 0; i < nFiles; i++) {
            TestHelper.createRandomFile(getFolderAtBart().getLocalBase());
        }
        scanFolder(getFolderAtBart());

        // Copy
        TestHelper.waitForCondition(50, new Condition() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() >= nFiles;
            }
        });
        assertEquals(nFiles, getFolderAtLisa().getKnownItemCount());

        // Now delete the file at lisa
        for (FileInfo fileInfo : getFolderAtLisa().getKnownFiles()) {
            assertTrue(fileInfo.getDiskFile(
                getContollerLisa().getFolderRepository()).delete());
        }
        scanFolder(getFolderAtLisa());

        assertEquals(nFiles, getFolderAtLisa().getKnownItemCount());
        for (FileInfo fileInfo : getFolderAtLisa().getKnownFiles()) {
            assertEquals(1, fileInfo.getVersion());
            assertTrue(fileInfo.isDeleted());
        }

        // Wait to sync the deletions
        TestHelper.waitMilliSeconds(1000);

        // Test the correct deletions state at bart
        assertEquals(nFiles, getFolderAtBart().getKnownItemCount());
        for (FileInfo fileInfo : getFolderAtBart().getKnownFiles()) {
            assertTrue(fileInfo.isDeleted());
            assertEquals(1, fileInfo.getVersion());
        }

        // Assume only 1 file (=PowerFolder system dir)
        assertEquals(1, getFolderAtBart().getLocalBase().list().length);

    }

    public void testDeletionSyncScenarioMultiple() throws Exception {
        for (int i = 0; i < 10; i++) {
            testDeletionSyncScenario();
            tearDown();
            setUp();
        }
    }

    /**
     * Complex scenario to test the the correct deletion synchronization.
     * <p>
     * Related tickets: #9
     * 
     * @throws IOException
     */
    public void testDeletionSyncScenario() throws IOException {
        // file "host" and "client"
        getFolderAtBart().setSyncProfile(SyncProfile.HOST_FILES);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        File file1 = TestHelper.createTestFile(
            getFolderAtBart().getLocalBase(), "/TestFile.txt",
            "This are the contents of the testfile".getBytes());
        File file2 = TestHelper.createTestFile(
            getFolderAtBart().getLocalBase(), "/TestFile2.txt",
            "This are the contents  of the 2nd testfile".getBytes());
        File file3 = TestHelper.createTestFile(
            getFolderAtBart().getLocalBase(), "/sub/sub/TestFile3.txt",
            "This are the contents of the 3nd testfile".getBytes());

        // Let him scan the new content
        scanFolder(getFolderAtBart());

        // 3 files + 2 dirs
        assertEquals(5, getFolderAtBart().getKnownItemCount());

        // Give them time to copy
        TestHelper.waitForCondition(20, new ConditionWithMessage() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() >= 5
                    && getContollerBart().getTransferManager()
                        .getCompletedUploadsCollection().size() >= 3;
            }

            public String message() {
                return "Lisa known files: "
                    + getFolderAtLisa().getKnownItemCount()
                    + ". Bart completed uploads: "
                    + getContollerBart().getTransferManager()
                        .getCompletedUploadsCollection().size();
            }
        });

        // Test ;)
        // 3 files + 2 dirs
        assertEquals(getFolderAtLisa().getKnownDirectories().toString(), 5,
            getFolderAtLisa().getKnownItemCount());

        // Version should be the 0 for new files
        for (FileInfo fileInfo : getFolderAtBart().getKnownFiles()) {
            assertEquals(0, fileInfo.getVersion());
            List<FileInfo> archivedVersions = getFolderAtLisa()
                .getFileArchiver().getArchivedFilesInfos(fileInfo);
            assertEquals(0, archivedVersions.size());
        }

        // Version should be the 0 for new files
        for (FileInfo fileInfo : getFolderAtLisa().getKnownFiles()) {
            assertEquals(0, fileInfo.getVersion());
            List<FileInfo> archivedVersions = getFolderAtLisa()
                .getFileArchiver().getArchivedFilesInfos(fileInfo);
            assertEquals(0, archivedVersions.size());
        }

        assertTrue("Unable to delete: " + file1, file1.delete());
        assertTrue("Unable to delete: " + file2, file2.delete());
        assertTrue("Unable to delete: " + file3, file3.delete());

        assertFalse(file1.exists());
        assertFalse(file2.exists());
        assertFalse(file3.exists());

        // Let him scan the new content
        scanFolder(getFolderAtBart());

        // all 3 must be deleted
        for (FileInfo fileInfo : getFolderAtBart().getKnownFiles()) {
            assertTrue(fileInfo.isDeleted());
            assertEquals(1, fileInfo.getVersion());
        }

        // Give them time to remote deletion
        TestHelper.waitMilliSeconds(3000);

        // all 3 must be deleted remote
        for (FileInfo fileInfo : getFolderAtLisa().getKnownFiles()) {
            assertTrue(fileInfo.isDeleted());
            assertEquals(1, fileInfo.getVersion());
            File file = getFolderAtLisa().getDiskFile(fileInfo);
            assertFalse(file.exists());
            List<FileInfo> archivedVersions = getFolderAtLisa()
                .getFileArchiver().getArchivedFilesInfos(fileInfo);
            assertEquals(
                "Not in archive at lisa: " + fileInfo.toDetailString(), 1,
                archivedVersions.size());
        }

        // switch profiles
        getFolderAtLisa().setSyncProfile(SyncProfile.HOST_FILES);

        FileArchiver archiver = getFolderAtLisa().getFileArchiver();
        Collection<FileInfo> filesAtLisa = getFolderAtLisa().getKnownFiles();
        for (FileInfo fileAtLisa : filesAtLisa) {
            if (fileAtLisa.isDeleted()) {
                List<FileInfo> versions = archiver
                    .getArchivedFilesInfos(fileAtLisa);
                FileInfo inArchive = versions.get(0);
                assertEquals(fileAtLisa.getRelativeName(),
                    inArchive.getRelativeName());
                archiver.restore(versions.get(0), fileAtLisa
                    .getDiskFile(getContollerLisa().getFolderRepository()));
            }
        }

        scanFolder(getFolderAtLisa());

        // all 3 must not be deleted at lisas folder
        for (FileInfo fileAtLisa : getFolderAtLisa().getKnownFiles()) {
            assertFalse(fileAtLisa.isDeleted());
            assertEquals(2, fileAtLisa.getVersion());
            assertEquals(getContollerLisa().getMySelf().getInfo(),
                fileAtLisa.getModifiedBy());
            File file = getFolderAtLisa().getDiskFile(fileAtLisa);
            assertTrue(file.exists());
            assertEquals(fileAtLisa.getSize(), file.length());
            assertEquals(fileAtLisa.getModifiedDate().getTime(),
                file.lastModified());
        }

        TestHelper.waitForCondition(2, new ConditionWithMessage() {
            public String message() {
                return "Bart incoming: " + getFolderAtBart().getIncomingFiles()
                    + " Lisa known files: "
                    + getFolderAtLisa().getKnownItemCount();
            }

            public boolean reached() {
                // Only files should be incoming
                return getFolderAtBart().getIncomingFiles().size() == 3;
            }
        });

        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getContollerBart().getFolderRepository().getFileRequestor()
            .triggerFileRequesting();

        // Give them time to undelete sync (means downloading;)
        TestHelper.waitForCondition(100, new ConditionWithMessage() {
            public boolean reached() {
                return getContollerBart().getTransferManager()
                    .countCompletedDownloads() >= 3;
            }

            public String message() {
                return "Downloaded files: "
                    + getContollerBart().getTransferManager()
                        .countCompletedDownloads() + " known: "
                    + getFolderAtBart().getKnownItemCount();
            }
        });

        // all 3 must not be deleted anymore at folder1
        for (FileInfo fileInfo : getFolderAtBart().getKnownFiles()) {
            assertEquals(2, fileInfo.getVersion());
            assertFalse(fileInfo.isDeleted());
            assertTrue(fileInfo.getDiskFile(
                getContollerBart().getFolderRepository()).exists());
        }

        for (FileInfo fileInfo : getFolderAtLisa().getKnownFiles()) {
            assertEquals(2, fileInfo.getVersion());
            assertFalse(fileInfo.isDeleted());
            assertTrue(fileInfo.getDiskFile(
                getContollerLisa().getFolderRepository()).exists());
        }
    }

    /**
     * EVIL: #666
     */
    public void testDeleteCustomProfile() {
        getFolderAtBart().setSyncProfile(
            SyncProfile.getSyncProfileByFieldList("false,false,true,true,60"));
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_DOWNLOAD);
        final Member lisaAtBart = getContollerBart().getNodeManager().getNode(
            getContollerLisa().getMySelf().getInfo());

        disconnectBartAndLisa();

        // Create a file with version = 1
        final File testFileBart = TestHelper.createRandomFile(getFolderAtBart()
            .getLocalBase());
        scanFolder(getFolderAtBart());
        assertEquals(1, getFolderAtBart().getKnownItemCount());
        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        assertEquals(0, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());
        TestHelper.changeFile(testFileBart);
        TestHelper.waitMilliSeconds(50);
        scanFolder(getFolderAtBart());

        assertEquals(1, getFolderAtBart().getKnownItemCount());
        assertFileMatch(testFileBart, getFolderAtBart().getKnownFiles()
            .iterator().next(), getContollerBart());
        assertEquals(1, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());

        connectBartAndLisa();

        // Let Lisa download the file via auto-dl and broadcast the change to
        // bart
        TestHelper.waitForCondition(10, new ConditionWithMessage() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() >= 1
                    && getFolderAtBart().getFilesAsCollection(lisaAtBart)
                        .size() >= 1;
            }

            public String message() {
                return "Files at lisa: " + getFolderAtLisa().getKnownFiles()
                    + " Bart thinks: "
                    + getFolderAtBart().getFilesAsCollection(lisaAtBart);
            }
        });
        assertEquals(1, getFolderAtLisa().getKnownFiles().iterator().next()
            .getVersion());

        // Now delete the file @ lisa.
        final File testFileLisa = getFolderAtLisa().getKnownFiles().iterator()
            .next().getDiskFile(getContollerLisa().getFolderRepository());
        TestHelper.waitForCondition(10, new ConditionWithMessage() {
            public boolean reached() {
                return testFileLisa.delete();
            }

            public String message() {
                return "Unable to delete testfile at lisa: " + testFileLisa;
            }
        });
        scanFolder(getFolderAtLisa());
        assertEquals(1, getFolderAtLisa().getKnownItemCount());

        TestHelper.waitForCondition(100, new ConditionWithMessage() {
            public boolean reached() {
                return !testFileBart.exists()
                    && 2 == getFolderAtBart().getKnownFiles().iterator().next()
                        .getVersion();
            }

            public String message() {
                return "Barts file: "
                    + getFolderAtBart().getKnownFiles().iterator().next()
                        .toDetailString();
            }
        });
        assertFalse(testFileBart.exists());
        assertEquals(2, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());
    }

    public void testDbNotInSyncDeletion() throws IOException {
        // Step 1) Create file
        File testFile = TestHelper.createRandomFile(getFolderAtBart()
            .getLocalBase());
        scanFolder(getFolderAtBart());
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        // 2) Delete and sync deletion
        testFile.delete();
        scanFolder(getFolderAtBart());
        assertTrue(getFolderAtBart().getKnownFiles().iterator().next()
            .isDeleted());

        TestHelper.waitForCondition(10, new ConditionWithMessage() {
            public boolean reached() {
                return getFolderAtLisa().getKnownItemCount() == 1;
            }

            public String message() {
                return "Know files at lisa: "
                    + getFolderAtLisa().getKnownItemCount();
            }
        });
        assertTrue(getFolderAtLisa().getKnownFiles().iterator().next()
            .isDeleted());
        assertFileMatch(testFile, getFolderAtBart().getKnownFiles().iterator()
            .next(), getContollerBart());
        disconnectBartAndLisa();

        // Now bring PF into the problematic state
        testFile.createNewFile();
        TestHelper.changeFile(testFile);

        // Not scanned yet
        connectBartAndLisa();

        TestHelper.waitMilliSeconds(1000);
        assertTrue(testFile.exists());
    }

    public void testDupeDeletedDBEntries() {
        disconnectBartAndLisa();
        getFolderAtBart().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);
        getFolderAtLisa().setSyncProfile(SyncProfile.AUTOMATIC_SYNCHRONIZATION);

        File fBart = TestHelper.createRandomFile(getFolderAtBart()
            .getLocalBase());
        scanFolder(getFolderAtBart());
        assertTrue(fBart.delete());
        scanFolder(getFolderAtBart());
        TestHelper.createTestFile(getFolderAtBart().getLocalBase(),
            fBart.getName(), new byte[0]);
        scanFolder(getFolderAtBart());
        assertTrue(fBart.delete());
        scanFolder(getFolderAtBart());
        assertEquals(3, getFolderAtBart().getKnownFiles().iterator().next()
            .getVersion());

        File fLisa = TestHelper.createTestFile(
            getFolderAtLisa().getLocalBase(), fBart.getName().toUpperCase(),
            new byte[0]);
        scanFolder(getFolderAtLisa());
        assertTrue(fLisa.delete());
        scanFolder(getFolderAtLisa());
        assertEquals(1, getFolderAtLisa().getKnownFiles().iterator().next()
            .getVersion());

        connectBartAndLisa();

        if (FileInfo.IGNORE_CASE) {
            assertEquals(1, getFolderAtBart().getKnownItemCount());
            assertEquals(1, getFolderAtLisa().getKnownItemCount());
        } else {
            TestHelper.waitForCondition(10, new ConditionWithMessage() {
                public boolean reached() {
                    return getFolderAtBart().getKnownItemCount() == 2
                        && getFolderAtLisa().getKnownItemCount() == 2;
                }

                public String message() {
                    return "Bart: " + getFolderAtBart().getKnownItemCount()
                        + ", Lisa: " + getFolderAtLisa().getKnownItemCount();
                }
            });
            assertEquals(2, getFolderAtBart().getKnownItemCount());
            assertEquals(2, getFolderAtLisa().getKnownItemCount());
        }

        TestHelper.createTestFile(getFolderAtBart().getLocalBase(),
            fBart.getName(), new byte[0]);
    }
}
