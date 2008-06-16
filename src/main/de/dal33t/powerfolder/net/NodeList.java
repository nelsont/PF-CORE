/*
* Copyright 2004 - 2008 Christian Sprajc, Dennis Waldherr. All rights reserved.
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
package de.dal33t.powerfolder.net;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.Reject;

/**
 * Container for saving and loading nodes. This class is not Thread-safe.
 * <p>
 * TODO Bytekeeper Please add a NodeListTest!
 * 
 * @author Dennis "Dante" Waldherr
 * @version $Revision$
 */
public class NodeList {
    private List<MemberInfo> nodeList;
    private Set<MemberInfo> friendsSet;

    public NodeList() {
    }

    /**
     * Initalizes this nodelist with the given collection of nodes.
     * 
     * @param nodes
     *            the nodes to include.
     * @param friends
     *            the friends to include.
     */
    public NodeList(Collection<MemberInfo> nodes, Collection<MemberInfo> friends)
    {
        nodeList = new ArrayList<MemberInfo>(nodes);
        if (friends != null) {
            friendsSet = new HashSet<MemberInfo>(friends);
        }
    }

    /**
     * Returns the Set containing the friends.
     * 
     * @return
     */
    public Set<MemberInfo> getFriendsSet() {
        if (friendsSet == null) {
            friendsSet = new HashSet<MemberInfo>();
        }

        return friendsSet;
    }

    /**
     * Returns the List containing the supernodes.
     * 
     * @return
     */
    public List<MemberInfo> getNodeList() {
        if (nodeList == null) {
            nodeList = new ArrayList<MemberInfo>();
        }

        return nodeList;
    }

    /**
     * Initializes this NodeList with the data from the given InputStream.
     * 
     * @param in
     * @throws IOException
     */
    public void load(InputStream in) throws IOException, ClassNotFoundException
    {
        ObjectInputStream oin = new ObjectInputStream(new BufferedInputStream(
            in));

        try {
            // Create new Lists/Sets instead of using those loaded.
            // This makes the use of this class independed from the
            // implementation of the saved objects.
            nodeList = new ArrayList<MemberInfo>((List<MemberInfo>) oin
                .readObject());

            // Friendlist is optional
            Object next = oin.readObject();
            if (next != null) {
                friendsSet = new HashSet<MemberInfo>((Set<MemberInfo>) next);
            }
        } finally {
            try {
                oin.close();
            } catch (Throwable t) {
            }
        }
    }

    /**
     * Saves this NodeLists contents to the given OutputStream.
     * 
     * @param out
     * @throws IOException
     */
    public void save(OutputStream out) throws IOException {
        ObjectOutputStream oout = new ObjectOutputStream(out);

        oout.writeObject(nodeList);
        oout.writeObject(friendsSet);

        oout.flush();
    }

    /**
     * Initializes this NodeList with the data from the given File.
     * 
     * @param file
     * @throws IOException
     */
    public void load(File file) throws IOException, ClassNotFoundException {
        InputStream in = new FileInputStream(file);
        load(in);
        in.close();
    }

    /**
     * Loads the list from a url.
     * 
     * @param url
     *            the url to load the nodefile from
     * @return if succeeded
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public boolean load(URL url) throws IOException, ClassNotFoundException {
        Reject.ifNull(url, "URL is null");
        Object content = url.getContent();
        if (content instanceof InputStream) {
            InputStream in = (InputStream) content;
            load(in);
            in.close();
            return true;
        }
        return false;
    }

    /**
     * Saves this NodeLists contents to the given File.
     * 
     * @param file
     * @throws IOException
     */
    public void save(File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        save(out);
        out.close();
    }
}
