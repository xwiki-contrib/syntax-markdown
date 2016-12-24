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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer;

import org.xwiki.contrib.rendering.markdown11.internal.renderer.Markdown11ChainingRenderer;
import org.xwiki.rendering.listener.chaining.ListenerChain;
import org.xwiki.rendering.listener.reference.ResourceReference;
import org.xwiki.rendering.renderer.reference.ResourceReferenceSerializer;

/**
 * Convert listener events to Markdown 1.2.
 *
 * @version $Id$
 * @since 8.4
 */
public class Markdown12ChainingRenderer extends Markdown11ChainingRenderer
{
    /**
     * @param listenerChain the chain of listener filters used to compute various states
     * @param linkReferenceSerializer the component to use for converting {@link ResourceReference} links to strings
     * @param imageReferenceSerializer the component to use for converting {@link ResourceReference} images to strings
     */
    public Markdown12ChainingRenderer(ListenerChain listenerChain,
        ResourceReferenceSerializer linkReferenceSerializer, ResourceReferenceSerializer imageReferenceSerializer)
    {
        super(listenerChain, linkReferenceSerializer, imageReferenceSerializer);
    }
}
