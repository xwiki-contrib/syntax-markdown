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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.renderer;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates Markdown syntax for a Macro Block.
 *
 * @version $Id: edd3c4900f7ec0aa1cc17bc7cd339626de1cd128 $
 * @since 8.1RC1
 */
public class Markdown12MacroRenderer extends MarkdownMacroRenderer
{
    /**
     * The ampersands starting a character reference that is decoded in the macro parameter values when parsing.
     */
    private static final Pattern DECODED_REFERENCE_START = Pattern.compile("&(?=(amp|quot|#10|#13);)");

    private static final Pattern LINE_ENDINGS = Pattern.compile("\\r\\n|\\r|\\n");

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
        // The inline macro must fit in the paragraph in which it's located (a blank line would end it and a line could
        // start a new block), and Markdown has no way to escape a line ending in the macro content (which is passed as
        // is to the macro). Thus the line endings are replaced by spaces, as for the link labels.
        String inlineContent = content == null ? null : LINE_ENDINGS.matcher(content).replaceAll(" ");
        return renderBlockMacro(id, parameters, inlineContent, false);
    }

    /**
     * Serializes a macro located in a table cell, which is written as an inline macro on the line of the table row.
     * Contrary to {@link #renderInlineMacro(String, Map, String)}, the line endings of the macro content are kept,
     * since the line endings of a table cell are written as line break tags (which keeps them visible for the macros
     * having wiki content, the line break tags being read back as is in the macro content).
     *
     * @param id the macro id
     * @param parameters the macro parameters
     * @param content the macro content
     * @return the serialized macro such as {@code {{macroid param1="value1"}}content{{/macroid}}}
     * @since 8.10.0
     */
    public String renderTableCellMacro(String id, Map<String, String> parameters, String content)
    {
        return renderBlockMacro(id, parameters, content, false);
    }

    /**
     * Serializes macro parameters to text. The parameter values are always quoted, and the characters that would end
     * the quoted value or the macro (the line endings, since a blank line would end the paragraph, and the macro
     * opening tag of a standalone macro must be on a single line) are written as character references, which are
     * decoded when parsing.
     *
     * @param parameters the macro parameters
     * @return the serialized macro parameters such as {@code param1="value1" param2="value2"}
     */
    @Override
    public String renderMacroParameters(Map<String, String> parameters)
    {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(parameter.getKey()).append("=\"").append(escapeParameterValue(parameter.getValue()))
                .append('"');
        }
        return builder.toString();
    }

    private String escapeParameterValue(String value)
    {
        // The ampersands that are not followed by a decoded character reference are kept as is.
        return DECODED_REFERENCE_START.matcher(value).replaceAll("&amp;").replace("\"", "&quot;")
            .replace("\n", "&#10;").replace("\r", "&#13;");
    }
}
