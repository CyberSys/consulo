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

/**
 * @author cdr
 */
package com.intellij.openapi.module;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import org.consulo.module.extension.ModuleExtension;
import org.consulo.module.extension.ModuleExtensionWithSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleUtil extends ModuleUtilCore {

  private ModuleUtil() {}

  @Nullable
  public static <E extends ModuleExtension<E>> E getExtension(@NotNull Module module, @NotNull Class<E> extensionClass) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return moduleRootManager.getExtension(extensionClass);
  }

  @Nullable
  public static <S extends Sdk, E extends ModuleExtensionWithSdk<E>> S getSdk(@NotNull Module module, @NotNull Class<E> extensionClass) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    final E extension = moduleRootManager.getExtension(extensionClass);
    if(extension == null) {
      return null;
    }
    else {
      return (S) extension.getSdk();
    }
  }
}
