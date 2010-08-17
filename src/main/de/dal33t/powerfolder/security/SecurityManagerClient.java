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
 * $Id: Folder.java 8681 2009-07-19 00:07:45Z tot $
 */
package de.dal33t.powerfolder.security;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.clientserver.RemoteCallException;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.event.ListenerSupportFactory;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Util;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * The security manager for the client.
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class SecurityManagerClient extends PFComponent implements
    SecurityManager
{

    private static final boolean CACHE_ENABLED = true;

    private static final AccountInfo NULL_ACCOUNT = new AccountInfo(null, null)
    {
        private static final long serialVersionUID = 1L;

        public String toString() {
            return "Default";
        }
    };

    private SecurityManagerListener listners;
    private ServerClient client;
    private Map<Member, Session> sessions;
    private Map<AccountInfo, PermissionsCacheSegment> permissionsCacheAccounts;

    public SecurityManagerClient(Controller controller, ServerClient client) {
        super(controller);
        Reject.ifNull(client, "Client is null");
        this.client = client;
        this.sessions = new ConcurrentHashMap<Member, Session>();
        this.permissionsCacheAccounts = new ConcurrentHashMap<AccountInfo, PermissionsCacheSegment>();
        this.listners = ListenerSupportFactory
            .createListenerSupport(SecurityManagerListener.class);
    }

    public Account authenticate(String username, char[] password) {
        Account a = client.login(username, password);
        if (!a.isValid()) {
            return null;
        }
        return a;
    }

    public Account authenticate(String username, String passwordMD5, String salt)
    {
        // TRAC #1921
        throw new UnsupportedOperationException(
            "Authentication with md5 encoded password not supported at client for "
                + username);
    }

    private final Object requestPermissionLock = new Object();

    public boolean hasPermission(Account account, Permission permission) {
        return hasPermission(account != null ? account.createInfo() : null,
            permission);
    }

    public boolean hasPermission(AccountInfo accountInfo, Permission permission)
    {
        try {
            Boolean hasPermission;
            PermissionsCacheSegment cache = permissionsCacheAccounts
                .get(nullSafeGet(accountInfo));
            if (cache != null) {
                hasPermission = cache.hasPermission(permission);
            } else {
                // Create cache
                hasPermission = null;
                cache = new PermissionsCacheSegment();
                permissionsCacheAccounts.put(nullSafeGet(accountInfo), cache);
            }
            String source;
            if (!CACHE_ENABLED) {
                hasPermission = null;
            }
            if (hasPermission == null) {
                if (client.isConnected()) {
                    synchronized (requestPermissionLock) {
                        // Re-check cache
                        PermissionsCacheSegment secondCheck = permissionsCacheAccounts
                            .get(nullSafeGet(accountInfo));
                        hasPermission = secondCheck != null ? secondCheck
                            .hasPermission(permission) : null;
                        if (!CACHE_ENABLED) {
                            hasPermission = null;
                        }
                        if (hasPermission == null) {
                            hasPermission = Boolean.valueOf(retrievePermission(
                                accountInfo, permission, cache));
                            source = "recvd";
                        } else {
                            source = "cache";
                        }
                    }
                } else {
                    hasPermission = hasPermissionDisconnected(permission);
                    source = "nocon";
                }
            } else {
                source = "cache";
            }
            if (isFine()) {
                logFine("(" + source + ") " + nullSafeGet(accountInfo)
                    + " has " + (hasPermission ? "" : "NOT ") + permission);
            }
            return hasPermission;
        } catch (RemoteCallException e) {
            if (isWarning()) {
                logWarning("Unable to check " + permission + " for "
                    + nullSafeGet(accountInfo) + ". " + e);
            }
            if (isFiner()) {
                logFiner(e);
            }

            return hasPermissionDisconnected(permission);
        }
    }

    private boolean retrievePermission(AccountInfo aInfo,
        Permission permission, PermissionsCacheSegment cache)
    {
        boolean supportsBulkRequest = false;
        try {
            supportsBulkRequest = Util.compareVersions(client.getServer()
                .getIdentity().getProgramVersion(), "4.2.9");
        } catch (Exception e) {
        }

        if (supportsBulkRequest && permission instanceof FolderPermission) {
            // Optimization. Request all folder permissions in bulk
            FolderPermission fp = (FolderPermission) permission;
            FolderInfo foInfo = fp.folder;
            if (isFine()) {
                logFine("Using bulk permission request for " + aInfo + " on "
                    + foInfo);
            }

            List<Permission> permissions = new ArrayList<Permission>(5);
            permissions.add(permission);
            permissions.add(FolderPermission.read(foInfo));
            permissions.add(FolderPermission.readWrite(foInfo));
            permissions.add(FolderPermission.admin(foInfo));
            permissions.add(FolderPermission.owner(foInfo));

            try {
                List<Boolean> result = client.getSecurityService()
                    .hasPermissions(aInfo, permissions);

                cache.set(FolderPermission.read(foInfo), result.get(1));
                cache.set(FolderPermission.readWrite(foInfo), result.get(2));
                cache.set(FolderPermission.admin(foInfo), result.get(3));
                cache.set(FolderPermission.owner(foInfo), result.get(4));

                // Original request.
                return result.get(0);
            } catch (RemoteCallException e) {
                if (e.getCause() instanceof NoSuchMethodException) {
                    // Fallthrough. Use single method.
                    logWarning("Unable to retrieve permissions in bulk. Falling back to legacy call. "
                        + aInfo + " has? " + permission);
                } else {
                    throw e;
                }
            }
        }

        // Single request / Legacy call.
        boolean singleResult = client.getSecurityService().hasPermission(aInfo,
            permission);
        cache.set(permission, singleResult);
        return singleResult;

    }

    private boolean hasPermissionDisconnected(Permission permission) {
        boolean noConnectPossible = getController().isLanOnly()
            && !client.getServer().isOnLAN();
        if (noConnectPossible) {
            // Server is not on LAN, but running in LAN only mode. Allow all
            // since we will never connect at all
            return true;
        }
        if (permission instanceof FolderPermission) {
            return ConfigurationEntry.SERVER_DISCONNECT_SYNC_ANYWAYS
                .getValueBoolean(getController());
        } else {
            return !ConfigurationEntry.SECURITY_PERMISSIONS_STRICT
                .getValueBoolean(getController());
        }
    }

    private final Object requestAccountInfoLock = new Object();

    /**
     * Gets the {@link AccountInfo} for the given node. Retrieves it from server
     * if necessary. NEVER refreshes from server when running in EDT thread.
     * 
     * @see de.dal33t.powerfolder.security.SecurityManager#getAccountInfo(de.dal33t.powerfolder.Member)
     */
    public AccountInfo getAccountInfo(Member node) {
        if (client.isServer(node)) {
            return NULL_ACCOUNT;
        }
        Session session = sessions.get(node);
        // Cache hit
        if (session != null) {
            if (isFiner()) {
                logFiner("Retured cached account for " + node + " : "
                    + session.getAccountInfo());
            }
            return session.getAccountInfo();
        }
        // Smells like hack
        if (UIUtil.isAwtAvailable() && EventQueue.isDispatchThread()) {
            if (isFiner()) {
                logFiner("Not trying to refresh account of " + node
                    + ". Running in EDT thread");
            }
            return null;
        }
        if (ServerClient.SERVER_HANDLE_MESSAGE_THREAD.get()) {
            if (isWarning()) {
                logWarning("Not trying to refresh account of " + node
                    + ". Running handleMessage thread of Server");
            }
            return null;
        }
        if (!client.isConnected()) {
            // Not available yet.
            return null;
        }
        AccountInfo aInfo;
        try {
            // TODO Check if really required
            synchronized (requestAccountInfoLock) {
                // After we are request lock owner. Check if other thread
                // probably has refreshed the session we are looking for.
                session = sessions.get(node);
                // Cache hit
                if (session != null) {
                    if (isFiner()) {
                        logFiner("Retured cached account for " + node + " : "
                            + session.getAccountInfo());
                    }
                    return session.getAccountInfo();
                }

                Map<MemberInfo, AccountInfo> res = client.getSecurityService()
                    .getAccountInfos(Collections.singleton(node.getInfo()));
                aInfo = res.get(node.getInfo());
                if (isFiner()) {
                    logFiner("Retrieved account " + aInfo + " for " + node);
                }
                if (CACHE_ENABLED) {
                    sessions.put(node, new Session(aInfo));
                }
            }
        } catch (RemoteCallException e) {
            logWarning("Unable to retrieve account info for " + node + ". " + e);
            logFiner(e);
            aInfo = null;
        }
        return aInfo;
    }

    public void nodeAccountStateChanged(final Member node) {
        Runnable refresher = null;
        if (node.isMySelf()) {
            refresher = new MySelfRefrehser(node);
        } else if (!node.isCompletelyConnected()) {
            refresher = new DisconnectRefresher(node);
        } else {
            refresher = new DefaultRefresher(node);
        }
        if (refresher != null && getController().isStarted()) {
            getController().getThreadPool().schedule(refresher, 0,
                TimeUnit.SECONDS);
        }
    }

    public void fetchAccountInfos(Collection<Member> nodes, boolean forceRefresh)
    {
        Reject.ifNull(nodes, "Nodes is null");
        try {
            if (!client.isConnected()) {
                return;
            }
            Collection<MemberInfo> reqNodes = new ArrayList<MemberInfo>(nodes
                .size());
            for (Member node : nodes) {
                if (forceRefresh || !sessions.containsKey(node)) {
                    reqNodes.add(node.getInfo());
                }
            }
            if (reqNodes.isEmpty()) {
                return;
            }
            if (isFine()) {
                logFine("Pre-fetching account infos for " + nodes.size()
                    + " nodes");
            }
            if (reqNodes.size() > 50) {
                logWarning("Pre-fetching account infos for " + nodes.size()
                    + " nodes");
            }
            Map<MemberInfo, AccountInfo> res = client.getSecurityService()
                .getAccountInfos(reqNodes);
            if (isFine()) {
                logFine("Retrieved " + res.size() + " AccountInfos for "
                    + reqNodes.size() + " requested of " + nodes.size()
                    + " nodes: " + res);
            }
            for (Entry<MemberInfo, AccountInfo> entry : res.entrySet()) {
                Member node = entry.getKey().getNode(getController(), false);
                if (node == null) {
                    continue;
                }
                AccountInfo aInfo = res.get(node.getInfo());
                if (CACHE_ENABLED) {
                    sessions.put(node, new Session(aInfo));
                }
                // logWarning("Fire account state change on " + node + " - "
                // + aInfo);
                fireNodeAccountStateChanged(node);
            }
        } catch (RemoteCallException e) {
            logWarning("Unable to retrieve account info for " + nodes.size()
                + " nodes. " + e);
            logFiner(e);
        }
    }

    // Internal helper ********************************************************

    private AccountInfo nullSafeGet(AccountInfo aInfo) {
        if (aInfo == null) {
            return NULL_ACCOUNT;
        }
        return aInfo;
    }

    private void clearNodeCache(Member node) {
        Session s = sessions.remove(node);
        if (isFiner()) {
            logFiner("Clearing cache on " + node + ": " + s);
        }
        permissionsCacheAccounts.remove(nullSafeGet(s != null ? s
            .getAccountInfo() : null));
    }

    /**
     * Handle server connect: Refresh/Precache AccountInfos of friends and
     * members on our folders.
     */
    private void prefetchAccountInfos() {
        Collection<Member> nodesToRefresh = new LinkedList<Member>();
        for (Member node : getController().getNodeManager()
            .getNodesAsCollection())
        {
            if (shouldAutoRefresh(node)) {
                nodesToRefresh.add(node);
            }
        }
        fetchAccountInfos(nodesToRefresh, true);
    }

    /**
     * Refreshes a AccountInfo for the given node if it should be pre-fetched.
     * 
     * @param node
     */
    private void refresh(Member node) {
        if (shouldAutoRefresh(node)) {
            getAccountInfo(node);
        }
        fireNodeAccountStateChanged(node);
    }

    private boolean shouldAutoRefresh(Member node) {
        return node.isMySelf() || node.isFriend() || node.hasJoinedAnyFolder()
            || node.isOnLAN();
    }

    // Event handling *********************************************************

    protected void fireNodeAccountStateChanged(Member node) {
        listners.nodeAccountStateChanged(new SecurityManagerEvent(node));
    }

    public void addListener(SecurityManagerListener listner) {
        ListenerSupportFactory.addListener(listners, listner);
    }

    public void removeListener(SecurityManagerListener listner) {
        ListenerSupportFactory.removeListener(listners, listner);
    }

    // Inner classes **********************************************************

    private final class DefaultRefresher implements Runnable {
        private final Member node;

        private DefaultRefresher(Member node) {
            this.node = node;
        }

        public void run() {
            if (client.isServer(node) && node.isCompletelyConnected()) {
                prefetchAccountInfos();
            }

            clearNodeCache(node);
            refresh(node);

            // This is required because of probably changed access
            // permissions to folder.
            node.synchronizeFolderMemberships();
        }
    }

    private final class MySelfRefrehser implements Runnable {
        private final Member node;

        private MySelfRefrehser(Member node) {
            this.node = node;
        }

        public void run() {
            try {
                client.refreshAccountDetails();
            } catch (Exception e) {
                logWarning("Unable to refresh account details. " + e);
                logFiner(e);
            }
            clearNodeCache(node);
            refresh(node);
            getController().getFolderRepository()
                .triggerSynchronizeAllFolderMemberships();
        }
    }

    private final class DisconnectRefresher implements Runnable {
        private final Member node;

        private DisconnectRefresher(Member node) {
            this.node = node;
        }

        public void run() {
            clearNodeCache(node);
            refresh(node);
        }
    }

    private class Session {
        private AccountInfo info;

        public Session(AccountInfo info) {
            super();
            // Info CAN be null! Means no login
            this.info = info;
        }

        public AccountInfo getAccountInfo() {
            return info;
        }
    }

    private class PermissionsCacheSegment {
        Map<Permission, Boolean> permissions = new ConcurrentHashMap<Permission, Boolean>();

        void set(Permission permission, Boolean hasPermission) {
            Reject.ifNull(permission, "Permission is null");
            permissions.put(permission, hasPermission);
        }

        Boolean hasPermission(Permission permission) {
            Reject.ifNull(permission, "Permission is null");
            return permissions.get(permission);
        }
    }
}
