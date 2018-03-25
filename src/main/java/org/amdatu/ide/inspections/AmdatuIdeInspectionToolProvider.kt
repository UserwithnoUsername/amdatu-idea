package org.amdatu.ide.inspections

import com.intellij.codeInspection.InspectionToolProvider

class AmdatuIdeInspectionToolProvider : InspectionToolProvider {

    override fun getInspectionClasses(): Array<Class<Any>> {
        return arrayOf(
                ExportedPackageWithoutVersion::class.java as Class<Any>,
                MissingExportedPackage::class.java as Class<Any>,
                MissingPrivatePackage::class.java as Class<Any>)
    }

}
