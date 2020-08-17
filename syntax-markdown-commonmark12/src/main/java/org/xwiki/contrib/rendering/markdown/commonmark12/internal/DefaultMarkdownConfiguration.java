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
package org.xwiki.contrib.rendering.markdown.commonmark12.internal;

import java.util.Collections;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.data.MutableDataHolder;

@Component
@Singleton
public class DefaultMarkdownConfiguration extends AbstractMarkdownConfiguration implements MarkdownConfiguration
{
    @Inject
    private Logger logger;

    @Override
    public MutableDataHolder getOptions()
    {
        MutableDataHolder options = getDefaultOptions(ParserEmulationProfile.COMMONMARK, Collections.emptyList());

        // Configure other options
        options.set(WikiLinkExtension.IMAGE_LINKS, true);

        return options;
    }

    @Override
    protected Logger getLogger()
    {
        return this.logger;
    }
}
