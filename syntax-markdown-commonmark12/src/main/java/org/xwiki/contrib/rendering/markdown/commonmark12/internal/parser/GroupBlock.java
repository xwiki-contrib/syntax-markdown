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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.sequence.BasedSequence;

/**
 * Flexmark node representing an XWiki Group, i.e. the blocks located between a {@code <div data-xwiki-group>} HTML
 * block and its matching {@code </div>} HTML block.
 *
 * @version $Id$
 * @since 8.9.2
 */
public class GroupBlock extends Block
{
    private final Map<String, String> parameters;

    /**
     * @param parameters the Group parameters (i.e. the attributes of the div, except the Group marker)
     */
    public GroupBlock(Map<String, String> parameters)
    {
        this.parameters = new LinkedHashMap<>(parameters);
    }

    /**
     * @return the Group parameters
     */
    public Map<String, String> getParameters()
    {
        return Collections.unmodifiableMap(this.parameters);
    }

    @NotNull
    @Override
    public BasedSequence[] getSegments()
    {
        return EMPTY_SEGMENTS;
    }
}
