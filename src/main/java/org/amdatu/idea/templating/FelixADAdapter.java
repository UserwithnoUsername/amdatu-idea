// Copyright (c) Neil Bartlett (2009, 2017) and others.
// All Rights Reserved.
package org.amdatu.idea.templating;

import org.apache.felix.metatype.AD;
import org.osgi.service.metatype.AttributeDefinition;

public class FelixADAdapter implements AttributeDefinition {

    private final AD ad;

    public FelixADAdapter(AD ad) {
        this.ad = ad;
    }

    @Override
    public String getName() {
        return ad.getName();
    }

    @Override
    public String getID() {
        return ad.getID();
    }

    @Override
    public String getDescription() {
        return ad.getDescription();
    }

    @Override
    public int getCardinality() {
        return ad.getCardinality();
    }

    @Override
    public int getType() {
        return ad.getType();
    }

    @Override
    public String[] getOptionValues() {
        return ad.getOptionValues();
    }

    @Override
    public String[] getOptionLabels() {
        return ad.getOptionLabels();
    }

    @Override
    public String validate(String value) {
        return ad.validate(value);
    }

    @Override
    public String[] getDefaultValue() {
        return ad.getDefaultValue();
    }
}