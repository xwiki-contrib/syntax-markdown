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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.internal.renderer.ParametersPrinter;

/**
 * Generates Markdown syntax for a Macro Block.
 *
 * @version $Id: edd3c4900f7ec0aa1cc17bc7cd339626de1cd128 $
 * @since 8.1RC1
 */
public class MarkdownMacroRenderer
{
    /**
     * Quote character.
     */
    private static final String QUOTE = "\"";

    private static final String MACRO_OPEN_SYMBOL = "{{";

    private static final String MACRO_CLOSE_SYMBOL = "}}";

    private static final String SLASH = "/";

    private static final String NEWLINE = "\n";

    private static final String HTML_ID = "html";

    private static final ParametersPrinter PARAMETERS_PRINTER = new ParametersPrinter('\\');

    /**
     * Serializes a macro to text.
     *
     * @param id the macro id
     * @param parameters the macro parameters
     * @param content the macro content
     * @param isInline true if the macro is inline, false otherwise
     * @return the serialized macro such as {@code {{macroid param1="value1"}}content{{/macroid}}}
     */
    public String renderMacro(String id, Map<String, String> parameters, String content, boolean isInline)
    {
        return isInline ? renderInlineMacro(id, parameters, content) : renderBlockMacro(id, parameters, content);
    }

    /**
     * Serializes an inline macro to text.
     *
     * @param id the macro id
     * @param parameters the macro parameters
     * @param content the macro content
     * @return the serialized macro such as {@code #[testsimplemacro](param1=value1 param2=value2)}
     */
    public String renderInlineMacro(String id, Map<String, String> parameters, String content)
    {
        StringBuilder buffer = new StringBuilder();

        buffer.append("#[");
        buffer.append(id);
        buffer.append(']');

        // Print parameters (if any)
        if (!parameters.isEmpty() || !StringUtils.isEmpty(content)) {
            buffer.append('(');
            if (!parameters.isEmpty()) {
                buffer.append(renderMacroParameters(parameters));
            }
            if (!StringUtils.isEmpty(content)) {
                if (!parameters.isEmpty()) {
                    buffer.append(' ');
                }
                buffer.append(QUOTE);
                buffer.append(content);
                buffer.append(QUOTE);
            }
            buffer.append(')');
        }

        return buffer.toString();
    }

    /**
     * Serializes a block-level macro to text.
     *
     * @param id the macro id
     * @param parameters the macro parameters
     * @param content the macro content
     * @return the serialized macro such as {@code {{macroid param1="value1"}}content{{/macroid}}}
     */
    public String renderBlockMacro(String id, Map<String, String> parameters, String content)
    {
        return renderBlockMacro(id, parameters, content, true);
    }

    protected String renderBlockMacro(String id, Map<String, String> parameters, String content, boolean withNewLines)
    {
        if (id.equals(HTML_ID)) {
            return content;
        }

        StringBuilder buffer = new StringBuilder();

        // Print begin macro
        buffer.append(MACRO_OPEN_SYMBOL);
        buffer.append(id);

        // Print parameters
        if (!parameters.isEmpty()) {
            buffer.append(' ');
            buffer.append(renderMacroParameters(parameters));
        }

        // Print content and end macro
        if (content == null) {
            buffer.append(SLASH + MACRO_CLOSE_SYMBOL);
        } else {
            buffer.append(MACRO_CLOSE_SYMBOL);
            if (!content.isEmpty()) {
                if (withNewLines) {
                    buffer.append(NEWLINE);
                }
                buffer.append(content);
                if (withNewLines) {
                    buffer.append(NEWLINE);
                }
            }
            buffer.append(MACRO_OPEN_SYMBOL + SLASH).append(id).append(MACRO_CLOSE_SYMBOL);
        }

        return buffer.toString();
    }

    /**
     * Serializes macro parameters to text.
     *
     * @param parameters the macro parameters
     * @return the serialized macro parameters such as {@code param1="value1" param2="value2"}
     */
    public String renderMacroParameters(Map<String, String> parameters)
    {
        return PARAMETERS_PRINTER.print(parameters).replace(MACRO_CLOSE_SYMBOL, "\\}\\}");
    }
}
