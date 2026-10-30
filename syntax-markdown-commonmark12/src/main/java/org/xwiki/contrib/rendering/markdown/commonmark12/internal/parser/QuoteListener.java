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

import java.util.Collections;
import java.util.Map;

import org.xwiki.rendering.listener.HeaderLevel;
import org.xwiki.rendering.listener.ListType;
import org.xwiki.rendering.listener.WrappingListener;

/**
 * Special listener for handling blockquotes and converting them from the Flexmark Model to the XWiki Model.
 * <p>
 * The XWiki Model only supports inline content in quotation lines (see
 * <a href="http://jira.xwiki.org/browse/XRENDERING-259">XRENDERING-259</a>). Thus:
 * <ul>
 * <li>the paragraphs located directly in the quotation are converted into quotation lines,</li>
 * <li>the other blocks (lists, tables, groups, standalone macros, etc.) located directly in the quotation are wrapped
 * in a Group (which contains an independent document) located in its own quotation line, consecutive blocks sharing
 * the same Group, as the XHTML parser does,</li>
 * <li>nested quotations are located in the current quotation line, as the XWiki 2.1 parser does,</li>
 * <li>the content of the block containers located inside the quotation (e.g. the paragraphs of a Group) is left
 * untouched.</li>
 * </ul>
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
     * Whether a Group wrapping blocks located directly in the quotation has been started or not.
     */
    private boolean blockGroupStarted;

    /**
     * Depth of the block containers (lists, quotations, groups, etc.) opened inside the quotation. The blocks
     * located inside these containers are not direct children of the quotation.
     */
    private int containerDepth;

    /**
     * Close open quotation lines events.
     */
    public void closeOpenedQuotationLines()
    {
        closeOpenedBlockGroup();
        if (this.quotationLineStarted) {
            endQuotationLine();
        }
        this.quotationLineStarted = false;
    }

    private void closeOpenedBlockGroup()
    {
        if (this.blockGroupStarted) {
            super.endGroup(Collections.emptyMap());
        }
        this.blockGroupStarted = false;
    }

    /**
     * Called before a block that is not allowed in a quotation line is started. When the block is located directly in
     * the quotation, make sure it's wrapped in a Group located in a quotation line.
     */
    private void beginBlock()
    {
        if (this.containerDepth == 0 && !this.blockGroupStarted) {
            closeOpenedQuotationLines();
            beginQuotationLine();
            this.quotationLineStarted = true;
            super.beginGroup(Collections.emptyMap());
            this.blockGroupStarted = true;
        }
    }

    @Override
    public void beginParagraph(Map<String, String> parameters)
    {
        if (this.containerDepth == 0) {
            closeOpenedQuotationLines();
            beginQuotationLine();
            this.quotationLineStarted = true;
        } else {
            // A paragraph located in a block container (e.g. a Group) inside the quotation is kept.
            super.beginParagraph(parameters);
        }
    }

    @Override
    public void endParagraph(Map<String, String> parameters)
    {
        if (this.containerDepth > 0) {
            super.endParagraph(parameters);
        }
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
    public void onMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        if (!isInline) {
            beginBlock();
        }
        super.onMacro(id, parameters, content, isInline);
    }

    @Override
    public void onHorizontalLine(Map<String, String> parameters)
    {
        beginBlock();
        super.onHorizontalLine(parameters);
    }

    @Override
    public void beginList(ListType type, Map<String, String> parameters)
    {
        beginBlock();
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
        beginBlock();
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
        // A nested quotation is located in the current quotation line (if any), as the XWiki 2.1 parser does, and not
        // in the Group wrapping the blocks preceding it.
        if (this.containerDepth == 0) {
            closeOpenedBlockGroup();
        }
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
        beginBlock();
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
        beginBlock();
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
