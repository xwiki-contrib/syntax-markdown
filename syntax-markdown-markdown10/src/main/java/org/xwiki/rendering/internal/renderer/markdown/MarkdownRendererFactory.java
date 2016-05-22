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

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.internal.renderer.AbstractPrintRendererFactory;
import org.xwiki.rendering.syntax.Syntax;

/**
 * Create Markdown 1.0 Renderers.
 *
 * @version $Id: 95f7010206c064c66b909a294d373220b3461a1b $
 * @since 8.1RC1
 */
@Component
@Named("markdown/1.0")
@Singleton
public class MarkdownRendererFactory extends AbstractPrintRendererFactory
{
    @Override
    public Syntax getSyntax()
    {
        return Syntax.MARKDOWN_1_0;
    }
}
