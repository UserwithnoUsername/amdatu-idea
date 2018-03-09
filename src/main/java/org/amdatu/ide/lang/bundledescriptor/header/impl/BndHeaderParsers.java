package org.amdatu.ide.lang.bundledescriptor.header.impl;

import aQute.bnd.osgi.Constants;
import com.intellij.util.containers.ContainerUtil;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParser;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParserProvider;
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