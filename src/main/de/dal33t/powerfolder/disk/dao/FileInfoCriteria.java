/*
 * Copyright 2004 - 2010 Christian Sprajc. All rights reserved.
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
package de.dal33t.powerfolder.disk.dao;

import java.util.LinkedList;
import java.util.List;

import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.disk.Folder;
import de.dal33t.powerfolder.light.DirectoryInfo;
import de.dal33t.powerfolder.light.FileInfo;

/**
 * Object that holds criterias to select {@link FileInfo}s from a
 * {@link FileInfoDAO}
 * 
 * @author sprajc
 */
public class FileInfoCriteria {
    private List<String> domains = new LinkedList<String>();
    private String path;
    private boolean recursive;

    /**
     * @return the domain(s) to search in.
     */
    public List<String> getDomains() {
        return domains;
    }

    /**
     * @param domain
     *            a domain to search in for files.
     */
    public void addDomain(String domain) {
        if (!this.domains.contains(domain)) {
            this.domains.add(domain);
        }
    }

    /**
     * @param member
     *            the member to add to the selection criteria.
     */
    public void addMember(Member member) {
        addDomain(member.getId());
    }

    /**
     * Adds all fully connected {@link Member}s and myself to the selection
     * criteria.
     * 
     * @param folder
     */
    public void addConnectedAndMyself(Folder folder) {
        addMember(folder.getController().getMySelf());
        for (Member member : folder.getMembersAsCollection()) {
            if (member.isCompletelyConnected()) {
                addMember(member);
            }
        }
    }

    /**
     * @return path the path/relative name of the sub directory.
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path
     *            the path/relative name of the sub directory.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @param dirInfo
     *            the path/relative name of the sub directory.
     */
    public void setPath(DirectoryInfo dirInfo) {
        setPath(dirInfo != null ? dirInfo.getRelativeName() : null);
    }

    /**
     * @return true to recursively include all files from subdirectory too.
     */
    public boolean isRecursive() {
        return recursive;
    }

    /**
     * @param recursive
     *            true to recursively include all files from subdirectory too.
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}