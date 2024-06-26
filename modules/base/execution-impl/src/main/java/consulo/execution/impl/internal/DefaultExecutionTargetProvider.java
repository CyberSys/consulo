/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.execution.impl.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.DefaultExecutionTarget;
import consulo.execution.ExecutionTarget;
import consulo.execution.ExecutionTargetProvider;
import consulo.execution.configuration.RunConfiguration;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.List;

@ExtensionImpl
public class DefaultExecutionTargetProvider extends ExecutionTargetProvider {
  @Nonnull
  @Override
  public List<ExecutionTarget> getTargets(@Nonnull Project project, @Nonnull RunConfiguration configuration) {
    return List.of(DefaultExecutionTarget.INSTANCE);
  }
}
