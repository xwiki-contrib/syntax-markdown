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
package org.xwiki.contrib.rendering.markdown10.internal.renderer;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.rendering.internal.renderer.xwiki20.AbstractXWikiSyntaxRenderer;
import org.xwiki.rendering.listener.chaining.ChainingListener;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

/**
 * Generates Markdown 1.0 from a {@link org.xwiki.rendering.block.XDOM} object being traversed.
 *
 * @version $Id: c6de9dbd78a32d460fdbbe27f01fb98030c5d68c $
 * @since 8.1RC1
 */
@Component
@Named("markdown/1.0")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class MarkdownRenderer extends AbstractXWikiSyntaxRenderer
{
    /**
     * Needed by MarkdownChainingRenderer to serialize wiki link references.
     */
    @Inject
    @Named("markdown/1.0/link")
    private ResourceReferenceSerializer linkReferenceSerializer;

    /**
     * Needed by MarkdownChainingRenderer to serialize wiki image references.
     */
    @Inject
    @Named("markdown/1.0/image")
    private ResourceReferenceSerializer imageReferenceSerializer;

    @Override
    protected ChainingListener createXWikiSyntaxChainingRenderer(ListenerChain chain)
    {
        return new MarkdownChainingRenderer(chain, this.linkReferenceSerializer, this.imageReferenceSerializer);
    }

    @Override
    public void flush() throws IOException
    {
        // TODO: Understand why the AbstractXWikiSyntaxRenderer calls endDocument() which results in endDocument()
        // being called twice. Note that we don't want this here since we perform some handling in endDocument for
        // Markdown.
    }
}
