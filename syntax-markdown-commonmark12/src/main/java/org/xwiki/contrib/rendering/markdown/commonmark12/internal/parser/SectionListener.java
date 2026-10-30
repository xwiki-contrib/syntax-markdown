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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;

import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.listener.WrappingListener;

/**
 * Handles Sections since the notion of Section doesn't exist in Pegdown but XWiki requires it.
 * <p>
 * Sections are scoped to the block container in which their heading is located (the document, a group, a quotation,
 * a list item, a definition term or description, a table cell): the sections opened inside a container are closed
 * when the container ends, and the headings of a container don't close the sections opened outside of it. This is
 * the same behavior as the XWiki 2.1 parser for which, for example, a group contains an independent document.
 *
 * @version $Id: 58db867f738fbf5d876351f7db8b320a67e021d3 $
 * @since 4.5M1
 */
public class SectionListener extends WrappingListener
{
    /**
     * Depth of heading sections that will need to be closed, for the current container.
     */
    private int depth;

    /**
     * Depths of the parent containers, to be restored when the current container ends.
     */
    private final Deque<Integer> parentDepths = new ArrayDeque<>();

    @Override
    public void beginHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        if (level.getAsInt() - 1 < this.depth) {
            closeSections(this.depth - level.getAsInt() + 1);
        }

        super.beginSection(Collections.emptyMap());
        super.beginHeader(level, id, parameters);
        this.depth++;
    }

    @Override
    public void endHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        super.endHeader(level, id, parameters);
    }

    @Override
    public void beginGroup(Map<String, String> parameters)
    {
        super.beginGroup(parameters);
        beginContainer();
    }

    @Override
    public void endGroup(Map<String, String> parameters)
    {
        endContainer();
        super.endGroup(parameters);
    }

    @Override
    public void beginQuotation(Map<String, String> parameters)
    {
        super.beginQuotation(parameters);
        beginContainer();
    }

    @Override
    public void endQuotation(Map<String, String> parameters)
    {
        endContainer();
        super.endQuotation(parameters);
    }

    @Override
    public void beginListItem()
    {
        super.beginListItem();
        beginContainer();
    }

    @Override
    public void endListItem()
    {
        endContainer();
        super.endListItem();
    }

    @Override
    public void beginDefinitionTerm()
    {
        super.beginDefinitionTerm();
        beginContainer();
    }

    @Override
    public void endDefinitionTerm()
    {
        endContainer();
        super.endDefinitionTerm();
    }

    @Override
    public void beginDefinitionDescription()
    {
        super.beginDefinitionDescription();
        beginContainer();
    }

    @Override
    public void endDefinitionDescription()
    {
        endContainer();
        super.endDefinitionDescription();
    }

    @Override
    public void beginTableCell(Map<String, String> parameters)
    {
        super.beginTableCell(parameters);
        beginContainer();
    }

    @Override
    public void endTableCell(Map<String, String> parameters)
    {
        endContainer();
        super.endTableCell(parameters);
    }

    @Override
    public void beginTableHeadCell(Map<String, String> parameters)
    {
        super.beginTableHeadCell(parameters);
        beginContainer();
    }

    @Override
    public void endTableHeadCell(Map<String, String> parameters)
    {
        endContainer();
        super.endTableHeadCell(parameters);
    }

    @Override
    public void endDocument(MetaData metaData)
    {
        closeSections(this.depth);
        super.endDocument(metaData);
    }

    /**
     * Start a new sections scope for the container that has just begun.
     */
    private void beginContainer()
    {
        this.parentDepths.push(this.depth);
        this.depth = 0;
    }

    /**
     * Close the sections opened inside the container that is ending and restore the parent container scope.
     */
    private void endContainer()
    {
        closeSections(this.depth);
        this.depth = this.parentDepths.isEmpty() ? 0 : this.parentDepths.pop();
    }

    private void closeSections(int count)
    {
        for (int i = 0; i < count; i++) {
            super.endSection(Collections.emptyMap());
        }
        this.depth -= count;
    }
}
