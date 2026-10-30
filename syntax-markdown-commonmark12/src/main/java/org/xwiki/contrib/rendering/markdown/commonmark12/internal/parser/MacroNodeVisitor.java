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
            String content = node.getMacroContentChars().toString();
            getListener().onMacro(node.getName().toString(), parameters, normalizeMacroContent(content), true);
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
        String content = node.getContentChars().toString();
        getListener().onMacro(node.getMacroNode().getName().toString(), parameters, normalizeMacroContent(content),
            false);
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

    private String normalizeMacroContent(String content)
    {
        String normalizedContent;
        if (StringUtils.isEmpty(content)) {
            normalizedContent = null;
        } else {
            normalizedContent = content.trim();
        }
        return normalizedContent;
    }
}
