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

/**
 * Verify that rendering large content to Markdown 1.2 doesn't overflow the stack, i.e. that the regular expressions
 * used by the renderer don't need a recursion per repeated element.
 *
 * @version $Id$
 */
@AllComponents
public class Markdown12LargeContentTest
{
    private static final int COUNT = 100000;

    @Rule
    public MockitoComponentManagerRule mocker = new MockitoComponentManagerRule();

    @BeforeComponent
    public void setUpComponents() throws Exception
    {
        this.mocker.registerMockComponent(WikiModel.class);
    }

    @Test
    public void emailWithManyDomainLabels() throws Exception
    {
        String email = "a@b" + StringUtils.repeat(".c", COUNT);

        assertEquals('<' + email + '>', renderToMarkdown("[[mailto:" + email + "]]"));
    }

    @Test
    public void htmlTagWithManyAttributes() throws Exception
    {
        String html = "<span" + StringUtils.repeat(" a", COUNT) + ">b</span>";

        assertEquals("c " + html, renderToMarkdown("c {{html}}" + html + "{{/html}}"));
    }

    @Test
    public void tableCellWithManyLineBreaks() throws Exception
    {
        // The consecutive line breaks contain empty lines and are thus replaced by 2 line break tags.
        assertEquals("|              |\n| ------------ |\n| a<br/><br/>b |",
            renderToMarkdown("|a" + StringUtils.repeat("\\\\", COUNT) + "b"));
    }

    private String renderToMarkdown(String input) throws Exception
    {
        XDOM xdom = this.mocker.<Parser>getInstance(Parser.class, "xwiki/2.1").parse(new StringReader(input));
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.mocker.<BlockRenderer>getInstance(BlockRenderer.class, "markdown/1.2").render(xdom, printer);
        return printer.toString();
    }
}
