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
package org.xwiki.contrib.rendering.markdown.github10.internal;

import java.util.Collections;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;

/**
 * Register the {@code markdown+github/1.0} Syntax supported by this module.
 *
 * @version $Id$
 * @since 8.8
 */
@Component
@Named("markdown+github/1.0")
@Singleton
public class GitHubMarkdown10SyntaxProvider implements Provider<List<Syntax>>
{
    /**
     * GitHub-Flavored Markdown syntax type.
     */
    public static final SyntaxType MARKDOWN_GITHUB = new SyntaxType("markdown+github", "GitHub-Flavored CommonMark");

    /**
     * GitHub-Flavored Markdown 1.0 syntax.
     */
    public static final Syntax MARKDOWN_GITHUB_1_0 = new Syntax(MARKDOWN_GITHUB, "1.0");

    @Override
    public List<Syntax> get()
    {
        return Collections.singletonList(MARKDOWN_GITHUB_1_0);
    }
}
