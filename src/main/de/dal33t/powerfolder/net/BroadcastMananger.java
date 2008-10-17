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

import de.dal33t.powerfolder.ConfigurationEntry;
import de.dal33t.powerfolder.Controller;
import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.PFComponent;
import de.dal33t.powerfolder.util.Reject;
import de.dal33t.powerfolder.util.Util;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener, which listens for incoming broadcast messages
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.19 $
 */
public class BroadcastMananger extends PFComponent implements Runnable {

    private static final Logger log = Logger.getLogger(BroadcastMananger.class.getName());
    private static final int DEFAULT_BROADCAST_PORT = 1337;
    private static final int IN_BUFFER_SIZE = 128;

    private InetAddress subnetIP;
    private InetAddress group;
    private MulticastSocket socket;
    private DatagramSocket[] senderSockets;
    private String broadCastString;
    private Thread myThread;
    private long waitTime;
    private ArrayList<InetAddress> localAddresses;
    private ArrayList<InetAddress> oldLocalAddresses;
    private ArrayList<NetworkInterface> localNICList;
    private Collection<InetAddress> receivedBroadcastsFrom;

    /**
     * Builds a new broadcast listener
     * 
     * @param controller
     * @throws ConnectionException
     */
    public BroadcastMananger(Controller controller) throws ConnectionException {
        super(controller);
        try {
            subnetIP = InetAddress.getLocalHost();

            localNICList = new ArrayList<NetworkInterface>();
            localAddresses = new ArrayList<InetAddress>();
            receivedBroadcastsFrom = new ArrayList<InetAddress>();

            waitTime = Controller.getWaitTime() * 3;
            group = InetAddress.getByName("224.0.0.1");

            if (controller.hasConnectionListener()) {
                String id = controller.getMySelf().getId();
                if (id.indexOf('[') >= 0 || id.indexOf(']') >= 0) {
                    throw new IllegalArgumentException(
                        "Node id contains illegal characters: " + id);
                }

                // build broadcast string
                broadCastString = "PowerFolder node: ["
                    + controller.getConnectionListener().getAddress().getPort()
                    + "]-[" + id + "]";
            }
        } catch (IOException e) {
            throw new ConnectionException("Unable to open broadcast socket", e);
        }
    }

    /**
     * Starts the manager
     * 
     * @throws ConnectionException
     *             if the broadcast manager could not be initalized.
     */
    public void start() throws ConnectionException {
        try {
            // open multicast socket
            socket = new MulticastSocket(DEFAULT_BROADCAST_PORT);

            InetAddress bindAddr;
            String bindIP = ConfigurationEntry.NET_BIND_ADDRESS
                .getValue(getController());
            if (!StringUtils.isEmpty(bindIP)) {
                bindAddr = InetAddress.getByName(bindIP);
            } else {
                // TRAC #466 - Windows Vista
                bindAddr = InetAddress.getLocalHost();
            }

            log.finer("Binding multicast on address: " + bindAddr);
            socket.setInterface(bindAddr);
            socket.setSoTimeout((int) waitTime);
            socket.joinGroup(group);
            socket.setTimeToLive(65);
            /*
             * log.finer( "Opened broadcast manager on nic: " +
             * socket.getNetworkInterface());
             */
            socket.setBroadcast(true);
        } catch (IOException e) {
            throw new ConnectionException("Unable to open broadcast socket", e);
        }

        myThread = new Thread(this, "Subnet broadcast manager, port "
            + socket.getLocalPort());
        myThread.setPriority(Thread.MIN_PRIORITY);
        myThread.start();
        log.fine("Started");

        if (getController().getConnectionListener() != null) {
            getController().scheduleAndRepeat(new TimerTask() {
                @Override
                public void run() {
                    if (socket == null || socket.isClosed()) {
                        return;
                    }
                    if (broadCastString == null) {
                        log.warning("Not sending network broadcast");
                        return;
                    }
                    getController().getIOProvider().startIO(
                        new BroadcastSender());
                }
            }, 10L * 1000);
        }
    }

    /**
     * Shuts the manager down
     */
    public void shutdown() {
        if (myThread != null) {
            myThread.interrupt();
        }
        if (socket != null) {
            socket.close();
        }
        log.fine("Stopped");
    }

