/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.amdatu.idea.lang.bundledescriptor.header.impl;

import aQute.bnd.osgi.Constants;
import com.intellij.util.containers.ContainerUtil;
import org.amdatu.idea.lang.bundledescriptor.header.HeaderParser;
import org.amdatu.idea.lang.bundledescriptor.header.HeaderParserProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BndHeaderParsers implements HeaderParserProvider {
    private final Map<String, HeaderParser> myParsers;

    public BndHeaderParsers() {
        myParsers = ContainerUtil.newHashMap();
        for (String header : Constants.headers) {
            myParsers.put(header, StandardHeaderParser.INSTANCE);
        }

        for (String header : Constants.options) {
            myParsers.put(header, StandardHeaderParser.INSTANCE);
        }

        // overwrite class references
        myParsers.put(Constants.BUNDLE_ACTIVATOR, ClassReferenceParser.INSTANCE);

    }

    @NotNull
    @Override
    public Map<String, HeaderParser> getHeaderParsers() {
        return myParsers;
    }
}