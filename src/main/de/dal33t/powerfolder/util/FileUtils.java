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
package de.dal33t.powerfolder.util;

import java.awt.Desktop;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.util.os.OSUtil;

public class FileUtils {

    private static final Logger log = Logger.getLogger(FileUtils.class
        .getName());

    private static final int BYTE_CHUNK_SIZE = 8192;

    public static final String DOWNLOAD_META_FILE = "(downloadmeta) ";
    public static final String DESKTOP_INI_FILENAME = "desktop.ini";

    // no instances
    private FileUtils() {
    }

    /**
     * @param file
     * @return true if the given file is a meta data file for downloading
     *         purposes.
     */
    public static boolean isDownloadMetaFile(File file) {
        Reject.ifNull(file, "File is null");
        String fileName = file.getName();
        return fileName.startsWith(DOWNLOAD_META_FILE);
    }

    /**
     * @param file
     * @return true if this is a temporary download file
     */
    public static boolean isTempDownloadFile(File file) {
        if (file == null) {
            throw new NullPointerException("File is null");
        }
        String fileName = file.getName();
        return fileName.startsWith("(incomplete) ");
    }

    /**
     * @param file
     * @return true if this file is a completed download file, means there
     *         exists a targetfile with full name
     */
    public static boolean isCompletedTempDownloadFile(File file) {
        if (!isTempDownloadFile(file)) {
            return false;
        }
        // String targetFilename = file.getName().substring(11);
        String targetFilename = file.getName().substring(13);
        File targetFile = new File(file.getParentFile(), targetFilename);
        return targetFile.exists() && (targetFile.length() == file.length());
    }

    /**
     * @param file
     * @return true if this file is the windows desktop.ini
     */
    public static boolean isDesktopIni(File file) {
        if (file == null) {
            throw new NullPointerException("File is null");
        }
        return file.getName().equalsIgnoreCase("DESKTOP.INI");
    }

