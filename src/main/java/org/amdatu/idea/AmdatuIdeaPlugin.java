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

package org.amdatu.idea;

import aQute.bnd.build.Workspace;

public interface AmdatuIdeaPlugin {

    boolean isBndWorkspace();

    /**
     * Get the bnd workspace for a project
     *
     * @return The bnd {@link Workspace} for the project
     */
    Workspace getWorkspace();


    void refreshWorkspace(boolean refreshExportedContentJars);

    PackageInfoService getPackageInfoService();

    AmdatuIdeaNotificationService getNotificationService();

    WorkspaceOperationToken startWorkspaceOperation();

    void completeWorkspaceOperation(WorkspaceOperationToken token);

    public interface WorkspaceOperationToken {

    }
}
