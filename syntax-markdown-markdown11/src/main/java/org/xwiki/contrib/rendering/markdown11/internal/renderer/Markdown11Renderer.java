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
package org.xwiki.contrib.rendering.markdown11.internal.renderer;

import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.rendering.markdown10.internal.renderer.MarkdownRenderer;
import org.xwiki.rendering.listener.chaining.ChainingListener;
import org.xwiki.rendering.listener.chaining.ListenerChain;

/**
 * Generates Markdown 1.1 from a {@link org.xwiki.rendering.block.XDOM} object being traversed.
 *
 * @version $Id: a0565b7a453680edbd20e91af1e03eae85675e03 $
 * @since 8.1RC1
 */
@Component
@Named("markdown/1.1")
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class Markdown11Renderer extends MarkdownRenderer
{
    @Override
    protected ChainingListener createXWikiSyntaxChainingRenderer(ListenerChain chain)
    {
        return new Markdown11ChainingRenderer(chain, this.linkReferenceSerializer, this.imageReferenceSerializer);
    }
}
