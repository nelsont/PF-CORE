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
package de.dal33t.powerfolder.net;

import de.dal33t.powerfolder.Constants;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.message.Ping;
import de.dal33t.powerfolder.security.SecurityManager;
import de.dal33t.powerfolder.util.NamedThreadFactory;
import de.dal33t.powerfolder.util.Range;
import de.dal33t.powerfolder.util.Reject;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides basic IO stuff.
 * 
 * @author <a href="mailto:sprajc@riege.com">Christian Sprajc</a>
 * @version $Revision: 1.5 $
 */
public class IOProvider extends PFComponent {

    private static final Logger log = Logger.getLogger(IOProvider.class.getName());
    private static final long CONNECTION_KEEP_ALIVE_TIMOUT_MS = Constants.CONNECTION_KEEP_ALIVE_TIMOUT * 1000L;
    private static final long TIME_WITHOUT_KEEPALIVE_UNTIL_PING = CONNECTION_KEEP_ALIVE_TIMOUT_MS / 3L;

    /**
     * The threadpool executing the basic I/O connections to the nodes.
     */
    private ExecutorService ioThreadPool;

    /**
     * The connection handler factory.
     */
    private ConnectionHandlerFactory conHanFactory;

    /**
     * Manager of relayed connection
     */
    private RelayedConnectionManager relayedConManager;

    /**
     * Manager of UDT socket connections
     */
    private UDTSocketConnectionManager udtConManager;

    /**
     * The list of connection handlers to check for keepalive
     */
    private List<ConnectionHandler> keepAliveList;

    private boolean started;

    public IOProvider(Controller controller) {
        super(controller);
        // Create default connection factory. not set this in
        conHanFactory = new ConnectionHandlerFactory(controller);
        keepAliveList = new CopyOnWriteArrayList<ConnectionHandler>();
        relayedConManager = new RelayedConnectionManager(controller);
        udtConManager = new UDTSocketConnectionManager(controller, Range
            .getRangeByNumbers(1024, 65535));
    }

    public void start() {
        // For basic IO
        ioThreadPool = Executors.newCachedThreadPool(new NamedThreadFactory(
            "IOThread-"));
        started = true;
        startIO(new KeepAliveChecker());
        relayedConManager.start();
    }

    public void shutdown() {
        started = false;
        if (ioThreadPool != null) {
            log.fine("Shutting down connection I/O threadpool");
            ioThreadPool.shutdownNow();
        }
    }

    /**
     * Sets the connection handler factory, which is responsible for creating
     * connection handler for basic io.
     * 
     * @param conHanFactory
     *            the new factory.
     */
    public synchronized void setConnectionHandlerFactory(
        ConnectionHandlerFactory conHanFactory)
    {
        Reject.ifNull(conHanFactory, "The factory must not be null");
        log.finer("Setting new connection factory: " + conHanFactory);
        this.conHanFactory = conHanFactory;
    }

    /**
     * @return the connection handler factory to create connection handler with.
     */
    public ConnectionHandlerFactory getConnectionHandlerFactory() {
        return conHanFactory;
    }

    /**
     * @return the relayed connection manager.
     */
    public RelayedConnectionManager getRelayedConnectionManager() {
        return relayedConManager;
    }

    public UDTSocketConnectionManager getUDTSocketConnectionManager() {
        return udtConManager;
    }

    /**
     * Starts the sender and receiver IO in the global threadpool.
     * 
     * @param ioSender
     *            the io sender
     * @param ioReceiver
     *            the io receiver
     */
    public void startIO(Runnable ioSender, Runnable ioReceiver) {
        Reject.ifNull(ioSender, "IO Sender is null");
        Reject.ifNull(ioReceiver, "IO Receiver is null");
        if (log.isLoggable(Level.FINER)) {
            log.finer("Starting IO for " + ioSender + " " + ioReceiver);
        }
        startIO(ioSender);
        startIO(ioReceiver);
    }

    /**
     * Starts a general connection handling working.
     * 
     * @param ioWorker
     *            a io worker
     */
    public void startIO(final Runnable ioWorker) {
        Reject.ifNull(ioWorker, "IO Worker is null");
        if (ioThreadPool.isTerminated() || ioThreadPool.isShutdown()) {
            log.warning("Rejected executing of ioWorker, already stopped: "
                + ioWorker);
            return;
        }
        if (log.isLoggable(Level.FINER)) {
            log.finer("Starting IO for " + ioWorker);
        }
        // Ensure clean security context. Unsure destructed session.
        Runnable sessionDestroyer = new SessionDestroyerRunnable(ioWorker);
        ioThreadPool.submit(sessionDestroyer);
    }

