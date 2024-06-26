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

package consulo.ide.impl.idea.packageDependencies.ui;

import consulo.application.AllIcons;
import consulo.language.content.ProjectRootsUtil;
import consulo.project.ui.view.tree.BaseProjectViewDirectoryHelper;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.Comparing;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.ide.impl.psi.search.scope.packageSet.FilePatternPackageSet;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.image.Image;

import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Set;

public class DirectoryNode extends PackageDependenciesNode {

  private final String myDirName;
  private PsiDirectory myDirectory;

  private DirectoryNode myCompactedDirNode;
  private DirectoryNode myWrapper;

  private boolean myCompactPackages = true;
  private String myFQName = null;
  private final VirtualFile myVDirectory;

  public DirectoryNode(VirtualFile aDirectory,
                       Project project,
                       boolean compactPackages,
                       boolean showFQName,
                       VirtualFile baseDir, final VirtualFile[] contentRoots) {
    super(project);
    myVDirectory = aDirectory;
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    final ProjectFileIndex index = projectRootManager.getFileIndex();
    String dirName = aDirectory.getName();
    if (showFQName) {
      final VirtualFile contentRoot = index.getContentRootForFile(myVDirectory);
      if (contentRoot != null) {
        if (Comparing.equal(myVDirectory, contentRoot)) {
          myFQName = dirName;
        }
        else {
          final VirtualFile sourceRoot = index.getSourceRootForFile(myVDirectory);
          if (Comparing.equal(myVDirectory, sourceRoot)) {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, contentRoot, '/');
          }
          else if (sourceRoot != null) {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, sourceRoot, '/');
          }
          else {
            myFQName = VfsUtilCore.getRelativePath(myVDirectory, contentRoot, '/');
          }
        }

        if (contentRoots.length > 1 && ProjectRootsUtil.isModuleContentRoot(myVDirectory, project)) {
          myFQName = getContentRootName(baseDir, myFQName);
        }
      }
      else {
        myFQName = FilePatternPackageSet.getLibRelativePath(myVDirectory, index);
      }
      dirName = myFQName;
    } else {
      if (contentRoots.length > 1 && ProjectRootsUtil.isModuleContentRoot(myVDirectory, project)) {
        dirName = getContentRootName(baseDir, dirName);
      }
    }
    myDirName = dirName;
    myCompactPackages = compactPackages;
  }

  private String getContentRootName(final VirtualFile baseDir, final String dirName) {
    if (baseDir != null) {
      if (!Comparing.equal(myVDirectory, baseDir)) {
        if (VfsUtil.isAncestor(baseDir, myVDirectory, false)) {
          return VfsUtilCore.getRelativePath(myVDirectory, baseDir, '/');
        }
        else {
          return myVDirectory.getPresentableUrl();
        }
      }
    } else {
      return myVDirectory.getPresentableUrl();
    }
    return dirName;
  }

  @Override
  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      if (child instanceof FileNode || recursively) {
        child.fillFiles(set, true);
      }
    }
  }

  public String toString() {
    if (myFQName != null) return myFQName;
    if (myCompactPackages && myCompactedDirNode != null) {
      return myDirName + "/" + myCompactedDirNode.getDirName();
    }
    return myDirName;
  }

  public String getDirName() {
    if (myVDirectory == null || !myVDirectory.isValid()) return "";
    if (myCompactPackages && myCompactedDirNode != null) {
      return myVDirectory.getName() + "/" + myCompactedDirNode.getDirName();
    }
    return myDirName;
  }

  public String getFQName() {
    final ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile directory = myVDirectory;
    VirtualFile contentRoot = index.getContentRootForFile(directory);
    if (Comparing.equal(directory, contentRoot)) {
      return "";
    }
    if (contentRoot == null) {
      return "";
    }
    return VfsUtilCore.getRelativePath(directory, contentRoot, '/');
  }

  @Override
  public PsiElement getPsiElement() {
    return getTargetDirectory();
  }

  @Nullable
  private PsiDirectory getPsiDirectory() {
    if (myDirectory == null) {
      if (myVDirectory.isValid() && !myProject.isDisposed()) {
        myDirectory = PsiManager.getInstance(myProject).findDirectory(myVDirectory);
      }
    }
    return myDirectory;
  }

  public PsiDirectory getTargetDirectory() {
    DirectoryNode dirNode = this;
    while (dirNode.getCompactedDirNode() != null) {
      dirNode = dirNode.getCompactedDirNode();
      assert dirNode != null;
    }

    return dirNode.getPsiDirectory();
  }

  @Override
  public int getWeight() {
    return 3;
  }

  public boolean equals(Object o) {
    if (isEquals()) {
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof DirectoryNode)) return false;

    final DirectoryNode packageNode = (DirectoryNode)o;

    if (!toString().equals(packageNode.toString())) return false;

    return true;
  }

  public int hashCode() {
    return toString().hashCode();
  }

  @Override
  public Image getIcon() {
    if (myDirectory != null) {
      return IconDescriptorUpdaters.getIcon(myDirectory, 0);
    }
    return AllIcons.Nodes.TreeOpen;
  }

  public void setCompactedDirNode(final DirectoryNode compactedDirNode) {
    if (myCompactedDirNode != null) {
      myCompactedDirNode.myWrapper = null;
    }
    myCompactedDirNode = compactedDirNode;
    if (myCompactedDirNode != null) {
      myCompactedDirNode.myWrapper = this;
    }
  }

  public DirectoryNode getWrapper() {
    return myWrapper;
  }

  @Nullable
  public DirectoryNode getCompactedDirNode() {
    return myCompactPackages ? myCompactedDirNode : null;
  }

  public void removeUpReference() {
    myWrapper = null;
  }


  @Override
  public boolean isValid() {
    return myVDirectory != null && myVDirectory.isValid();
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public String getComment() {
    if (myVDirectory != null && myVDirectory.isValid() && !myProject.isDisposed()) {
      final PsiDirectory directory = getPsiDirectory();
      if (directory != null) {
        return BaseProjectViewDirectoryHelper.getLocationString(directory);
      }
    }
    return super.getComment();
  }

  @Override
  public boolean canSelectInLeftTree(final Map<PsiFile, Set<PsiFile>> deps) {
    Set<PsiFile> files = deps.keySet();
    for (PsiFile file : files) {
      if (file.getContainingDirectory() == getPsiDirectory()) {
        return true;
      }
    }
    return false;
  }

  public VirtualFile getDirectory() {
    return myVDirectory;
  }
}
