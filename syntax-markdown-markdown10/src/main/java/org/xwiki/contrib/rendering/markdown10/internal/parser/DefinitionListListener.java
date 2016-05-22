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
package org.xwiki.contrib.rendering.markdown10.internal.parser;

import java.util.Map;

import org.xwiki.rendering.listener.WrappingListener;

/**
 * Special listener for handling Definition Lists: Pegdown issues a Paragraph event for definition description which
 * we don't want (even though it's not technically wrong).
 *
 * @version $Id: cd25ae5cf0153841b9d995723be979621bfeddb9 $
 * @since 4.5M1
 */
public class DefinitionListListener extends WrappingListener
{
    @Override
    public void beginParagraph(Map<String, String> parameters)
    {
        // Ignore
    }

    @Override
    public void endParagraph(Map<String, String> parameters)
    {
        // Ignore
    }
}
