/* $Id: RequestNodeList.java,v 1.1 2005/05/07 19:56:51 totmacherr Exp $
 */
package de.dal33t.powerfolder.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import de.dal33t.powerfolder.Member;
import de.dal33t.powerfolder.light.MemberInfo;
import de.dal33t.powerfolder.util.Reject;

/**
 * A request to send the nodelist. Expected answer is a <code>KnownNodes</code>
 * message.
 * <p>
 * TODO Add filter criteria for folders.
 * 
 * @see de.dal33t.powerfolder.message.KnownNodes
 * @see de.dal33t.powerfolder.message.SearchNodeRequest
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.1 $
 */
public class RequestNodeList extends Message {
    private static final long serialVersionUID = 101L;

    /**
     * The criteria for getting the supernodes infos.
     */
    public static enum NodesCriteria {
        ALL, ONLINE, NONE
    }

    /**
     * What supernodes information should be returned.
     */
    private NodesCriteria supernodesCriteria;

    /**
     * What other nodes information should be returned, additionally match
     * through nodeId list.
     */
    private NodesCriteria nodesCriteria;

    /**
     * The list of node ids for which we want to request fresh connection
     * information. May be null. Usually the ids of the friends.
     */
    private Collection<String> nodeIds;

    /**
     * Constructs a new request.
     */
    private RequestNodeList(Collection<String> mIds,
        NodesCriteria supernodesCrit, NodesCriteria normalNodesCrit)
    {
        Reject.ifNull(normalNodesCrit, "Nodes criteria is null");
        Reject.ifNull(supernodesCrit, "Supernodes criteria is null");
        supernodesCriteria = supernodesCrit;
        nodesCriteria = normalNodesCrit;
        nodeIds = mIds;
    }

    // Creation methods *******************************************************

    /**
     * Creates a request nodes message, which requests all node infomations.
     * 
     * @return the request message
     */
    public static RequestNodeList createRequestAllNodes() {
        return new RequestNodeList(null, NodesCriteria.ALL, NodesCriteria.ALL);
    }

    /**
     * Creates a request nodes message, which requests nodes information of the
     * given nodes. Can additionally obtain information about supernodes.
     * 
     * @param nodes
     *            the nodes to request new information for
     * @param supernodesCriteria
     *            the supernodes criteria.
     * @param normalNodesCriteria
     *            the criteria for normal nodes in not in id list.
     * @return the message to sent
     */
    public static RequestNodeList createRequest(Collection<Member> nodes,
        NodesCriteria supernodesCriteria, NodesCriteria normalNodesCriteria)
    {
        Reject.ifNull(nodes, "Nodes is null");
        Collection<String> mIds = new HashSet<String>(nodes.size());
        for (Member node : nodes) {
            mIds.add(node.getId());
        }
        return new RequestNodeList(mIds, supernodesCriteria,
            normalNodesCriteria);
    }

    // API ********************************************************************

    /**
     * Filter the collection of nodes with the criterias of this request.
     * 
     * @param source
     *            the list of nodes
     * @return the filtered list of node infos
     */
    public List<MemberInfo> filter(Collection<Member> source) {
        List<MemberInfo> nodes = new ArrayList<MemberInfo>();
        for (Member node : source) {
            if (matches(node)) {
                nodes.add(node.getInfo());
            }
        }
        return nodes;
    }

    // Helper *****************************************************************

    private boolean matches(Member node) {
        // "general" nodes criteria match
        if (NodesCriteria.ALL.equals(nodesCriteria)) {
            return true;
        }
        if (NodesCriteria.ONLINE.equals(nodesCriteria)
            && node.isConnectedToNetwork())
        {
            return true;
        }
        // Supernode match
        if (NodesCriteria.ALL.equals(supernodesCriteria) && node.isSupernode())
        {
            return true;
        }
        if (NodesCriteria.ONLINE.equals(supernodesCriteria)
            && node.isSupernode() && node.isConnectedToNetwork())
        {
            return true;
        }
        // Node id list match
        if (nodeIds != null && nodeIds.contains(node.getId())) {
            return true;
        }
        return false;
    }

    // General ****************************************************************

    public String toString() {
        return "Request for NodeList (supernodes: " + supernodesCriteria
            + ", normal-nodes: " + nodesCriteria + ", "
            + (nodeIds == null ? "all" : Integer.valueOf(nodeIds.size()))
            + " nodes)";
    }
}