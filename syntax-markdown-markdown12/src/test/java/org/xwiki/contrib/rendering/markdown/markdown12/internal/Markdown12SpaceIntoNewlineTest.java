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
package org.xwiki.contrib.rendering.markdown.markdown12.internal;

import java.io.StringReader;
import java.util.Collections;

import org.junit.Rule;
import org.junit.Test;
import org.xwiki.contrib.rendering.markdown.markdown12.MarkdownConfiguration;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.DefaultFlexmarkNodeVisitor;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.Markdown12Parser;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.parser.Markdown12StreamParser;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.internal.parser.plain.PlainTextStreamParser;
import org.xwiki.rendering.internal.renderer.plain.PlainTextRendererFactory;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.parser.ResourceReferenceParser;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.mockito.MockitoComponentManagerRule;

import com.vladsch.flexmark.util.builder.Extension;
import com.vladsch.flexmark.util.data.MutableDataHolder;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verify that 2 spaces at end of line are transformed into a new line when the newline separator is either
 * {@code \n} or {@code \r\n}.
 *
 * @version $Id$
 * @since 8.4.3
 */
@ComponentList({
    Markdown12Parser.class,
    Markdown12StreamParser.class,
    DefaultFlexmarkNodeVisitor.class,
    PlainTextStreamParser.class,
    PlainTextRendererFactory.class
})
public class Markdown12SpaceIntoNewlineTest
{
    @Rule
    public MockitoComponentManagerRule mocker = new MockitoComponentManagerRule();

    @BeforeComponent
    public void setUpComponents() throws Exception
    {
        // Simulate an empty configuration
        MarkdownConfiguration configuration = this.mocker.registerMockComponent(MarkdownConfiguration.class);
        MutableDataHolder options = mock(MutableDataHolder.class);
        when(options.get(com.vladsch.flexmark.parser.Parser.EXTENSIONS)).thenReturn(Collections.<Extension>emptyList());
        when(configuration.getOptions()).thenReturn(options);

        // Not needed for the test so we just mock them
        this.mocker.registerMockComponent(ResourceReferenceParser.class, "image");
        this.mocker.registerMockComponent(ResourceReferenceParser.class, "link");
    }

    @Test
    public void convertTwoSpacesIntoNewMine() throws Exception
    {
        Parser parser = this.mocker.getInstance(Parser.class, "markdown/1.2");

        XDOM xdom = parser.parse(new StringReader("paragraph1 on  \nmultiple  \nlines\n"));
        assertEquals(2, xdom.getBlocks(new ClassBlockMatcher(NewLineBlock.class), Block.Axes.DESCENDANT).size());

        xdom = parser.parse(new StringReader("paragraph1 on  \r\nmultiple  \r\nlines\r\n"));
        assertEquals(2, xdom.getBlocks(new ClassBlockMatcher(NewLineBlock.class), Block.Axes.DESCENDANT).size());
    }
}
