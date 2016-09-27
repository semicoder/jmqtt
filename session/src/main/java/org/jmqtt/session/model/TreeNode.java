/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.jmqtt.session.model;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

class TreeNode {

    Token token;
    List<TreeNode> children = new ArrayList<>();
    Set<ClientTopicCouple> clients = new HashSet<>();

    TreeNode() {
    }

    Token getToken() {
        return token;
    }

    void setToken(Token topic) {
        this.token = topic;
    }

    void addClient(ClientTopicCouple s) {
        clients.add(s);
    }

    void addChild(TreeNode child) {
        children.add(child);
    }

    /**
     * Creates a shallow copy of the current node.
     * Copy the token and the children.
     */
    TreeNode copy() {
        final TreeNode copy = new TreeNode();
        copy.children = new ArrayList<>(children);
        copy.clients = new HashSet<>(clients);
        copy.token = token;
        return copy;
    }

    /**
     * Search for children that has the specified token, if not found return
     * null;
     */
    TreeNode childWithToken(Token token) {
        for (TreeNode child : children) {
            if (child.getToken().equals(token)) {
                return child;
            }
        }

        return null;
    }

    void updateChild(TreeNode oldChild, TreeNode newChild) {
        children.remove(oldChild);
        children.add(newChild);
    }

    Collection<ClientTopicCouple> getClients() {
        return clients;
    }

    public void remove(ClientTopicCouple clientTopicCouple) {
        clients.remove(clientTopicCouple);
    }

    void matches(Queue<Token> tokens, List<ClientTopicCouple> matchClients) {
        Token t = tokens.poll();

        //check if t is null <=> tokens finished
        if (t == null) {
            matchClients.addAll(clients);
            //check if it has got a MULTI child and add its getClients
            for (TreeNode n : children) {
                if (n.getToken() == Token.MULTI || n.getToken() == Token.SINGLE) {
                    matchClients.addAll(n.getClients());
                }
            }

            return;
        }

        //we are on MULTI, than add getClients and return
        if (token == Token.MULTI) {
            matchClients.addAll(clients);
            return;
        }

        for (TreeNode n : children) {
            if (n.getToken().match(t)) {
                //Create a copy of token, else if navigate 2 sibling it
                //consumes 2 elements on the queue instead of one
                n.matches(new LinkedBlockingQueue<>(tokens), matchClients);
            }
        }
    }

    /**
     * Return the number of registered getClients
     */
    int size() {
        int res = clients.size();
        for (TreeNode child : children) {
            res += child.size();
        }
        return res;
    }

    /**
     * Create a copied subtree rooted on this node but purged of clientId's getClients.
     */
    TreeNode removeClientSubscriptions(String clientID) {
        //collect what to delete and then delete to avoid ConcurrentModification
        TreeNode newSubRoot = this.copy();
        List<ClientTopicCouple> subsToRemove = new ArrayList<>();
        for (ClientTopicCouple s : newSubRoot.clients) {
            if (s.clientId.equals(clientID)) {
                subsToRemove.add(s);
            }
        }

        for (ClientTopicCouple s : subsToRemove) {
            newSubRoot.clients.remove(s);
        }

        //go deep
        List<TreeNode> newChildren = new ArrayList<>(newSubRoot.children.size());
        for (TreeNode child : newSubRoot.children) {
            newChildren.add(child.removeClientSubscriptions(clientID));
        }
        newSubRoot.children = newChildren;
        return newSubRoot;
    }
}
