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

package org.amdatu.idea.jps;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Collections;
import java.util.List;

public class AmdatuIdeaBuilderService extends BuilderService {

    @NotNull
    @Override
    public List<? extends BuildTargetType<?>> getTargetTypes() {
        return Collections.singletonList(AmdatuIdeaModuleBasedTargetType.INSTANCE);
    }

    @NotNull
    @Override
    public List<? extends TargetBuilder<?, ?>> createBuilders() {
        return Collections.singletonList(new AmdatuIdeaTargetBuilder());
    }
}
