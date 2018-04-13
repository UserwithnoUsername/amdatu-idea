package org.amdatu.idea.inspections

import com.intellij.codeInspection.InspectionToolProvider

class AmdatuIdeaInspectionToolProvider : InspectionToolProvider {

    @Suppress("UNCHECKED_CAST")
    override fun getInspectionClasses(): Array<Class<Any>> {
        return arrayOf(
                ExportedPackageWithoutVersion::class.java as Class<Any>,
                MissingExportedPackage::class.java as Class<Any>,
                MissingPrivatePackage::class.java as Class<Any>,
                MissingBundleInspection::class.java as Class<Any>)
    }

}
