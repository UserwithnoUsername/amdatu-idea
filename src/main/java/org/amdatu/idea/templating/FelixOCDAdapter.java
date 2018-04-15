// Copyright (c) Neil Bartlett (2009, 2017) and others.
// All Rights Reserved.

package org.amdatu.idea.templating;

import org.apache.felix.metatype.AD;
import org.apache.felix.metatype.OCD;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.ObjectClassDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class FelixOCDAdapter implements ObjectClassDefinition {

    private final OCD ocd;

    public FelixOCDAdapter(OCD ocd) {
        if (ocd == null)
            throw new NullPointerException();
        this.ocd = ocd;
    }

    @Override
    public String getName() {
        return ocd.getName();
    }

    @Override
    public String getID() {
        return ocd.getID();
    }

    @Override
    public String getDescription() {
        return ocd.getDescription();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AttributeDefinition[] getAttributeDefinitions(int filter) {
        if (ocd.getAttributeDefinitions() == null)
            return null;

        Iterator<AD> iter = ocd.getAttributeDefinitions().values().iterator();
        if (filter == ObjectClassDefinition.OPTIONAL || filter == ObjectClassDefinition.REQUIRED) {
            boolean required = (filter == ObjectClassDefinition.REQUIRED);
            iter = new RequiredFilterIterator(iter, required);
        } else if (filter != ObjectClassDefinition.ALL) {
            return null;
        }

        if (!iter.hasNext())
            return null;

        List<AttributeDefinition> result = new ArrayList<>();
        while (iter.hasNext()) {
            result.add(new FelixADAdapter(iter.next()));
        }
        return result.toArray(new AttributeDefinition[0]);
    }

    @Override
    public InputStream getIcon(int size) throws IOException {
        // TODO
        return null;
    }

    @SuppressWarnings("rawtypes")
    private static class RequiredFilterIterator implements Iterator {

        private final Iterator base;

        private final boolean required;

        private AD next;

        private RequiredFilterIterator(Iterator base, boolean required) {
            this.base = base;
            this.required = required;
            this.next = seek();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            AD toReturn = next;
            next = seek();
            return toReturn;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        private AD seek() {
            if (base.hasNext()) {
                AD next;
                do {
                    next = (AD) base.next();
                }
                while (next.isRequired() != required && base.hasNext());

                if (next.isRequired() == required) {
                    return next;
                }
            }

            // nothing found any more
            return null;
        }

    }
}
