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
package org.xwiki.rendering.internal.renderer.markdown;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.internal.renderer.AbstractBlockRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;

/**
 * Block Renderer for Markdown 1.0 syntax. To be used to convert XDOM into Markdown 1.0.
 *
 * @version $Id: acfb465cc95ebcdeb3f3dc60ba44305769eef182 $
 * @since 8.1RC1
 */
@Component
@Named("markdown/1.0")
@Singleton
public class MarkdownBlockRenderer extends AbstractBlockRenderer
{
    @Inject
    @Named("markdown/1.0")
    private PrintRendererFactory markdownRendererFactory;

    @Override
    protected PrintRendererFactory getPrintRendererFactory()
    {
        return this.markdownRendererFactory;
    }
}
