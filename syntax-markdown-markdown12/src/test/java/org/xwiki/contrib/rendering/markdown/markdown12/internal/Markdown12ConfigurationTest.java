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

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.xwiki.contrib.rendering.markdown.markdown12.MarkdownConfiguration;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer.Markdown12BlockRenderer;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer.Markdown12Renderer;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer.Markdown12RendererFactory;
import org.xwiki.contrib.rendering.markdown10.internal.renderer.MarkdownImageReferenceSerializer;
import org.xwiki.contrib.rendering.markdown10.internal.renderer.MarkdownLinkReferenceSerializer;
import org.xwiki.rendering.block.FormatBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.internal.renderer.xwiki20.reference.XWiki20ResourceReferenceTypeSerializer;
import org.xwiki.rendering.listener.Format;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.mockito.MockitoComponentManagerRule;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataHolder;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests exercising the Markdown Renderer with various configuration settings.
 *
 * @version $Id$
 * @since 8.4
 */
@ComponentList({
    Markdown12RendererFactory.class,
    Markdown12BlockRenderer.class,
    Markdown12Renderer.class,
    MarkdownLinkReferenceSerializer.class,
    MarkdownImageReferenceSerializer.class,
    XWiki20ResourceReferenceTypeSerializer.class
})
public class Markdown12ConfigurationTest
{
    @Rule
    public MockitoComponentManagerRule mocker = new MockitoComponentManagerRule();

    @BeforeComponent
    public void setUpComponents() throws Exception
    {
        // Simulate an empty configuration
        MarkdownConfiguration configuration = this.mocker.registerMockComponent(MarkdownConfiguration.class);
        MutableDataHolder options = mock(MutableDataHolder.class);
        when(options.get(Parser.EXTENSIONS)).thenReturn(emptyList());
        when(configuration.getOptions()).thenReturn(options);
    }

    @Test
    public void strikedOutRenderingWithoutStrikethroughExtension() throws Exception
    {
        BlockRenderer renderer = this.mocker.getInstance(BlockRenderer.class, "markdown/1.2");

        XDOM xdom = new XDOM(Arrays.asList(new ParagraphBlock(Arrays.asList(new FormatBlock(Arrays.asList(
            new WordBlock("hello")), Format.STRIKEDOUT)))));
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        renderer.render(xdom, printer);

        assertEquals("<del>hello</del>", printer.toString());
    }
}