    public void run() {
        // check subnet ip
        if (subnetIP == null) {
            return;
        }

        // preparing receiving packet
        byte[] inBuffer = new byte[IN_BUFFER_SIZE];
        DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                // received new packet
                socket.receive(inPacket);

                if (isPowerFolderBroadcast(inPacket)) {
                    processBroadcast(inPacket);
                }
            } catch (SocketTimeoutException e) {
                // ignore
            } catch (IOException e) {
                log.log(Level.FINER, "Closing broadcastmanager", e);
                break;
            }
        }

        // cleanup
        if (socket != null) {
            socket.close();
        }

        if (senderSockets != null) {
            for (int i = 0; i < senderSockets.length; i++) {
                if (senderSockets[i] != null) {
                    senderSockets[i].close();
                }
            }
        }
    }

    /**
     * @param addr
     *            the remove address
     * @return true if a broadcast has been received on this address
     */
    public boolean receivedBroadcast(InetAddress addr) {
        Reject.ifNull(addr, "Address is null");
        return receivedBroadcastsFrom.contains(addr);
    }

    /**
     * Sends a broadcast throu the broadcast sockets
     * 
     * @param broadcast
     *            the packet to send
     */
    private void sendBroadcast(DatagramPacket broadcast) {
        // send broadcast set
        if (log.isLoggable(Level.FINER)) {
            log.finer("Sending broadcast: " + broadCastString);
        }
        for (int i = 0; i < senderSockets.length; i++) {
            if (senderSockets[i] != null) {
                try {
                    senderSockets[i].send(broadcast);
                } catch (IOException e) {
                    log.log(Level.FINER,
                        "Removing socket from future sendings. "
                            + senderSockets[i], e);
                    senderSockets[i].close();
                    senderSockets[i] = null;
                }
            }
        }
    }

    /**
     * Answers if this packet is a powerfolder broadcast message
     * 
     * @param packet
     * @return
     */
    private boolean isPowerFolderBroadcast(DatagramPacket packet) {
        if (packet == null) {
            throw new NullPointerException("Packet is null");
        }
        if (packet.getData() == null) {
            throw new NullPointerException("Packet data is null");
        }

        String message = new String(packet.getData());
        return message.startsWith("PowerFolder node");
    }

    /**
     * Triies to connect to a node, parsed from a broadcast message
     * 
     * @param packet
     * @return
     */
    private boolean processBroadcast(DatagramPacket packet) {
        if (packet == null) {
            throw new NullPointerException("Packet is null");
        }
        if (packet.getData() == null) {
            throw new NullPointerException("Packet data is null");
        }
        byte[] content = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, content, 0, content.length);

        String message = new String(content);

        if (log.isLoggable(Level.FINER)) {
            log.finer(
                "Received broadcast: " + message + ", " + packet.getAddress());
        }

        int port;
        String id;

        int bS = message.indexOf('[');
        int bE = message.indexOf(']');

        if (bS > 0 && bE > 0) {
            String portStr = message.substring(bS + 1, bE);
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                log.finer("Unable to parse port from broadcast message");
                return false;
            }
        } else {
            return false;
        }

        bS = message.indexOf('[', bE + 1);
        bE = message.indexOf(']', bE + 1);
        if (bS > 0 && bE > 0) {
            id = message.substring(bS + 1, bE);
        } else {
            return false;
        }

        InetSocketAddress address = new InetSocketAddress(packet.getAddress(),
            port);
        Member node = getController().getNodeManager().getNode(id);
        if (node == null || (!node.isMySelf() && !node.isConnected())) {
            receivedBroadcastsFrom.add(packet.getAddress());
            log.info(
                "Found user on local network: " + address
                    + ((node != null) ? ", " + node : ""));
            try {
                if (getController().isStarted()) {
                    // found another new node!!!
                    getController().connect(address);
                    return true;
                }

            } catch (ConnectionException e) {
                log.warning("Unable to connect to node on subnet: " + address
                    + ": " + e);
                log.log(Level.FINER, "ConnectionException", e);
            }
        } else {
            if (log.isLoggable(Level.FINER)) {
                log.finer("Node already known: ID: " + id + ", " + node);
            }
            // Node must be on lan
            node.setOnLAN(true);
        }

        return false;
    }

    /**
     * compares the two lists of inet addresses, the new and the previous one,
     * for differences. Note that they are treated as different if they are
     * equal modulo permutations.
     * 
     * @param addrListNew
     * @param addrListOld
     * @return true if different, false otherwise.
     */
    private boolean compareLocalAddresses(ArrayList<InetAddress> addrListNew,
        ArrayList<InetAddress> addrListOld)
    {

        if (addrListOld == null) {
            return true;
        }

        int addrsize = addrListNew.size();

        if (addrsize != addrListOld.size()) {
            return true;
        }

        for (int i = 0; i < addrsize; i++) {
            InetAddress addr1 = addrListNew.get(i);
            InetAddress addr2 = addrListOld.get(i);

            if (Util.compareIpAddresses(addr1.getAddress(), addr2.getAddress()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * checks for added or removed net interfaces and updates our internal list
     * of sender sockets.
     */
    @SuppressWarnings("unchecked")
    private void updateSenderSockets() {

        updateLocalAddresses();

        if (compareLocalAddresses(localAddresses, oldLocalAddresses)) {
            log.fine("NetworkInterfaces initialiazing or change detected");

            InetAddress[] inet = new InetAddress[localAddresses.size()];
            localAddresses.toArray(inet);

            if (senderSockets != null) {
                for (int i = 0; i < senderSockets.length; i++) {
                    if (senderSockets[i] != null) {
                        try {
                            log.fine("closing socket");
                            senderSockets[i].close();
                        } catch (Exception e) {
                            log.log(Level.SEVERE, "closing socket", e);
                        }
                    }
                }
            }
            senderSockets = new DatagramSocket[inet.length];
            for (int i = 0; i < inet.length; i++) {
                try {
                    senderSockets[i] = new DatagramSocket(0, inet[i]);
                    if (log.isLoggable(Level.FINER)) {
                        log.finer(
                            "Successfully opened broadcast sender for "
                                + inet[i]);
                    }

                } catch (IOException e) {
                    if (log.isLoggable(Level.FINER)) {
                        log.finer(
                            "Unable to open broadcast sender for " + inet[i]
                                + ": " + e.getMessage());
                    }
                    senderSockets[i] = null;
                }
            }
            oldLocalAddresses = (ArrayList<InetAddress>) localAddresses.clone();
        }
    }

    /**
     * updates the internal list of local addresses.
     */
    private void updateLocalAddresses() {
        updateNetworkInterfaces();
        localAddresses.clear();

        String cfgBind = ConfigurationEntry.NET_BIND_ADDRESS
            .getValue(getController());
        if (cfgBind != null && cfgBind.length() > 0)
            try {
                localAddresses.add(InetAddress.getByName(cfgBind));
            } catch (UnknownHostException e) {
                log.log(Level.SEVERE,
                    "Warning, \"net.bindaddress\" is NOT an IP address!", e);
            }
        else {
            for (int i = 0; i < localNICList.size(); i++) {
                NetworkInterface ni = localNICList.get(i);
                Enumeration<InetAddress> en = ni.getInetAddresses();
                while (en.hasMoreElements()) {
                    localAddresses.add(en.nextElement());
                }
            }
        }
    }

    /**
     * Gets all network interfaces, which are connected to local nets and
     * updates the internal network interfaces list.
     */

    private void updateNetworkInterfaces() {
        Enumeration<NetworkInterface> en;

        // clears the previos content of the network interfaces list
        localNICList.clear();

        try {
            en = NetworkInterface.getNetworkInterfaces();

        } catch (SocketException e) {
            log.severe("Unable to get local network interfaces");
            log.log(Level.FINER, "SocketException", e);
            return;
        }

        while (en.hasMoreElements()) {
            NetworkInterface ni = en.nextElement();
            localNICList.add(ni);
        }
    }

    private class BroadcastSender implements Runnable {
        public void run() {
            DatagramPacket broadcast = null;
            byte[] msg = broadCastString.getBytes();
            broadcast = new DatagramPacket(msg, msg.length, group,
                DEFAULT_BROADCAST_PORT);
            if (log.isLoggable(Level.FINER)) {
                log.finer("Sending network broadcast");
            }
            // check for added or removed net interfaces
            // and update our internal list of sender sockets
            updateSenderSockets();
            sendBroadcast(broadcast);
        }
    }
}