/*
 * Copyright 2013-2022 consulo.io
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
package consulo.component.internal.inject;

import consulo.component.bind.InjectingBinding;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author VISTALL
 * @since 13-Jun-22
 */
public class InjectingBindingHolder {
  private final Map<String, List<InjectingBinding>> myBindings = new HashMap<>();
  private final AtomicBoolean myLocked;

  public InjectingBindingHolder(AtomicBoolean locked) {
    myLocked = locked;
  }

  public void clear() {
    myBindings.clear();
  }

  public void addBinding(InjectingBinding binding) {
    if (myLocked.get()) {
      throw new IllegalArgumentException("locked");
    }

    myBindings.computeIfAbsent(binding.getApiClassName(), s -> new LinkedList<>()).add(binding);
  }

  @Nonnull
  public Map<String, List<InjectingBinding>> getBindings() {
    return Collections.unmodifiableMap(myBindings);
  }

  public static boolean isValid(InjectingBinding binding, int componentProfiles) {
    int bindingComponentProfiles = binding.getComponentProfiles();
    if (bindingComponentProfiles == 0) {
      return true;
    }
    return (componentProfiles & bindingComponentProfiles) == bindingComponentProfiles;
  }

  @Nullable
  public static InjectingBinding findValid(List<InjectingBinding> bindings, int componentProfiles) {
    InjectingBinding result = null;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < bindings.size(); i++) {
      InjectingBinding binding = bindings.get(i);

      if (isValid(binding, componentProfiles)) {
        // do not allow override services
        if (result != null) {
          return null;
        }
        result = binding;
      }
    }

    return result;
  }
}
