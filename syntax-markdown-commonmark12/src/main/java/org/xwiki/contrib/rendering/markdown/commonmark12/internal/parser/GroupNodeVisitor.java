/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.parser;

import java.util.Deque;

import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

/**
 * Handle Group events.
 *
 * @version $Id$
 * @since 8.9.2
 */
public class GroupNodeVisitor extends AbstractNodeVisitor
{
    /**
     * @param visitor the visitor to use to visit the Group children
     * @param listeners the stack of listeners to which to send the events
     */
    public GroupNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    /**
     * @return the handler to register in the node visitor
     */
    public VisitHandler<GroupBlock> getVisitHandler()
    {
        return new VisitHandler<>(GroupBlock.class, this::visit);
    }

    /**
     * @param node the Group node to visit
     */
    public void visit(GroupBlock node)
    {
        getListener().beginGroup(node.getParameters());
        getVisitor().visitChildren(node);
        getListener().endGroup(node.getParameters());
    }
}
