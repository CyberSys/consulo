/*
 * Copyright 2013 Consulo.org
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
package org.consulo.compiler.server.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.roots.impl.DirectoryInfo;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.util.ArchiveVfsUtil;
import com.intellij.util.AbstractQuery;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.consulo.compiler.server.fileSystem.archive.ChildArchiveNewVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author VISTALL
 * @since 15:43/12.08.13
 */
public class CompilerServerDirectoryIndex extends DirectoryIndex {
  @NotNull
  private final Project myProject;
  private final ModuleManager myModuleManager;

  private Map<VirtualFile, DirectoryInfo> myInfoDirectoryCache = new HashMap<VirtualFile, DirectoryInfo>();

  public CompilerServerDirectoryIndex(@NotNull Project project, @NotNull ModuleManager moduleManager) {
    myProject = project;
    myModuleManager = moduleManager;
  }

  @Override
  public void checkConsistency() {
  }

  @Override
  public DirectoryInfo getInfoForDirectory(@NotNull VirtualFile fileForInfo) {
    DirectoryInfo directoryInfo = myInfoDirectoryCache.get(fileForInfo);
    if(directoryInfo != null) {
      return directoryInfo;
    }

    directoryInfo = getDirectoryInfo0(fileForInfo);
    myInfoDirectoryCache.put(fileForInfo, directoryInfo);
    return directoryInfo;
  }

  private DirectoryInfo getDirectoryInfo0(VirtualFile fileForInfo) {
    Module module = null;
    VirtualFile contentRoot = null;
    VirtualFile sourceRoot = null;
    VirtualFile libraryRoot = null;
    byte flags = 0;

    for (Module moduleIter : myModuleManager.getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(moduleIter);
      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        VirtualFile temp = contentEntry.getFile();
        if (temp != null && VfsUtilCore.isAncestor(temp, fileForInfo, false)) {
          contentRoot = temp;
          module = moduleIter;

          for (ContentFolder contentFolder : contentEntry.getFolders()) {
            temp = contentFolder.getFile();
            if (temp != null && VfsUtilCore.isAncestor(temp, fileForInfo, false)) {
              sourceRoot = temp;

              flags |= 1 << contentFolder.getType().ordinal();
            }
          }

          break;
        }
      }
    }

    VirtualFile original =
      fileForInfo instanceof ChildArchiveNewVirtualFile ? ((ChildArchiveNewVirtualFile)fileForInfo).getArchiveFile() : fileForInfo;
    loop: for (Module moduleIter : myModuleManager.getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(moduleIter);
      for (OrderEntry contentEntry : moduleRootManager.getOrderEntries()) {
        if (contentEntry instanceof ModuleExtensionWithSdkOrderEntry) {
          VirtualFile[] files =
            ArrayUtil.mergeArrays(contentEntry.getFiles(OrderRootType.CLASSES), contentEntry.getFiles(OrderRootType.SOURCES));

          for (VirtualFile file : files) {
            if (file.equals(original)) {
              module = moduleIter;
              libraryRoot = original;
              break loop;
            }
          }
        }
      }
    }

    DirectoryInfo directoryInfo = DirectoryInfo.createNew();
    directoryInfo = directoryInfo.with(module, contentRoot, sourceRoot, libraryRoot, flags, OrderEntry.EMPTY_ARRAY);
    return directoryInfo;
  }

  @Override
  public boolean isProjectExcludeRoot(@NotNull VirtualFile dir) {
    return false;
  }

  @NotNull
  @Override
  public Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    final List<VirtualFile> dirs = new ArrayList<VirtualFile>();

    String relatPath = packageName.replace(".", "/");
    for (Module moduleIter : myModuleManager.getModules()) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(moduleIter);
      for (ContentEntry contentEntry : moduleRootManager.getContentEntries()) {
        VirtualFile file = contentEntry.getFile();
        if (file == null) {
          continue;
        }
        VirtualFile fileByRelativePath = file.findFileByRelativePath(relatPath);
        if (fileByRelativePath != null) {
          dirs.add(file);
        }
      }
    }

    if (includeLibrarySources) {
      VirtualFile[] libraryRoots = getLibraryRoots(ModuleManager.getInstance(myProject).getModules());
      for (VirtualFile libraryRoot : libraryRoots) {
        VirtualFile virtualFileForJar = ArchiveVfsUtil.getJarRootForLocalFile(libraryRoot);
        if (virtualFileForJar == null) {
          continue;
        }
        VirtualFile child = virtualFileForJar.findFileByRelativePath(relatPath);
        if (child != null) {
          dirs.add(child);
        }
      }
    }

    return new AbstractQuery<VirtualFile>() {
      @Override
      protected boolean processResults(@NotNull Processor<VirtualFile> consumer) {
        for (VirtualFile dir : dirs) {
          if (!consumer.process(dir)) {
            return false;
          }
        }
        return true;
      }
    };
  }

  public static VirtualFile[] getLibraryRoots(final Module[] modules) {
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            VirtualFile[] files = library.getFiles(OrderRootType.SOURCES);
            ContainerUtil.addAll(roots, files);

            files = library.getFiles(OrderRootType.CLASSES);
            ContainerUtil.addAll(roots, files);
          }
        }
        else if (entry instanceof SdkOrderEntry) {
          VirtualFile[] files = entry.getFiles(OrderRootType.SOURCES);
          ContainerUtil.addAll(roots, files);

          files = entry.getFiles(OrderRootType.CLASSES);
          ContainerUtil.addAll(roots, files);
        }
      }
    }
    return VfsUtil.toVirtualFileArray(roots);
  }

  @Nullable
  @Override
  public String getPackageName(@NotNull VirtualFile dir) {
    return null;
  }

  @Override
  public boolean isInitialized() {
    return true;
  }
}