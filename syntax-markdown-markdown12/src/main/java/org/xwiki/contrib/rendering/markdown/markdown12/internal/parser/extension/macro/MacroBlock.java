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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.extension.macro;

import java.util.List;

import com.vladsch.flexmark.ast.Block;
import com.vladsch.flexmark.util.sequence.BasedSequence;

public class MacroBlock extends Block
{
    private BasedSequence openingMarker = BasedSequence.NULL;

    private BasedSequence info = BasedSequence.NULL;

    private BasedSequence closingMarker = BasedSequence.NULL;

    public MacroBlock()
    {
    }

    public MacroBlock(BasedSequence chars)
    {
        super(chars);
    }

    public MacroBlock(BasedSequence chars, BasedSequence openingMarker, BasedSequence info,
        List<BasedSequence> segments, BasedSequence closingMarker)
    {
        super(chars, segments);
        this.openingMarker = openingMarker;
        this.info = info;
        this.closingMarker = closingMarker;
    }

    @Override
    public void getAstExtra(StringBuilder out)
    {
        BasedSequence content = getContentChars();
        int lines = getContentLines().size();
        segmentSpanChars(out, openingMarker, "open");
        segmentSpanChars(out, info, "info");
        segmentSpan(out, content, "content");
        out.append(" lines[").append(lines).append("]");
        segmentSpanChars(out, closingMarker, "close");
    }

    @Override
    public BasedSequence[] getSegments()
    {
        return new BasedSequence[]{ openingMarker, info, getContentChars(), closingMarker };
    }

    public BasedSequence getOpeningMarker()
    {
        return openingMarker;
    }

    public void setOpeningMarker(BasedSequence openingMarker)
    {
        this.openingMarker = openingMarker;
    }

    public BasedSequence getClosingMarker()
    {
        return closingMarker;
    }

    public void setClosingMarker(BasedSequence closingMarker)
    {
        this.closingMarker = closingMarker;
    }

    /**
     * @return the sequence for the info part of the node
     * @see <a href="http://spec.commonmark.org/0.18/#info-string">CommonMark spec</a>
     */
    public BasedSequence getInfo()
    {
        return info;
    }

    public void setInfo(BasedSequence info)
    {
        this.info = info;
    }
}
