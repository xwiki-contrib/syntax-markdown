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
 * Verify that the content containing carriage returns (which can't be expressed in the test files since their line
 * endings are normalized) is rendered properly to Markdown 1.2: code blocks stay code blocks and table rows stay on a
 * single line.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents
class Markdown12CodeLineEndingsTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeComponent
    void setUpComponents() throws Exception
    {
        this.componentManager.registerMockComponent(WikiModel.class);
    }

    @Test
    void codeBlocksWithCarriageReturns() throws Exception
    {
        String markdown = render(parse("{{code}}a\r<b>b</b>{{/code}}\n\n"
            + "* (((\n{{code language=\"java\"}}c\r<b>d</b>{{/code}}\n)))", "xwiki/2.1"), "markdown/1.2");

        assertEquals("    a\n    <b>b</b>\n\n* <div>\n\n  ```java\n  c\n  <b>d</b>\n  ```\n\n  </div>", markdown);

        assertEquals("beginDocument [[syntax]=[CommonMark Markdown 1.2]]\n"
            + "onMacroStandalone [code] [language=none] [a\n<b>b</b>]\n"
            + "beginList [BULLETED]\n"
            + "beginListItem\n"
            + "beginGroup\n"
            + "onMacroStandalone [code] [language=java] [c\n<b>d</b>]\n"
            + "endGroup\n"
            + "endListItem\n"
            + "endList [BULLETED]\n"
            + "endDocument [[syntax]=[CommonMark Markdown 1.2]]",
            render(parse(markdown, "markdown/1.2"), "event/1.0"));
    }

    @Test
    void macroWithCarriageReturnsInTableCell() throws Exception
    {
        String markdown = render(parse("|=a\n|{{info}}b\rc\r\rd{{/info}}", "xwiki/2.1"), "markdown/1.2");

        assertEquals("|                  a                  |\n"
            + "| ----------------------------------- |\n"
            + "| {{info}}b<br/>c<br/><br/>d{{/info}} |", markdown);

        assertEquals("beginDocument [[syntax]=[CommonMark Markdown 1.2]]\n"
            + "beginTable\n"
            + "beginTableRow\n"
            + "beginTableHeadCell\n"
            + "onWord [a]\n"
            + "endTableHeadCell\n"
            + "endTableRow\n"
            + "beginTableRow\n"
            + "beginTableCell\n"
            + "onMacroInline [info] [] [b<br/>c<br/><br/>d]\n"
            + "endTableCell\n"
            + "endTableRow\n"
            + "endTable\n"
            + "endDocument [[syntax]=[CommonMark Markdown 1.2]]",
            render(parse(markdown, "markdown/1.2"), "event/1.0"));
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
