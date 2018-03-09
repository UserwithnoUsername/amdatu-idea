package org.amdatu.ide.lang.bundledescriptor.header.impl;

import com.intellij.util.containers.ContainerUtil;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParser;
import org.amdatu.ide.lang.bundledescriptor.header.HeaderParserProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

public class AmdatuBlueprintHeaderParsers implements HeaderParserProvider {
    private final Map<String, HeaderParser> myParsers;

    public AmdatuBlueprintHeaderParsers() {
        myParsers = ContainerUtil.newHashMap();
        for (String header : Arrays.asList("-buildfeatures", "-runfeatures")) {
            myParsers.put(header, StandardHeaderParser.INSTANCE);
        }
    }

    @NotNull
    @Override
    public Map<String, HeaderParser> getHeaderParsers() {
        return myParsers;
    }
}