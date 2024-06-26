package consulo.ide.impl.idea.openapi.vcs.changes;

import consulo.versionControlSystem.FilePathComparator;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.versionControlSystem.change.VcsDirtyScope;
import consulo.virtualFileSystem.VirtualFile;
import consulo.versionControlSystem.util.VcsUtil;
import jakarta.annotation.Nonnull;

import java.util.*;

public class RecursiveFileHolder<T> implements IgnoredFilesHolder {

  protected final Project myProject;
  protected final ProjectLevelVcsManager myVcsManager;
  protected final HolderType myHolderType;
  protected final TreeMap<VirtualFile, T> myMap;
  protected final TreeMap<VirtualFile, T> myDirMap;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myMap = new TreeMap<>(FilePathComparator.getInstance());
    myDirMap = new TreeMap<>(FilePathComparator.getInstance());
    myHolderType = holderType;
  }

  @Override
  public void cleanAll() {
    myMap.clear();
    myDirMap.clear();
  }

  protected Collection<VirtualFile> keys() {
    return myMap.keySet();
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
  }

  @Override
  public HolderType getType() {
    return myHolderType;
  }

  @Override
  public void addFile(final VirtualFile file) {
    if (! containsFile(file)) {
      myMap.put(file, null);
      if (file.isDirectory()) {
        myDirMap.put(file, null);
      }
    }
  }

  public void removeFile(@Nonnull final VirtualFile file) {
    myMap.remove(file);
    if (file.isDirectory()) {
      myDirMap.remove(file);
    }
  }

  @Override
  public RecursiveFileHolder copy() {
    final RecursiveFileHolder<T> copyHolder = new RecursiveFileHolder<>(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    copyHolder.myDirMap.putAll(myDirMap);
    return copyHolder;
  }

  @Override
  public boolean containsFile(final VirtualFile file) {
    if (myMap.containsKey(file)) return true;
    final VirtualFile floor = myDirMap.floorKey(file);
    if (floor == null) return false;
    final SortedMap<VirtualFile, T> floorMap = myDirMap.headMap(floor, true);
    for (VirtualFile parent : floorMap.keySet()) {
      if (VfsUtilCore.isAncestor(parent, file, false)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<VirtualFile> values() {
    return myMap.keySet();
  }

  @Override
  public void cleanAndAdjustScope(final VcsModifiableDirtyScope scope) {
    if (myProject.isDisposed()) return;
    final Iterator<VirtualFile> iterator = keys().iterator();
    while (iterator.hasNext()) {
      final VirtualFile file = iterator.next();
      if (isFileDirty(scope, file)) {
        iterator.remove();
      }
    }
  }

  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (!file.isValid()) return true;
    final AbstractVcs[] vcsArr = new AbstractVcs[1];
    if (scope.belongsTo(VcsUtil.getFilePath(file), vcs -> vcsArr[0] = vcs)) {
      return true;
    }
    return vcsArr[0] == null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFileHolder that = (RecursiveFileHolder)o;
    if (myMap.size() != that.myMap.size()) return false;
    final Iterator<Map.Entry<VirtualFile, T>> it1 = myMap.entrySet().iterator();
    final Iterator<Map.Entry<VirtualFile, T>> it2 = that.myMap.entrySet().iterator();

    while (it1.hasNext()) {
      if (! it2.hasNext()) return false;
      Map.Entry<VirtualFile, T> next1 = it1.next();
      Map.Entry<VirtualFile, T> next2 = it2.next();
      if (! Comparing.equal(next1.getKey(), next2.getKey()) || ! Comparing.equal(next1.getValue(), next2.getValue())) return false;
    }

    return true;
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
