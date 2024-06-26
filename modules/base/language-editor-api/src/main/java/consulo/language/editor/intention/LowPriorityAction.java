/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.language.editor.intention;

import consulo.language.editor.inspection.PriorityAction;
import jakarta.annotation.Nonnull;

/**
 * @author Max Ishchenko
 * Marker interface for intentions and quick fixes.
 * Marked actions are shown lower in the list of available quick fixes.
 */
public interface LowPriorityAction extends PriorityAction {
  @Nonnull
  @Override
  default Priority getPriority() {
    return Priority.LOW;
  }
}
