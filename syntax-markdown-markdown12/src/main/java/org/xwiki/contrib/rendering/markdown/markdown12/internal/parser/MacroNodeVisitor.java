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

import java.util.Deque;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.rendering.listener.Listener;

import com.vladsch.flexmark.ext.xwiki.macros.Macro;
import com.vladsch.flexmark.ext.xwiki.macros.MacroBlock;
import com.vladsch.flexmark.ext.xwiki.macros.MacroClose;
import com.vladsch.flexmark.ext.xwiki.macros.MacroVisitor;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.ast.Visitor;

public class MacroNodeVisitor extends AbstractNodeVisitor implements MacroVisitor
{
    public MacroNodeVisitor(NodeVisitor visitor, Deque<Listener> listeners)
    {
        super(visitor, listeners);
    }

    public static <V extends MacroNodeVisitor> VisitHandler<?>[] VISIT_HANDLERS(final V visitor)
    {
        return new VisitHandler<?>[]{
                new VisitHandler<>(Macro.class, new Visitor<Macro>()
                {
                    @Override
                    public void visit(Macro node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(MacroBlock.class, new Visitor<MacroBlock>()
                {
                    @Override
                    public void visit(MacroBlock node)
                    {
                        visitor.visit(node);
                    }
                }),
                new VisitHandler<>(MacroClose.class, new Visitor<MacroClose>()
                {
                    @Override
                    public void visit(MacroClose node)
                    {
                        visitor.visit(node);
                    }
                })
        };
    }

    @Override
    public void visit(Macro node)
    {
        if (!node.isBlockMacro()) {
            Map<String, String> parameters = node.getAttributes();
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
        Map<String, String> parameters = node.getAttributes();
        String content = node.getMacroContentChars().toString();
        getListener().onMacro(node.getMacroNode().getName().toString(), parameters, normalizeMacroContent(content),
            false);
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
