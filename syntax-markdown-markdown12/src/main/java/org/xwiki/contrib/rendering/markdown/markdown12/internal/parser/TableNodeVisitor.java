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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.xwiki.rendering.listener.Listener;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.syntax.Syntax;

import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCaption;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

/**
 * Handle table events.
 *
 * @version $Id$
 * @since 8.4
 */
public class TableNodeVisitor extends AbstractNodeVisitor
{
    static <V extends TableNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(TableBlock.class, node -> visitor.visit(node)),
                new VisitHandler<>(TableHead.class, node -> visitor.visit(node)),
                new VisitHandler<>(TableRow.class, node -> visitor.visit(node)),
                new VisitHandler<>(TableCell.class, node -> visitor.visit(node)),
                new VisitHandler<>(TableCaption.class, node -> visitor.visit(node)),
                new VisitHandler<>(TableSeparator.class, node -> visitor.visit(node))
        };
    }

    /**
     * HTML Align attribute for table cells.
     */
    private static final String ALIGN_ATTRIBUTE = "align";

    /**
     * The current table node we're in. It's stacked to support nested tables.
     */
    private Deque<TableBlock> currentTableStack = new ArrayDeque<>();

    /**
     * The current column position in the current table; used to handle colspan. It's stacked to support nested tables.
     */
    private Deque<Integer> currentTableColumnPositionStack = new ArrayDeque<>();

    public TableNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners, PrintRendererFactory plainRendererFactory)
    {
        super(visitor, listeners, plainRendererFactory);
    }

    public void visit(TableBlock node)
    {
        this.currentTableStack.push(node);
        getListener().beginTable(Collections.EMPTY_MAP);
        getVisitor().visitChildren(node);
        getListener().endTable(Collections.EMPTY_MAP);
        this.currentTableStack.pop();
    }

    public void visit(TableHead node)
    {
//        this.isInTableHeaderStack.push(true);
        getVisitor().visitChildren(node);
//        this.isInTableHeaderStack.pop();
    }

    public void visit(TableRow node)
    {
        this.currentTableColumnPositionStack.push(0);
        getListener().beginTableRow(Collections.EMPTY_MAP);
        getVisitor().visitChildren(node);
        getListener().endTableRow(Collections.EMPTY_MAP);
        this.currentTableColumnPositionStack.pop();
    }

    public void visit(TableCell node)
    {
        boolean isInHeader = node.isHeader();

        // Compute cell parameters
        Map<String, String> parameters = new HashMap<>();
        if (node.getSpan() > 1) {
            parameters.put("colspan", "" + node.getSpan());
        }

        if (node.getAlignment() != null) {
            switch (node.getAlignment()) {
                case LEFT:
                    parameters.put(ALIGN_ATTRIBUTE, "left");
                    break;
                case RIGHT:
                    parameters.put(ALIGN_ATTRIBUTE, "right");
                    break;
                case CENTER:
                    parameters.put(ALIGN_ATTRIBUTE, "center");
                    break;
                default:
                    break;
            }
        }

        if (isInHeader) {
            getListener().beginTableHeadCell(parameters);
        } else {
            getListener().beginTableCell(parameters);
        }

        getVisitor().visitChildren(node);

        if (isInHeader) {
            getListener().endTableHeadCell(parameters);
        } else {
            getListener().endTableCell(parameters);
        }
    }

    public void visit(TableCaption node)
    {
        // TODO: XWiki Rendering doesn't support Caption in tables ATM. Add proper support. Also note that the
        // HTML caption tag is supposed to be sent just after the <table> tag and thus the limited solution we have
        // below is probably wrong...
        String captionText = extractText(node);
        getListener().onRawText(String.format("<caption>%s</caption>", captionText), Syntax.HTML_4_01);
    }

    public void visit(TableSeparator node)
    {
        // Just capture table separators and don't do anything with them.
    }
}