    /**
     * @param file
     * @return true if the file is a valid zipfile
     */
    public static boolean isValidZipFile(File file) {
        if (file == null) {
            throw new NullPointerException("File is null");
        }
        try {
            new ZipFile(file);
        } catch (ZipException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Copies a file
     * 
     * @param from
     * @param to
     *            if file exists it will be overwritten!
     * @throws IOException
     */
    public static void copyFile(File from, File to) throws IOException {
        if (from == null) {
            throw new NullPointerException("From file is null");
        }
        if (!from.exists()) {
            throw new IOException("From file does not exists "
                + from.getAbsolutePath());
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("cannot copy onto itself");
        }
        copyFromStreamToFile(new FileInputStream(from), to);
    }

    /**
     * Copies a file to disk from a stream. Overwrites the target file if exists
     * 
     * @see #copyFromStreamToFile(InputStream, File, StreamCallback, int)
     * @param in
     *            the input stream
     * @param to
     *            the file where the stream should be written in
     * @throws IOException
     */
    public static void copyFromStreamToFile(InputStream in, File to)
        throws IOException
    {
        copyFromStreamToFile(in, to, null, 0);
    }

    /**
     * Copies a file to disk from a stream. Overwrites the target file if
     * exists. The processe may be observed with a stream callback
     * 
     * @param in
     *            the input stream
     * @param to
     *            the file wher the stream should be written in
     * @param callback
     *            the callback to get information about the process, may be left
     *            null
     * @param totalAvailableBytes
     *            the byte total available
     * @throws IOException
     *             any io excetion or the stream read is broken by the callback
     */
    public static void copyFromStreamToFile(InputStream in, File to,
        StreamCallback callback, int totalAvailableBytes) throws IOException
    {
        if (in == null) {
            throw new NullPointerException("InputStream file is null");
        }
        if (to == null) {
            throw new NullPointerException("To file is null");
        }
        OutputStream out = null;
        try {
            if (to.exists()) {
                if (!to.delete()) {
                    throw new IOException("Unable to delete old file "
                        + to.getAbsolutePath());
                }
            }
            if (to.getParentFile() != null && !to.getParentFile().exists()) {
                to.getParentFile().mkdirs();
            }
            if (!to.createNewFile()) {
                throw new IOException("Unable to create file "
                    + to.getAbsolutePath());
            }
            if (!to.canWrite()) {
                throw new IOException("Unable to write to "
                    + to.getAbsolutePath());
            }

            out = new FileOutputStream(to);
            byte[] buffer = new byte[BYTE_CHUNK_SIZE];
            int read;
            int position = 0;

            do {
                read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                position += read;
                if (callback != null) {
                    // Execute callback
                    boolean breakStream = callback.streamPositionReached(
                        position, totalAvailableBytes);
                    if (breakStream) {
                        throw new IOException(
                            "Stream read break requested by callback. "
                                + callback);
                    }
                }
            } while (read >= 0);
        } finally {
            // Close streams
            try {
                in.close();
            } catch (IOException e) {
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Copies a given amount of data from one RandomAccessFile to another.
     * 
     * @param in
     *            the file to read the data from
     * @param out
     *            the file to write the data to
     * @param n
     *            the amount of bytes to transfer
     * @throws IOException
     *             if an Exception occurred while reading or writing the data
     */
    public static void ncopy(RandomAccessFile in, RandomAccessFile out, int n)
        throws IOException
    {
        int w = n;
        byte[] buf = new byte[BYTE_CHUNK_SIZE];
        while (w > 0) {
            int read = in.read(buf);
            if (read < 0) {
                throw new EOFException();
            }
            out.write(buf, 0, read);
            w -= read;
        }
    }

    /**
     * Execute the file, uses rundll approach to start on windows
     * 
     * @param file
     * @throws IOException
     */
    public static void openFile(File file) throws IOException {
        Reject.ifNull(file, "File is null");

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file);
        }
    }

    /**
     * Makes a file hidden on windows system
     * 
     * @param file
     * @return true if succeeded
     */
    public static boolean makeHiddenOnWindows(File file) {
        if (!OSUtil.isWindowsSystem()) {
            return false;
        }
        try {
            Process proc = Runtime.getRuntime().exec(
                "attrib.exe +h \"" + file.getAbsolutePath() + '\"');
            proc.waitFor();
            return true;
        } catch (IOException e) {
            log.log(Level.FINER, "IOException", e);
            return false;
        } catch (InterruptedException e) {
            log.log(Level.FINER, "InterruptedException", e);
            return false;
        }
    }

    /**
     * Makes a directory 'system' on windows system
     * 
     * @param file
     * @return true if succeeded
     */
    public static boolean makeSystemOnWindows(File file) {
        if (!OSUtil.isWindowsSystem()) {
            return false;
        }
        try {
            Process proc = Runtime.getRuntime().exec(
                "attrib.exe +s \"" + file.getAbsolutePath() + '\"');
            proc.waitFor();
            return true;
        } catch (IOException e) {
            log.log(Level.FINER, "IOException", e);
            return false;
        } catch (InterruptedException e) {
            log.log(Level.FINER, "InterruptedException", e);
            return false;
        }
    }

    /**
     * Sets file attributes on windows system
     * 
     * @param file
     * @param hidden
     * @param system
     * @return true if succeeded
     */
    public static boolean setAttributesOnWindows(File file, boolean hidden,
        boolean system)
    {
        if (!OSUtil.isWindowsSystem() || OSUtil.isWindowsMEorOlder()) {
            // Not set attributes on non-windows systems or win ME or older
            return false;
        }
        try {
            Process proc = Runtime.getRuntime().exec(
                "attrib " + (hidden ? "+h" : "") + ' ' + (system ? "+s" : "")
                    + " \"" + file.getAbsolutePath() + '\"');
            proc.getOutputStream();
            proc.waitFor();
            return true;
        } catch (IOException e) {
            log.log(Level.FINER, "IOException", e);
            return false;
        } catch (InterruptedException e) {
            log.log(Level.FINER, "InterruptedException", e);
            return false;
        }
    }

    /**
     * A recursive delete of a directory.
     * 
     * @param file
     *            directory to delete
     * @throws IOException
     */

    public static void recursiveDelete(File file) throws IOException {
        if (file != null) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                for (File nextFile : files) {
                    recursiveDelete(nextFile);
                }
            }

            if (file.exists() && !file.delete()) {
                throw new IOException("Could not delete file "
                    + file.getAbsolutePath());
            }
        }
    }

    /**
     * A recursive move of one directory to another.
     * 
     * @param sourceFile
     * @param targetFile
     * @throws IOException
     */
    public static void recursiveMove(File sourceFile, File targetFile)
        throws IOException
    {
        Reject.ifNull(sourceFile, "Source directory is null");
        Reject.ifNull(targetFile, "Target directory is null");

        if (!sourceFile.exists()) {
            // Do nothing.
            return;
        }

        if (sourceFile.isDirectory() && !targetFile.exists()) {
            targetFile.mkdirs();
        }

        if (sourceFile.isDirectory() && targetFile.isDirectory()) {
            File[] files = sourceFile.listFiles();
            for (File nextOriginalFile : files) {
                // Synthesize target file name.
                String lastPart = nextOriginalFile.getName();
                File nextTargetFile = new File(targetFile, lastPart);
                recursiveMove(nextOriginalFile, nextTargetFile);
            }
            // Delete directory after move
            sourceFile.delete();
        } else if (!sourceFile.isDirectory() && !targetFile.isDirectory()) {
            sourceFile.renameTo(targetFile);
        } else {
            throw new UnsupportedOperationException(
                "Can only copy directory to directory or file to file: "
                    + sourceFile.getAbsolutePath() + " --> "
                    + targetFile.getAbsolutePath());
        }

        // Hide target if original is hidden.
        if (sourceFile.isHidden()) {
            makeHiddenOnWindows(targetFile);
        }
    }

    /**
     * Set / remove desktop ini in managed folders.
     * 
     * @param controller
     * @param directory
     */
    public static void maintainDesktopIni(Controller controller, File directory)
    {

        // Only works on Windows
        // Vista you must log off and on again to see change
        if (!OSUtil.isWindowsSystem() || OSUtil.isWebStart()) {
            return;
        }

        // Safty checks.
        if (directory == null || !directory.exists()
            || !directory.isDirectory())
        {
            return;
        }

        // Look for a desktop ini in the folder.
        File desktopIniFile = new File(directory, DESKTOP_INI_FILENAME);
        boolean iniExists = desktopIniFile.exists();
        boolean usePfIcon = ConfigurationEntry.USE_PF_ICON
            .getValueBoolean(controller);
        if (!iniExists && usePfIcon) {
            // Need to set up desktop ini.
            PrintWriter pw = null;
            try {
                // @todo Does anyone know a nicer way of finding the run time
                // directory?
                File hereFile = new File("");
                String herePath = hereFile.getAbsolutePath();
                File powerFolderFile = new File(herePath, "PowerFolder.exe");
                if (!powerFolderFile.exists()) {
                    log.severe("Could not find PowerFolder.exe at "
                        + powerFolderFile.getAbsolutePath());
                    return;
                }

                // Write desktop ini directory
                pw = new PrintWriter(new FileWriter(new File(directory,
                    DESKTOP_INI_FILENAME)));
                pw.println("[.ShellClassInfo]");
                pw.println("ConfirmFileOp=0");
                pw.println("IconFile=" + powerFolderFile.getAbsolutePath());
                pw.println("IconIndex=0");
                pw.println("InfoTip="
                    + Translation.getTranslation("folder.info_tip"));
                pw.flush();

                // Hide the files
                makeHiddenOnWindows(desktopIniFile);

                // Now need to set folder as system for desktop.ini to work.
                makeSystemOnWindows(directory);
            } catch (IOException e) {
                log.log(Level.SEVERE, "Problem writing Desktop.ini file(s)", e);
            } finally {
                if (pw != null) {
                    try {
                        pw.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } else if (iniExists && !usePfIcon) {
            // Need to remove desktop ini.
            desktopIniFile.delete();
        }
    }

    /**
     * Method to remove the desktop ini if it exists
     * 
     * @param directory
     */
    public static void deleteDesktopIni(File directory) {
        // Look for a desktop ini in the folder.
        File desktopIniFile = new File(directory, DESKTOP_INI_FILENAME);
        boolean iniExists = desktopIniFile.exists();
        if (iniExists) {
            desktopIniFile.delete();
        }
    }

    /**
     * Scans a directory and gets full size of all files.
     * 
     * @param directory
     * @param depth
     * @return the size in byte of the directory
     */
    public static long calculateDirectorySize(File directory, int depth) {

        // Limit evil recursive symbolic links.
        if (depth == 100) {
            return 0;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        long sum = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                sum += calculateDirectorySize(file, depth + 1);
            } else {
                sum += file.length();
            }
        }
        return sum;
    }

    /**
     * Zips the file
     * 
     * @param file
     *            the file to zip
     * @param zipfile
     *            the zip file
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static void zipFile(File file, File zipfile) throws IOException {
        // Check that the directory is a directory, and get its contents
        if (!file.isFile()) {
            throw new IllegalArgumentException("Not a file:  " + file);
        }
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipfile));
        FileInputStream in = new FileInputStream(file); // Stream to read
        // file
        ZipEntry entry = new ZipEntry(file.getName()); // Make a ZipEntry
        out.putNextEntry(entry); // Store entry
        int bytesRead;
        byte[] buffer = new byte[4096]; // Create a buffer for copying
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        in.close();
        out.close();
    }

    /**
     * Zip the contents of the directory, and save it in the zipfile
     * 
     * @param dir
     * @param zipfile
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static void zipDirectory(File dir, File zipfile) throws IOException {
        // Check that the directory is a directory, and get its contents
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory:  " + dir);
        }
        String[] entries = dir.list();
        byte[] buffer = new byte[4096]; // Create a buffer for copying
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipfile));
        for (String entry1 : entries) {
            File f = new File(dir, entry1);
            if (f.isDirectory()) {
                continue;// Ignore directory
            }
            FileInputStream in = new FileInputStream(f); // Stream to read
            // file
            ZipEntry entry = new ZipEntry(f.getPath()); // Make a ZipEntry
            out.putNextEntry(entry); // Store entry
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            in.close();
        }
        out.close();
    }

    /**
     * See if a file in inside a directory.
     * 
     * @param file
     * @param directory
     * @return
     */
    public static boolean isFileInDirectory(File file, File directory) {

        Reject.ifTrue(file == null || directory == null,
            "File and directory may not be null");

        Reject.ifTrue(file.isDirectory() || !directory.isDirectory(),
            "File must be a file and directory must be a directory.");

        File fileParent = file.getParentFile();
        String fileParentPath;
        if (fileParent == null) {
            fileParentPath = File.separator;
        } else {
            fileParentPath = fileParent.getAbsolutePath();
        }
        String directoryPath = directory.getAbsolutePath();

        log.finer("File parent: " + fileParentPath);
        log.finer("Directory: " + directoryPath);

        return fileParentPath.startsWith(directoryPath);
    }

    /**
     * Removes invalid characters from the filename.
     * 
     * @param filename
     * @return
     */
    public static String removeInvalidFilenameChars(String filename) {
        String invalidChars = "/\\:*?\"<>|";
        for (int i = 0; i < invalidChars.length(); i++) {
            char c = invalidChars.charAt(i);
            while (filename.indexOf(c) != -1) {
                int index = filename.indexOf(c);
                filename = filename.substring(0, index)
                    + filename.substring(index + 1, filename.length());
            }
        }
        return filename;
    }

    /**
     * Methods does two things: 1. Removes all invalid characters from the raw
     * name and 2. searches and takes care that this directory is new and not
     * yet existing. If dir already exists with the same raw name it appends
     * (1), (2), and so on until it finds an non-existing sub directory.
     * <p>
     * 
     * @param baseDir
     * @param rawName
     *            the raw name of the directory. is it NOT guranteed that it
     *            will/can be named like this.
     * @return the directory that is guranteed to be NEW and EMPTY.
     */
    public static File createEmptyDirectory(File baseDir, String rawName) {
        Reject.ifNull(baseDir, "Base dir is null");
        Reject.ifBlank(rawName, "Raw name is null");

        String name = removeInvalidFilenameChars(rawName);
        File candidate = new File(baseDir, name);
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(baseDir, name + " (" + suffix + ")");
            suffix++;
        }
        candidate.mkdirs();
        return candidate;
    }

    /**
     * Helper method to perform hashing on a file.
     * 
     * @param file
     * @param digest
     *            the MessageDigest to use, MUST be in initial state - aka
     *            either newly created or being reseted.
     * @param listener
     * @return the result of the hashing, usually size 16.
     * @throws IOException
     *             if the file was not found or an error occured while reading.
     * @throws InterruptedException
     *             if this thread got interrupted, this can be used to cancel a
     *             ongoing hashing operation.
     */
    public static byte[] digest(File file, MessageDigest digest,
        ProgressListener listener) throws IOException, InterruptedException
    {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[BYTE_CHUNK_SIZE];
            long size = file.length();
            long pos = 0;
            int read;
            while ((read = in.read(buf)) > 0) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                digest.update(buf, 0, read);
                pos += read;
                if (listener != null) {
                    listener.progressReached(pos * 100.0 / size);
                }
            }
            return digest.digest();
        } finally {
            in.close();
        }
    }
}
