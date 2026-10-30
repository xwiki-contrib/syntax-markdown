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

import java.util.Map;

import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.WrappingListener;

/**
 * Special listener for handling blockquotes and converting them from the Flexmark Model to the XWiki Model.
 *
 * @version $Id$
 * @since 8.4
 */
public class QuoteListener extends WrappingListener
{
    /**
     * Whether a quotation line has been started or not.
     */
    private boolean quotationLineStarted;

    /**
     * Depth of the block containers (lists, quotations, groups, etc.) opened inside the quotation. The headings
     * located inside these containers are not direct children of the quotation.
     */
    private int containerDepth;

    /**
     * Close open quotation lines events.
     */
    public void closeOpenedQuotationLines()
    {
        if (this.quotationLineStarted) {
            endQuotationLine();
        }
        this.quotationLineStarted = false;
    }

    @Override
    public void beginParagraph(Map<String, String> parameters)
    {
        closeOpenedQuotationLines();
        beginQuotationLine();
        this.quotationLineStarted = true;
    }

    @Override
    public void endParagraph(Map<String, String> parameters)
    {
        // Ignore
    }

    @Override
    public void beginHeader(HeaderLevel level, String id, Map<String, String> parameters)
    {
        // A heading starts a new section, which cannot be located inside a quotation line. A heading located inside a
        // block container (e.g. a list item) has its section scoped to that container, and the quotation line in
        // which the container is located must not be closed since this would generate badly nested events.
        if (this.containerDepth == 0) {
            closeOpenedQuotationLines();
        }
        super.beginHeader(level, id, parameters);
    }

    @Override
    public void beginList(ListType type, Map<String, String> parameters)
    {
        this.containerDepth++;
        super.beginList(type, parameters);
    }

    @Override
    public void endList(ListType type, Map<String, String> parameters)
    {
        super.endList(type, parameters);
        this.containerDepth--;
    }

    @Override
    public void beginDefinitionList(Map<String, String> parameters)
    {
        this.containerDepth++;
        super.beginDefinitionList(parameters);
    }

    @Override
    public void endDefinitionList(Map<String, String> parameters)
    {
        super.endDefinitionList(parameters);
        this.containerDepth--;
    }

    @Override
    public void beginQuotation(Map<String, String> parameters)
    {
        this.containerDepth++;
        super.beginQuotation(parameters);
    }

    @Override
    public void endQuotation(Map<String, String> parameters)
    {
        super.endQuotation(parameters);
        this.containerDepth--;
    }

    @Override
    public void beginGroup(Map<String, String> parameters)
    {
        this.containerDepth++;
        super.beginGroup(parameters);
    }

    @Override
    public void endGroup(Map<String, String> parameters)
    {
        super.endGroup(parameters);
        this.containerDepth--;
    }

    @Override
    public void beginTable(Map<String, String> parameters)
    {
        this.containerDepth++;
        super.beginTable(parameters);
    }

    @Override
    public void endTable(Map<String, String> parameters)
    {
        super.endTable(parameters);
        this.containerDepth--;
    }
}
