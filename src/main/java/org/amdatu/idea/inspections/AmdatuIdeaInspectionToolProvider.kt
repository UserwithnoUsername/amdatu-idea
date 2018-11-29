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

package org.amdatu.idea.inspections

import com.intellij.codeInspection.InspectionToolProvider

class AmdatuIdeaInspectionToolProvider : InspectionToolProvider {

    @Suppress("UNCHECKED_CAST")
    override fun getInspectionClasses(): Array<Class<Any>> {
        return arrayOf(
                ExportedPackageWithoutVersion::class.java as Class<Any>,
                MissingExportedPackage::class.java as Class<Any>,
                MissingPrivatePackage::class.java as Class<Any>,
                MissingBundleInspection::class.java as Class<Any>,
                BundleVersionBaselining::class.java as Class<Any>,
                PackageInfoJavaPackageVersion::class.java as Class<Any>,
                PackageInfoPackageVersion::class.java as Class<Any>
                )
    }

}
