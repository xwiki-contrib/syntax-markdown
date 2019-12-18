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

import java.util.Deque;

import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.parser.StreamParser;

import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.ast.Visitor;

/**
 * Handle text events.
 *
 * @version $Id$
 * @since 8.4
 */
public class TextNodeVisitor extends AbstractNodeVisitor
{
    static <V extends TextNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(Text.class, new Visitor<Text>()
                {
                    @Override
                    public void visit(Text node)
                    {
                        visitor.visit(node);
                    }
                })
        };
    }

    public TextNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners, StreamParser plainTextStreamParser)
    {
        super(visitor, listeners, null, plainTextStreamParser);
    }

    public void visit(Text node)
    {
        parseInline(node.getChars().toString());

        // Descend into children (could be omitted in this case because Text nodes don't have children).
        getVisitor().visitChildren(node);
    }
}
