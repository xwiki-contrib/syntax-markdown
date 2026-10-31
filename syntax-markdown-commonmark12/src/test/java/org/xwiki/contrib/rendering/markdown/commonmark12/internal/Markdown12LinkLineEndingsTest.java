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
 * Verify that the links and images whose reference or alternative text contains carriage returns (which can't be
 * expressed in the test files since their line endings are normalized) are rendered on a single line.
 *
 * @version $Id$
 */
@ComponentTest
@AllComponents
class Markdown12LinkLineEndingsTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @BeforeComponent
    void setUpComponents() throws Exception
    {
        this.componentManager.registerMockComponent(WikiModel.class);
    }

    @Test
    void linksWithCarriageReturns() throws Exception
    {
        String markdown = render(parse("[[label>>doc:A\r\rB]] [[image:C\r\rD.png||alt=\"E\r\rF\"]]", "xwiki/2.1"),
            "markdown/1.2");

        assertEquals("[[label|doc:A  B]] ![[E  F|C  D.png]]", markdown);
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
