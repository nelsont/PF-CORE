/* $Id: FolderJoinTest.java,v 1.2 2006/04/16 23:01:52 totmacherr Exp $
 */
package de.dal33t.powerfolder.test.transfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.disk.SyncProfile;
import de.dal33t.powerfolder.event.TransferManagerEvent;
import de.dal33t.powerfolder.event.TransferManagerListener;
import de.dal33t.powerfolder.test.TwoControllerTestCase;
import de.dal33t.powerfolder.light.FileInfo;
import de.dal33t.powerfolder.light.FolderInfo;

/**
 * Tests if both instance join the same folder by folder id
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.2 $
 */
public class FileTransferTest extends TwoControllerTestCase {

    private static final String BASEDIR1 = "build/test/controller1/testFolder";
    private static final String BASEDIR2 = "build/test/controller2/testFolder";

    private Folder folder1;
    private Folder folder2;

    @Override
    protected void setUp() throws Exception
    {
        System.out.println("FileTransferTest.setUp()");
        super.setUp();

        // Join on testfolder
        FolderInfo testFolder = new FolderInfo("testFolder", UUID.randomUUID()
            .toString(), true);
        joinFolder(testFolder, new File(BASEDIR1), new File(BASEDIR2));
        folder1 = getContoller1().getFolderRepository().getFolder(testFolder);
        folder2 = getContoller2().getFolderRepository().getFolder(testFolder);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        System.out.println("FileTransferTest.tearDown()");
    }

