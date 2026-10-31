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
import org.junit.jupiter.api.Test;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.wiki.WikiModel;
import org.xwiki.test.annotation.AllComponents;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verify that rendering large content to Markdown 1.2 doesn't overflow the stack, i.e. that the regular expressions
 * used by the renderer don't need a recursion per repeated element.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents
class Markdown12LargeContentTest
{
    private static final int COUNT = 100000;

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeComponent
    void setUpComponents() throws Exception
    {
        this.componentManager.registerMockComponent(WikiModel.class);
    }

    @Test
    void emailWithManyDomainLabels() throws Exception
    {
        String email = "a@b" + StringUtils.repeat(".c", COUNT);

        assertEquals('<' + email + '>', renderToMarkdown("[[mailto:" + email + "]]"));
    }

    @Test
    void htmlTagWithManyAttributes() throws Exception
    {
        String html = "<span" + StringUtils.repeat(" a", COUNT) + ">b</span>";

        assertEquals("c " + html, renderToMarkdown("c {{html}}" + html + "{{/html}}"));
    }

    @Test
    void tableCellWithManyLineBreaks() throws Exception
    {
        // The consecutive line breaks contain empty lines and are thus replaced by 2 line break tags.
        assertEquals("|              |\n| ------------ |\n| a<br/><br/>b |",
            renderToMarkdown("|a" + StringUtils.repeat("\\\\", COUNT) + "b"));
    }

    @Test
    void largeHTMLComment() throws Exception
    {
        String html = "<!--" + StringUtils.repeat(" a", COUNT) + " -->";

        assertEquals("c " + html, renderToMarkdown("c {{html}}" + html + "{{/html}}"));
    }

    @Test
    void macroBlockWithManyParameters() throws Exception
    {
        String markdown = "{{info" + StringUtils.repeat(" a=\"b\"", COUNT) + "}}\nc\n{{/info}}";

        assertEquals("beginDocument [[syntax]=[CommonMark Markdown 1.2]]\n"
            + "onMacroStandalone [info] [a=b] [c]\n"
            + "endDocument [[syntax]=[CommonMark Markdown 1.2]]", render(parse(markdown, "markdown/1.2"), "event/1.0"));
    }

    private String renderToMarkdown(String input) throws Exception
    {
        return render(parse(input, "xwiki/2.1"), "markdown/1.2");
    }

    private XDOM parse(String content, String syntax) throws Exception
    {
        return this.componentManager.<Parser>getInstance(Parser.class, syntax).parse(new StringReader(content));
    }

    private String render(XDOM xdom, String syntax) throws Exception
    {
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.componentManager.<BlockRenderer>getInstance(BlockRenderer.class, syntax).render(xdom, printer);
        return printer.toString();
    }
}
