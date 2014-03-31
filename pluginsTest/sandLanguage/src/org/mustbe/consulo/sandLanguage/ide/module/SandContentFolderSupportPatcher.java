/*
 * Copyright 2013-2014 must-be.org
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
package org.mustbe.consulo.sandLanguage.ide.module;

import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderSupportPatcher;
import org.mustbe.consulo.roots.ContentFolderTypeProvider;
import org.mustbe.consulo.roots.impl.ProductionContentFolderTypeProvider;
import org.mustbe.consulo.sandLanguage.ide.module.extension.SandModuleExtension;

import java.util.Set;

/**
 * @author VISTALL
 * @since 31.03.14
 */
public class SandContentFolderSupportPatcher implements ContentFolderSupportPatcher {
  @Override
  public void patch(@NotNull ModifiableRootModel model, @NotNull Set<ContentFolderTypeProvider> set) {
    SandModuleExtension extension = model.getExtension(SandModuleExtension.class);
    if(extension != null) {
      set.add(ProductionContentFolderTypeProvider.getInstance());
    }
  }
}