    /**
     * Adds this connection handler to get checked for keepalive. If the
     * connection handler times out is gets shut down.
     * 
     * @param conHan
     *            the connection handler to check
     */
    public void startKeepAliveCheck(ConnectionHandler conHan) {
        Reject.ifNull(conHan, "Connection handler is null");
        if (!conHan.isConnected()) {
            return;
        }
        keepAliveList.add(conHan);
    }

    /**
     * Removes this connection handler to get checked for keepalive.
     * 
     * @param conHan
     *            the connection handler to remove
     */
    public void removeKeepAliveCheck(ConnectionHandler conHan) {
        Reject.ifNull(conHan, "Connection handler is null");
        keepAliveList.remove(conHan);
    }

    /**
     * Class to wrap a runnable, especially IO receivers, which ensures a clean
     * security context on the executing thread. Destroys session before run()
     * is executed.
     */
    private final class SessionDestroyerRunnable implements Runnable {
        private final Runnable ioWorker;

        private SessionDestroyerRunnable(Runnable ioWorker) {
            this.ioWorker = ioWorker;
        }

        public void run() {
            try {
                destroySession();
                ioWorker.run();
            } catch (RuntimeException e) {
                log.log(Level.SEVERE, "RuntimeError in IO worker", e);
                throw e;
            } finally {
                destroySession();
            }
        }

        private void destroySession() {
            SecurityManager secMan = getController().getSecurityManager();
            if (secMan != null) {
                secMan.destroySession();
            }
        }
    }

    private class KeepAliveChecker implements Runnable {

        public void run() {
            if (!started) {
                return;
            }
            while (started) {
                if (log.isLoggable(Level.FINE)) {
                    log.fine("Checking " + keepAliveList.size()
                        + " con handlers for keepalive");
                }
                Collection<ConnectionHandler> list = new HashSet<ConnectionHandler>(
                    keepAliveList);
                if (getController().getNodeManager() != null) {
                    Collection<Member> nodes = getController().getNodeManager()
                        .getNodesAsCollection();
                    // Might happen on startup
                    if (nodes != null) {
                        for (Member node : nodes) {
                            ConnectionHandler peer = node.getPeer();
                            if (peer == null) {
                                continue;
                            }
                            if (!peer.isConnected()) {
                                continue;
                            }
                            if (!list.contains(peer)) {
                                log.severe("ConHan not in keepalive list of "
                                    + node);
                                list.add(peer);
                            }
                        }
                    }
                }

                for (ConnectionHandler conHan : list) {
                    if (!conHan.isConnected()) {
                        keepAliveList.remove(conHan);
                    }
                    if (!checkIfOk(conHan)) {
                        keepAliveList.remove(conHan);
                    }
                }
                try {
                    Thread.sleep(TIME_WITHOUT_KEEPALIVE_UNTIL_PING);
                } catch (InterruptedException e) {
                    log.log(Level.FINER, "InterruptedException", e);
                    return;
                }
            }
        }

        private boolean checkIfOk(ConnectionHandler conHan) {
            boolean newPing;
            Date lastKeepaliveMessage = conHan.getLastKeepaliveMessageTime();
            if (lastKeepaliveMessage == null) {
                newPing = true;
            } else {
                long timeWithoutKeepalive = System.currentTimeMillis()
                    - lastKeepaliveMessage.getTime();
                newPing = timeWithoutKeepalive >= TIME_WITHOUT_KEEPALIVE_UNTIL_PING;
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Keep-alive check. Received last keep alive message "
                        + timeWithoutKeepalive
                        + "ms ago, ping required? "
                        + newPing + ". Node: " + conHan.getMember());
                }
                if (timeWithoutKeepalive > CONNECTION_KEEP_ALIVE_TIMOUT_MS) {
                    log.warning("Shutting down. Dead connection detected ("
                        + (timeWithoutKeepalive / 1000) + "s timeout) to "
                        + conHan.getMember());
                    conHan.shutdownWithMember();
                    return false;
                }
            }
            if (newPing) {
                // Send new ping
                conHan.sendMessagesAsynchron(new Ping(-1));
            }
            return true;
        }
    }
}
