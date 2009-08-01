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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Feature;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.clientserver.RemoteCallException;
import de.dal33t.powerfolder.clientserver.ServerClient;
import de.dal33t.powerfolder.light.AccountInfo;
import de.dal33t.powerfolder.light.FolderInfo;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.ui.UIUtil;

/**
 * The security manager for the client.
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class SecurityManagerClient extends AbstractSecurityManager {
    private ServerClient client;
    private Map<Member, Session> sessions;
    private Map<Member, PermissionsCacheSegment> permissionsCache;
    private Map<FolderInfo, FolderPermission> defaultPermissionsCache;

    private static final boolean CACHE_ENABLED = true;

    public SecurityManagerClient(Controller controller, ServerClient client) {
        super(controller);
        Reject.ifNull(client, "Client is null");
        this.client = client;
        this.sessions = new ConcurrentHashMap<Member, Session>();
        this.permissionsCache = new ConcurrentHashMap<Member, PermissionsCacheSegment>();
        this.defaultPermissionsCache = new ConcurrentHashMap<FolderInfo, FolderPermission>();
    }

    public Account authenticate(String username, String password) {
        return client.login(username, password);
    }

    public boolean hasPermission(Member node, Permission permission) {
        Reject.ifNull(node, "Node is null");
        Reject.ifNull(permission, "Permission info is null");
        if (!Feature.SECURITY_CHECKS.isEnabled()) {
            return true;
        }
        try {
            Boolean hasPermission;
            PermissionsCacheSegment cache = permissionsCache.get(node);
            if (cache != null) {
                hasPermission = cache.hasPermission(permission);
            } else {
                // Create cache
                hasPermission = null;
                cache = new PermissionsCacheSegment();
                permissionsCache.put(node, cache);
            }
            boolean cacheHit;
            if (!CACHE_ENABLED) {
                hasPermission = null;
            }
            if (hasPermission == null) {
                hasPermission = Boolean.valueOf(client.getSecurityService()
                    .hasPermission(node.getInfo(), permission));
                cache.set(permission, hasPermission);
                cacheHit = false;
            } else {
                cacheHit = true;
            }
            logWarning((cacheHit ? "(cachd) " : "(retvd) ") + node + " has "
                + (hasPermission ? "" : "NOT ") + permission);

            return hasPermission;
        } catch (RemoteCallException e) {
            logWarning("Unable to check permission for " + node + ". " + e);
            logFiner(e);
            return false;
        }
    }

    /**
     * Gets the {@link AccountInfo} for the given node. Retrieves it from server
     * if necessary. NEVER refreshes from server when running in EDT thread.
     * 
     * @see de.dal33t.powerfolder.security.SecurityManager#getAccountInfo(de.dal33t.powerfolder.Member)
     */
    public AccountInfo getAccountInfo(Member node) {
        Session session = sessions.get(node);
        // Cache hit
        if (session != null && CACHE_ENABLED) {
            return session.getAccountInfo();
        }
        if (!client.isConnected()) {
            // Not available yet.
            return null;
        }
        if (node.isMySelf()) {
            Account myAccount = client.getAccount();
            if (myAccount == null || !myAccount.isValid()) {
                return null;
            }
            return myAccount.createInfo();
        }
        // Smells like hack
        if (UIUtil.isAWTAvailable() && EventQueue.isDispatchThread()) {
            if (isFiner()) {
                logFiner("Not trying to refresh account of " + node
                    + " running in EDT thread");
            }
            return null;
        }
        AccountInfo aInfo;
        try {
            Map<MemberInfo, AccountInfo> res = client.getSecurityService()
                .getAccountInfos(Collections.singleton(node.getInfo()));
            aInfo = res.get(node.getInfo());
            logWarning("Retrieved account " + aInfo + " for " + node);
        } catch (RemoteCallException e) {
            logSevere("Unable to retrieve account info for " + node + ". " + e);
            logFiner(e);
            aInfo = null;
        }
        if (CACHE_ENABLED) {
            sessions.put(node, new Session(aInfo));
        }
        return aInfo;
    }

    public void nodeAccountStateChanged(final Member node) {
        Runnable refresher = null;
        if (node.isMySelf()) {
            refresher = new MySelfRefrehser(node);
        } else if (!node.isCompleteyConnected()) {
            refresher = new DisconnectRefresher(node);
        } else {
            refresher = new DefaultRefresher(node);
        }
        if (refresher != null && getController().isStarted()) {
            getController().getThreadPool().schedule(refresher, 0,
                TimeUnit.SECONDS);
        }
    }

    @Override
    protected FolderPermission retrieveDefaultPermission(FolderInfo foInfo) {
        Reject.ifNull(foInfo, "FolderInfo is null");
        try {
            return getController().getOSClient().getSecurityService()
                .getDefaultPermission(foInfo);
        } catch (Exception e) {
            logWarning("Unable to retrieve default permission from server. Using admin as fallback. "
                + e);
            logFiner(e);
            return new FolderAdminPermission(foInfo);
        }
    }

    // Internal helper ********************************************************

    private void clearNodeCache(Member node) {
        permissionsCache.remove(node);
        sessions.remove(node);
    }

    /**
     * Handle server connect: Refresh/Precache AccountInfos of friends and
     * members on our folders.
     */
    private void prefetchAccountInfos() {
        // TODO Bulk request new account infos.
        for (Member node : getController().getNodeManager()
            .getNodesAsCollection())
        {
            AccountInfo aInfo = autoRefresh(node);
            fireNodeAccountStateChanged(node, aInfo);
        }
    }

    /**
     * Refreshes a AccountInfo for the given node if it should be pre-fetched.
     * Otherwise tries to return cached entry.
     * 
     * @param node
     * @return
     */
    private AccountInfo autoRefresh(Member node) {
        // TODO: Use Member.isInteresting() ?
        boolean refresh = node.isCompleteyConnected()
            && (node.isFriend() || node.hasJoinedAnyFolder() || node.isOnLAN());
        if (refresh) {
            return getAccountInfo(node);
        }
        // Try to return cached
        Session session = sessions.get(node);
        // Cache hit
        if (session != null && CACHE_ENABLED) {
            return session.getAccountInfo();
        }
        // Unknown.
        return null;
    }

    private final class DefaultRefresher implements Runnable {
        private final Member node;

        private DefaultRefresher(Member node) {
            this.node = node;
        }

        public void run() {
            clearNodeCache(node);

            // This is required because of probably changed access
            // permissions to folder.
            node.synchronizeFolderMemberships();
            if (client.isServer(node) && node.isCompleteyConnected()) {
                prefetchAccountInfos();
            }

            AccountInfo aInfo = autoRefresh(node);
            fireNodeAccountStateChanged(node, aInfo);
        }
    }

    private final class MySelfRefrehser implements Runnable {
        private final Member node;

        private MySelfRefrehser(Member node) {
            this.node = node;
        }

        public void run() {
            clearNodeCache(node);
            client.refreshAccountDetails();

            node.synchronizeFolderMemberships();

            AccountInfo aInfo = autoRefresh(node);
            fireNodeAccountStateChanged(node, aInfo);
        }
    }

    private final class DisconnectRefresher implements Runnable {
        private final Member node;

        private DisconnectRefresher(Member node) {
            this.node = node;
        }

        public void run() {
            clearNodeCache(node);
            AccountInfo aInfo = autoRefresh(node);
            fireNodeAccountStateChanged(node, aInfo);
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
