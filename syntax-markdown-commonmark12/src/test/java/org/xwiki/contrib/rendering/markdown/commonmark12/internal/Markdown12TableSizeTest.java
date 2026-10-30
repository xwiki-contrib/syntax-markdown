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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal;

import java.io.StringReader;

import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.wiki.WikiModel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.mockito.MockitoComponentManagerRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verify that the size of the Markdown 1.2 rendering of a table stays proportional to the size of the table, i.e. that
 * the padding of the rows and cells (so that the table is valid and its columns aligned) doesn't make it quadratic.
 *
 * @version $Id$
 */
@AllComponents
public class Markdown12TableSizeTest
{
    /**
     * The maximum ratio between the size of the rendered Markdown and the size of the XWiki Syntax input.
     */
    private static final int MAX_RATIO = 30;

    private static final int COUNT = 1000;

    @Rule
    public MockitoComponentManagerRule mocker = new MockitoComponentManagerRule();

    @BeforeComponent
    public void setUpComponents() throws Exception
    {
        this.mocker.registerMockComponent(WikiModel.class);
    }

    @Test
    public void wideRowFollowedByShortRows() throws Exception
    {
        // A header row of many cells, followed by many rows of a single cell.
        String input = StringUtils.repeat("|=h", COUNT) + "\n" + StringUtils.repeat("|x\n", COUNT);
        String markdown = assertRenderingSize(input);

        // The short rows are not padded with empty cells.
        assertTrue(markdown.endsWith("\n|  x  |"));
    }

    @Test
    public void wideRowFollowedByShortRowsWithoutHeader() throws Exception
    {
        assertRenderingSize(StringUtils.repeat("|a", COUNT) + "\n" + StringUtils.repeat("|x\n", COUNT));
    }

    @Test
    public void largeCellFollowedByShortRows() throws Exception
    {
        // A large cell followed by many small cells in the same column.
        String input = "|=" + StringUtils.repeat('a', COUNT * 10) + "\n" + StringUtils.repeat("|x\n", COUNT);
        String markdown = assertRenderingSize(input);

        // The small cells are not padded to the size of the large cell.
        assertTrue(markdown.endsWith("\n| x |"));
    }

    @Test
    public void largeColumnSpansFollowedByShortRows() throws Exception
    {
        // Many cells spanning all the columns of a wide table.
        String input = StringUtils.repeat("|=h", COUNT) + "\n"
            + StringUtils.repeat("|(% colspan=\"" + COUNT + "\" %)x\n", COUNT);
        String markdown = renderToMarkdown(input);

        // A cell spanning several columns must be closed by as many pipes, but it's not padded to the size of the
        // columns it spans when they are large.
        assertEquals("| x " + StringUtils.repeat('|', COUNT), StringUtils.substringAfterLast(markdown, "\n"));
    }

    @Test
    public void largeRowSpansFollowedByShortRows() throws Exception
    {
        // Many cells spanning all the rows of a wide table.
        String input = StringUtils.repeat("|=h", COUNT) + "\n"
            + StringUtils.repeat("|(% rowspan=\"" + COUNT + "\" %)x", COUNT) + "\n" + StringUtils.repeat("|x\n", COUNT);
        String markdown = assertRenderingSize(input);

        // The empty cells filling the spanned columns don't span more columns than the cells of the table: the row
        // spans are ignored in the last rows.
        assertEquals("|  x  |", StringUtils.substringAfterLast(markdown, "\n"));
    }

    private String assertRenderingSize(String input) throws Exception
    {
        String markdown = renderToMarkdown(input);
        assertTrue(String.format("The Markdown rendering is too large: [%s] characters for an input of [%s]",
            markdown.length(), input.length()), markdown.length() <= MAX_RATIO * input.length());
        return markdown;
    }

    private String renderToMarkdown(String input) throws Exception
    {
        XDOM xdom = this.mocker.<Parser>getInstance(Parser.class, "xwiki/2.1").parse(new StringReader(input));
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.mocker.<BlockRenderer>getInstance(BlockRenderer.class, "markdown/1.2").render(xdom, printer);
        return printer.toString();
    }
}
