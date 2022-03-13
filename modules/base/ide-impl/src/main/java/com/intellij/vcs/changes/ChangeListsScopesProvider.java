// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.changes;

import consulo.project.Project;
import consulo.application.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import consulo.ide.impl.psi.search.scope.packageSet.CustomScopesProviderEx;
import consulo.language.psi.search.scope.NamedScope;
import consulo.localize.LocalizeValue;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChangeListsScopesProvider extends CustomScopesProviderEx {
  @Nonnull
  private final Project myProject;

  public static ChangeListsScopesProvider getInstance(Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(project, ChangeListsScopesProvider.class);
  }

  public ChangeListsScopesProvider(@Nonnull Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public List<NamedScope> getCustomScopes() {
    if (myProject.isDefault() || !ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) return Collections.emptyList();
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    final List<NamedScope> result = new ArrayList<>();
    result.add(new ChangeListScope(changeListManager));

    if (ChangesUtil.hasMeaningfulChangelists(myProject)) {
      List<LocalChangeList> changeLists = changeListManager.getChangeLists();
      boolean skipSingleDefaultCL = Registry.is("vcs.skip.single.default.changelist") && changeLists.size() == 1 && changeLists.get(0).isBlank();
      if (!skipSingleDefaultCL) {
        for (ChangeList list : changeLists) {
          result.add(new ChangeListScope(changeListManager, list.getName(), LocalizeValue.of(list.getName())));
        }
      }
    }
    return result;
  }

  @Override
  public NamedScope getCustomScope(@Nonnull String name) {
    if (myProject.isDefault()) return null;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    if (ChangeListScope.ALL_CHANGED_FILES_SCOPE_NAME.equals(name)) {
      return new ChangeListScope(changeListManager);
    }
    if (ChangesUtil.hasMeaningfulChangelists(myProject)) {
      final LocalChangeList changeList = changeListManager.findChangeList(name);
      if (changeList != null) {
        return new ChangeListScope(changeListManager, changeList.getName(), LocalizeValue.of(changeList.getName()));
      }
    }
    return null;
  }

  @Override
  public boolean isVetoed(NamedScope scope, ScopePlace place) {
    if (place == ScopePlace.SETTING) {
      if (myProject.isDefault()) return false;
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      return changeListManager.findChangeList(scope.getScopeId()) != null;
    }
    return false;
  }
}
