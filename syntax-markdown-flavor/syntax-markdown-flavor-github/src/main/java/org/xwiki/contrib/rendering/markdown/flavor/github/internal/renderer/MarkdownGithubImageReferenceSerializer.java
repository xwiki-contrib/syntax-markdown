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
package org.xwiki.contrib.rendering.markdown.flavor.github.internal.renderer;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.rendering.markdown.markdown12.internal.renderer.Markdown12ImageReferenceSerializer;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Generate a string representation of an Image reference, in GitHub Flavored Markdown.
 *
 * @version $Id$
 * @since 8.7
 */
@Component
@Named("markdown+github/1.0/image")
@Singleton
public class MarkdownGithubImageReferenceSerializer extends Markdown12ImageReferenceSerializer
{
}
