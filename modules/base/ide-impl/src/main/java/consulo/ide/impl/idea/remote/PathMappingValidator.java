/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.ide.impl.idea.remote;

import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class PathMappingValidator {
  public static String validatePathMappings(@Nonnull Project project, @Nonnull RemoteSdkAdditionalData data) {
    boolean found = false;
    final List<String> locations = new SmartList<>();
    for (PathMappingProvider mappingProvider : PathMappingProvider.getSuitableMappingProviders(data)) {
      found = found || !mappingProvider.getPathMappingSettings(project, data).isEmpty();
      locations.add(mappingProvider.getProviderPresentableName(data));
    }

    if (!found) {
      final StringBuilder builder = new StringBuilder();
      builder.append("No path mappings were found.");
      if (!locations.isEmpty()) {
        builder.append(" Please, configure them at ").append(StringUtil.join(locations, " or "));
      }
      return builder.toString();
    }
    return null;
  }
}
