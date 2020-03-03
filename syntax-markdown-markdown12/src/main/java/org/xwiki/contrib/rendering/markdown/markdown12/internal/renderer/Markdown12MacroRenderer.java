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

import java.util.Map;

/**
 * Generates Markdown syntax for a Macro Block.
 *
 * @version $Id: edd3c4900f7ec0aa1cc17bc7cd339626de1cd128 $
 * @since 8.1RC1
 */
public class Markdown12MacroRenderer extends MarkdownMacroRenderer
{
    /**
     * Serializes an inline macro to text.
     *
     * @param id the macro id
     * @param parameters the macro parameters
     * @param content the macro content
     * @return the serialized macro such as {@code {{macroid param1="value1"}}content{{/macroid}}}
     */
    public String renderInlineMacro(String id, Map<String, String> parameters, String content)
    {
        return renderBlockMacro(id, parameters, content, false);
    }
}
