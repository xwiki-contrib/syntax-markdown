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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal.parser;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ext.xwiki.macros.Macro;
import com.vladsch.flexmark.ext.xwiki.macros.MacroBlock;
import com.vladsch.flexmark.ext.xwiki.macros.MacroClose;
import com.vladsch.flexmark.ext.xwiki.macros.MacroVisitor;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

public class MacroNodeVisitor extends AbstractNodeVisitor implements MacroVisitor
{
    private static final Pattern DECODED_REFERENCE = Pattern.compile("&(amp|quot|#10|#13);");

    private static final Map<String, String> DECODED_CHARACTERS = new HashMap<>();

    /**
     * The id of the code macro, whose content is kept exactly (white spaces included) since it's displayed as is.
     */
    private static final String CODE_MACRO_ID = "code";

    /**
     * The line ending located at the end of the content of a macro written on several lines, just before the line of
     * the closing tag.
     */
    private static final Pattern FINAL_LINE_ENDING = Pattern.compile("(?:\\r\\n|\\r|\\n)\\z");

    static {
        DECODED_CHARACTERS.put("amp", "&");
        DECODED_CHARACTERS.put("quot", "\"");
        DECODED_CHARACTERS.put("#10", "\n");
        DECODED_CHARACTERS.put("#13", "\r");
    }

    public MacroNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public static <V extends MacroNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(Macro.class, node -> visitor.visit(node)),
                new VisitHandler<>(MacroBlock.class, node -> visitor.visit(node)),
                new VisitHandler<>(MacroClose.class, node -> visitor.visit(node))
        };
    }

    @Override
    public void visit(Macro node)
    {
        if (!node.isBlockMacro()) {
            Map<String, String> parameters = decodeParameterValues(node.getAttributes());
            String name = node.getName().toString();
            String content = node.isClosedTag() ? null : node.getMacroContentChars().toString();
            getListener().onMacro(name, parameters, normalizeMacroContent(name, content, true), true);
        }
    }

    @Override
    public void visit(MacroClose node)
    {
    }

    @Override
    public void visit(MacroBlock node)
    {
        Map<String, String> parameters = decodeParameterValues(node.getAttributes());
        // The content lines of the macro block (see MacroBlockParser) don't include the prefixes of the containers
        // (e.g. quotation, list item) in which the macro is located.
        String name = node.getMacroNode().getName().toString();
        String content = node.isClosedTag() ? null : node.getContentChars().toString();
        getListener().onMacro(name, parameters, normalizeMacroContent(name, content, false), false);
    }

    /**
     * Decode the character references that the Markdown renderer generates in the macro parameter values, for the
     * characters that can't be written as is (the line endings and the quotes). The other character references are
     * kept as is.
     */
    private Map<String, String> decodeParameterValues(Map<String, String> parameters)
    {
        Map<String, String> decodedParameters = new LinkedHashMap<>();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            Matcher matcher = DECODED_REFERENCE.matcher(parameter.getValue());
            StringBuffer value = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(value, Matcher.quoteReplacement(DECODED_CHARACTERS.get(matcher.group(1))));
            }
            matcher.appendTail(value);
            decodedParameters.put(parameter.getKey(), value.toString());
        }
        return decodedParameters;
    }

    /**
     * @param name the macro name
     * @param content the content written between the macro tags, {@code null} if the macro has no content
     * @param isInline whether the macro is inline
     * @return the macro content
     */
    private String normalizeMacroContent(String name, String content, boolean isInline)
    {
        if (CODE_MACRO_ID.equals(name)) {
            return getExactContent(content, isInline);
        }

        String normalizedContent;
        if (StringUtils.isEmpty(content)) {
            normalizedContent = null;
        } else {
            normalizedContent = content.trim();
        }
        return normalizedContent;
    }

    /**
     * @return the content written between the macro tags, without the line ending that ends the content of a macro
     *     written on several lines (the line endings that follow the opening tag and that precede the closing tag are
     *     not part of the content, as in XWiki Syntax 2.1)
     */
    static String getExactContent(String content, boolean isInline)
    {
        if (content == null || isInline) {
            return content;
        }
        return FINAL_LINE_ENDING.matcher(content).replaceFirst("");
    }
}
