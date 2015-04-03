/*
 * Copyright 2013-2015 must-be.org
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
package com.intellij.openapi.roots.impl;

import com.google.common.base.Predicate;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.roots.ContentFolderTypeProvider;

/**
 * @author VISTALL
 * @since 02.04.2015
 */
public class NullModuleDirModuleRootsProcessor extends ModuleRootsProcessor {
  @Override
  public boolean canHandle(@NotNull ModuleRootModel moduleRootModel) {
    return moduleRootModel.getModule().getModuleDirUrl() == null;
  }

  @Override
  public boolean containsFile(@NotNull TObjectIntHashMap<VirtualFile> roots, @NotNull VirtualFile virtualFile) {
    return roots.contains(virtualFile);
  }

  @NotNull
  @Override
  public VirtualFile[] getFiles(@NotNull ModuleRootModel moduleRootModel, @NotNull Predicate<ContentFolderTypeProvider> predicate) {
    return moduleRootModel.getContentRoots();
  }

  @NotNull
  @Override
  public String[] getUrls(@NotNull ModuleRootModel moduleRootModel, @NotNull Predicate<ContentFolderTypeProvider> predicate) {
    return moduleRootModel.getContentRootUrls();
  }
}