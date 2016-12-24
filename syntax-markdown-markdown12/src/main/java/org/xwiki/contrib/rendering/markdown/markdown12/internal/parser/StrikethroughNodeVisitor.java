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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser;

import java.util.Collections;
import java.util.Deque;

import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ast.NodeVisitor;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;

public class StrikethroughNodeVisitor extends AbstractNodeVisitor
{
    public StrikethroughNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public void visit(Strikethrough node)
    {
        Format format = Format.STRIKEDOUT;
        getListener().beginFormat(format, Collections.EMPTY_MAP);
        getVisitor().visitChildren(node);
        getListener().endFormat(format, Collections.EMPTY_MAP);
    }
}
