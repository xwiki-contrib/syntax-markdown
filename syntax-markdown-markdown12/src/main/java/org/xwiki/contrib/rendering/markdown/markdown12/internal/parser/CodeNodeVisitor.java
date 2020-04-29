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
package org.xwiki.contrib.rendering.markdown.markdown12.internal.parser;

import java.util.Collections;
import java.util.Deque;
import java.util.Map;

import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;

/**
 * Handle Code events.
 *
 * @version $Id$
 * @since 8.4
 */
public class CodeNodeVisitor extends AbstractNodeVisitor
{
    static <V extends CodeNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
            new VisitHandler<>(Code.class, node -> visitor.visit(node)),
            new VisitHandler<>(FencedCodeBlock.class, node -> visitor.visit(node)),
            new VisitHandler<>(IndentedCodeBlock.class, node -> visitor.visit(node))
        };
    }

    /**
     * Id of the code macro.
     */
    private static final String CODE_MACRO_ID = "code";

    public CodeNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public void visit(Code node)
    {
        // Since XWiki doesn't have a Code Block we generate a Code Macro Block
        getListener().onMacro(CODE_MACRO_ID, getCodeMacroParameters(null), node.getText().toString(), true);
    }

    public void visit(FencedCodeBlock node)
    {
        // Since XWiki doesn't have a Code Block we generate a Code Macro Block
        Map<String, String> parameters;
        if (node.getInfo() != null) {
            parameters = getCodeMacroParameters(node.getInfo().toString());
        } else {
            parameters = getCodeMacroParameters(null);
        }

        // Flexmark puts trailing newline in the HTML block so we need to remove it.
        String content = node.getContentChars().toString().trim();
        getListener().onMacro(CODE_MACRO_ID, parameters, content, false);
    }

    public void visit(IndentedCodeBlock node)
    {
        // Since XWiki doesn't have a Code Block we generate a Code Macro Block
        // Insert a Group Block if we are in an inline context since the code macro is a standalone macro.
        // We consider that we are in an inline context if the parent node is a list item.
        // Note that Markdown syntax doesn't support indented code blocks inside table cells!
        if (node.getParent() instanceof ListItem) {
            getListener().beginGroup(Collections.emptyMap());
        }
        getListener().onMacro(CODE_MACRO_ID, getCodeMacroParameters(null), node.getContentChars().toString(), false);
        if (node.getParent() instanceof ListItem) {
            getListener().endGroup(Collections.emptyMap());
        }
    }

    private Map<String, String> getCodeMacroParameters(String language)
    {
        return Collections.singletonMap("language", language == null ? "none" : language);
    }
}