    public void testSmallFileCopy() throws IOException, InterruptedException {
        // Set both folders to auto download
        folder1.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);
        folder2.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);

        FileOutputStream fOut = new FileOutputStream(folder1.getLocalBase()
            .getAbsoluteFile()
            + "/TestFile.txt");
        fOut.write("This is the contenent of the testfile".getBytes());
        fOut.close();

        // Let him scan the new content
        folder1.forceNextScan();
        folder1.scan();

        // Give them time to copy
        Thread.sleep(500);

        // Test ;)
        assertEquals(1, folder2.getFilesCount());

        // No active downloads?
        assertEquals(0, getContoller2().getTransferManager()
            .getActiveDownloadCount());
    }

    public void testFileUpdate() throws IOException, InterruptedException {
        System.out.println("FileTransferTest.testFileUpdate");
        // Set both folders to auto download
        folder1.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);
        folder2.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);

        // First copy file
        testSmallFileCopy();

        File testFile1 = new File(folder1.getLocalBase() + "/TestFile.txt");
        FileOutputStream fOut = new FileOutputStream(testFile1, true);
        fOut.write("-> Next content<-".getBytes());
        fOut.close();

        // Readin file content
        FileInputStream fIn = new FileInputStream(testFile1);
        byte[] content1 = new byte[fIn.available()];
        fIn.read(content1);
        fIn.close();

        // Let him scan the new content
        folder1.forceNextScan();
        folder1.scan();

        // Give them time to copy
        Thread.sleep(1000);

        // Test ;)
        assertEquals(1, folder2.getFilesCount());
        FileInfo testFileInfo2 = folder2.getFiles()[0];
        assertEquals(testFile1.length(), testFileInfo2.getSize());

        // Read content
        File testFile2 = testFileInfo2.getDiskFile(getContoller2()
            .getFolderRepository());
        fIn = new FileInputStream(testFile2);
        byte[] conten2 = new byte[fIn.available()];
        fIn.read(conten2);
        fIn.close();

        // Check version
        assertEquals(1, testFileInfo2.getVersion());

        // Check content
        assertEquals(new String(content1), new String(conten2));
    }

    public void testEmptyFileCopy() throws IOException, InterruptedException {
        System.out.println("FileTransferTest.testEmptyFileCopy");
        // Set both folders to auto download
        folder1.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);
        folder2.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);

        // Register listeners
        MyTransferManagerListener tm1Listener = new MyTransferManagerListener();
        getContoller1().getTransferManager().addListener(tm1Listener);
        MyTransferManagerListener tm2Listener = new MyTransferManagerListener();
        getContoller2().getTransferManager().addListener(tm2Listener);

        File testFile1 = new File(folder1.getLocalBase() + "/TestFile.txt");
        FileOutputStream fOut = new FileOutputStream(testFile1);
        fOut.write(new byte[]{});
        fOut.close();
        assertTrue(testFile1.exists());

        // Let him scan the new content
        folder1.forceNextScan();
        folder1.scan();

        // Give them time to copy
        Thread.sleep(500);

        // Check correct event fireing
        assertEquals(1, tm1Listener.uploadRequested);
        assertEquals(1, tm1Listener.uploadStarted);
        assertEquals(1, tm1Listener.uploadCompleted);

        // Check correct event fireing
        assertEquals(1, tm2Listener.downloadRequested);
        assertEquals(1, tm2Listener.downloadQueued);
        assertEquals(1, tm2Listener.downloadStarted);
        assertEquals(1, tm2Listener.downloadCompleted);
        assertEquals(0, tm2Listener.downloadsCompletedRemoved);

        // Test ;)
        assertEquals(1, folder2.getFilesCount());

        // No active downloads?
        assertEquals(0, getContoller2().getTransferManager()
            .getActiveDownloadCount());

        // Clear completed downloads
        getContoller2().getTransferManager().clearCompletedDownloads();
        // give time for event firering
        Thread.sleep(500);
        assertEquals(1, tm2Listener.downloadsCompletedRemoved);
    }

    public void testMultipleFileCopy() throws IOException, InterruptedException
    {
        System.out.println("FileTransferTest.testMultipleFileCopy");
        // Set both folders to auto download
        folder1.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);
        folder2.setSyncProfile(SyncProfile.AUTO_DOWNLOAD_FROM_ALL);

        // Register listeners
        MyTransferManagerListener tm1Listener = new MyTransferManagerListener();
        getContoller1().getTransferManager().addListener(tm1Listener);
        MyTransferManagerListener tm2Listener = new MyTransferManagerListener();
        getContoller2().getTransferManager().addListener(tm2Listener);

        int nFiles = 20;
        for (int i = 0; i < nFiles; i++) {
            createRandomFile(folder1.getLocalBase());
        }

        // Let him scan the new content
        folder1.forceNextScan();
        folder1.scan();

        // Give them time to copy
        int i = 0;
        do {
            if (tm2Listener.downloadCompleted >= nFiles) {
                break;
            }
            Thread.sleep(100);
            i++;
        } while (i < 250);

        // Check correct event fireing
        assertEquals(nFiles, tm1Listener.uploadRequested);
        assertEquals(nFiles, tm1Listener.uploadStarted);
        assertEquals(nFiles, tm1Listener.uploadCompleted);

        // Check correct event fireing
        assertEquals(nFiles, tm2Listener.downloadRequested);
        assertEquals(nFiles, tm2Listener.downloadQueued);
        assertEquals(nFiles, tm2Listener.downloadStarted);
        assertEquals(nFiles, tm2Listener.downloadCompleted);
        assertEquals(0, tm2Listener.downloadsCompletedRemoved);

        // Test ;)
        assertEquals(nFiles, folder2.getFilesCount());

        // No active downloads?!
        assertEquals(0, getContoller2().getTransferManager()
            .getActiveDownloadCount());

        // Clear completed downloads
        getContoller2().getTransferManager().clearCompletedDownloads();
        // give time for event firering
        Thread.sleep(500);
        assertEquals(nFiles, tm2Listener.downloadsCompletedRemoved);
    }

    /**
     * Creates a file with a random name and random content in the directory.
     * 
     * @param directory
     *            the dir to place the file
     * @return the file that was created
     * @throws IOException
     */
    private File createRandomFile(File directory) throws IOException {
        File randomFile = new File(directory, UUID.randomUUID().toString()
            + ".test");
        FileOutputStream fOut = new FileOutputStream(randomFile);
        fOut.write(UUID.randomUUID().toString().getBytes());
        fOut.write(UUID.randomUUID().toString().getBytes());
        fOut.write(UUID.randomUUID().toString().getBytes());
        int size = (int) (Math.random() * 100);
        for (int i = 0; i < size; i++) {
            fOut.write(UUID.randomUUID().toString().getBytes());
        }

        fOut.close();
        assertTrue(randomFile.exists());
        return randomFile;
    }

    /**
     * For checking the correct events.
     */
    private class MyTransferManagerListener implements TransferManagerListener {
        public int downloadRequested;
        public int downloadQueued;
        public int pendingDownloadEnqued;
        public int downloadStarted;
        public int downloadBroken;
        public int downloadAborted;
        public int downloadCompleted;
        public int downloadsCompletedRemoved;

        public int uploadRequested;
        public int uploadStarted;
        public int uploadBroken;
        public int uploadAborted;
        public int uploadCompleted;

        public void downloadRequested(TransferManagerEvent event) {
            downloadRequested++;
        }

        public void downloadQueued(TransferManagerEvent event) {
            downloadQueued++;
        }

        public void downloadStarted(TransferManagerEvent event) {
            downloadStarted++;
        }

        public void downloadAborted(TransferManagerEvent event) {
            downloadAborted++;
        }

        public void downloadBroken(TransferManagerEvent event) {
            downloadBroken++;
        }

        public void downloadCompleted(TransferManagerEvent event) {
            downloadCompleted++;
        }

        public void completedDownloadRemoved(TransferManagerEvent event) {
            downloadsCompletedRemoved++;
        }

        public void pendingDownloadEnqueud(TransferManagerEvent event) {
            pendingDownloadEnqued++;
        }

        public void uploadRequested(TransferManagerEvent event) {
            uploadRequested++;

        }

        public void uploadStarted(TransferManagerEvent event) {
            uploadStarted++;

        }

        public void uploadAborted(TransferManagerEvent event) {
            uploadAborted++;

        }

        public void uploadBroken(TransferManagerEvent event) {
            uploadAborted++;
        }

        public void uploadCompleted(TransferManagerEvent event) {
            uploadCompleted++;
        }

    }

}
