/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.ide.scopeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ShowModulesAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.NonProjectFilesScope;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.ui.PopupHandler;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class ScopeViewPane extends AbstractProjectViewPane {
  @NonNls
  public static final String ID = "Scope";
  private final ProjectView myProjectView;
  private ScopeTreeViewPanel myViewPanel;
  private final DependencyValidationManager myDependencyValidationManager;
  private final NamedScopeManager myNamedScopeManager;
  private final NamedScopesHolder.ScopeListener myScopeListener;

  @Inject
  public ScopeViewPane(final Project project, ProjectView projectView, DependencyValidationManager dependencyValidationManager, NamedScopeManager namedScopeManager) {
    super(project);
    myProjectView = projectView;
    myDependencyValidationManager = dependencyValidationManager;
    myNamedScopeManager = namedScopeManager;
    myScopeListener = new NamedScopesHolder.ScopeListener() {
      Alarm refreshProjectViewAlarm = new Alarm();

      @Override
      public void scopesChanged() {
        // amortize batch scope changes
        refreshProjectViewAlarm.cancelAllRequests();
        refreshProjectViewAlarm.addRequest(new Runnable() {
          @Override
          public void run() {
            if (myProject.isDisposed()) return;
            final String subId = getSubId();
            final String id = myProjectView.getCurrentViewId();
            myProjectView.removeProjectPane(ScopeViewPane.this);
            myProjectView.addProjectPane(ScopeViewPane.this);
            if (id != null) {
              if (Comparing.strEqual(id, getId())) {
                myProjectView.changeView(id, subId);
              }
              else {
                myProjectView.changeView(id);
              }
            }
          }
        }, 10);
      }
    };
    myDependencyValidationManager.addScopeListener(myScopeListener);
    myNamedScopeManager.addScopeListener(myScopeListener);
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("scope.view.title");
  }

  @Override
  public consulo.ui.image.Image getIcon() {
    return AllIcons.Ide.LocalScope;
  }

  @Override
  @Nonnull
  public String getId() {
    return ID;
  }

  @Override
  public JComponent createComponent() {
    myViewPanel = new ScopeTreeViewPanel(myProject);
    Disposer.register(this, myViewPanel);
    myViewPanel.initListeners();
    myViewPanel.selectScope(NamedScopesHolder.getScope(myProject, getSubId()));
    myTree = myViewPanel.getTree();
    PopupHandler.installPopupHandler(myTree, IdeActions.GROUP_SCOPE_VIEW_POPUP, ActionPlaces.SCOPE_VIEW_POPUP);
    enableDnD();

    return myViewPanel.getPanel();
  }

  @Override
  public void dispose() {
    myViewPanel = null;
    myDependencyValidationManager.removeScopeListener(myScopeListener);
    myNamedScopeManager.removeScopeListener(myScopeListener);
    super.dispose();
  }

  @Override
  @Nonnull
  public String[] getSubIds() {
    NamedScope[] scopes = myDependencyValidationManager.getScopes();
    scopes = ArrayUtil.mergeArrays(scopes, myNamedScopeManager.getScopes());
    scopes = NonProjectFilesScope.removeFromList(scopes);
    String[] ids = new String[scopes.length];
    for (int i = 0; i < scopes.length; i++) {
      final NamedScope scope = scopes[i];
      ids[i] = scope.getName();
    }
    return ids;
  }

  @Override
  @Nonnull
  public String getPresentableSubIdName(@Nonnull final String subId) {
    return subId;
  }

  @Override
  public void addToolbarActions(DefaultActionGroup actionGroup) {
    actionGroup.add(ActionManager.getInstance().getAction("ScopeView.EditScopes"));
    actionGroup.addAction(new ShowModulesAction(myProject) {
      @Override
      protected String getId() {
        return ScopeViewPane.this.getId();
      }
    }).setAsSecondary(true);
  }

  @Override
  public ActionCallback updateFromRoot(boolean restoreExpandedPaths) {
    saveExpandedPaths();
    myViewPanel.selectScope(NamedScopesHolder.getScope(myProject, getSubId()));
    restoreExpandedPaths();
    return new ActionCallback.Done();
  }

  @Override
  public void select(Object element, VirtualFile file, boolean requestFocus) {
    if (file == null) return;
    PsiFileSystemItem psiFile = file.isDirectory() ? PsiManager.getInstance(myProject).findDirectory(file) : PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;
    if (!(element instanceof PsiElement)) return;

    List<NamedScope> allScopes = new ArrayList<NamedScope>();
    ContainerUtil.addAll(allScopes, myDependencyValidationManager.getScopes());
    ContainerUtil.addAll(allScopes, myNamedScopeManager.getScopes());
    for (int i = 0; i < allScopes.size(); i++) {
      final NamedScope scope = allScopes.get(i);
      String name = scope.getName();
      if (name.equals(getSubId())) {
        allScopes.set(i, allScopes.get(0));
        allScopes.set(0, scope);
        break;
      }
    }
    for (NamedScope scope : allScopes) {
      String name = scope.getName();
      PackageSet packageSet = scope.getValue();
      if (packageSet == null) continue;
      if (changeView(packageSet, ((PsiElement)element), psiFile, name, myNamedScopeManager, requestFocus)) break;
      if (changeView(packageSet, ((PsiElement)element), psiFile, name, myDependencyValidationManager, requestFocus)) break;
    }
  }

  private boolean changeView(final PackageSet packageSet,
                             final PsiElement element,
                             final PsiFileSystemItem psiFileSystemItem,
                             final String name,
                             final NamedScopesHolder holder,
                             boolean requestFocus) {
    if ((packageSet instanceof PackageSetBase && ((PackageSetBase)packageSet).contains(psiFileSystemItem.getVirtualFile(), holder)) ||
        (psiFileSystemItem instanceof PsiFile && packageSet.contains((PsiFile)psiFileSystemItem, holder))) {
      if (!name.equals(getSubId())) {
        myProjectView.changeView(getId(), name);
      }
      myViewPanel.selectNode(element, psiFileSystemItem, requestFocus);
      return true;
    }
    return false;
  }


  @Override
  public int getWeight() {
    return 3;
  }

  @Override
  public void installComparator() {
    myViewPanel.setSortByType();
  }

  @Override
  public SelectInTarget createSelectInTarget() {
    return new ScopePaneSelectInTarget(myProject);
  }

  @Override
  protected Object exhumeElementFromNode(final DefaultMutableTreeNode node) {
    if (node instanceof PackageDependenciesNode) {
      return ((PackageDependenciesNode)node).getPsiElement();
    }
    return super.exhumeElementFromNode(node);
  }

  @Override
  public Object getData(@Nonnull final Key<?> dataId) {
    final Object data = super.getData(dataId);
    if (data != null) {
      return data;
    }
    return myViewPanel != null ? myViewPanel.getData(dataId) : null;
  }

  @Nonnull
  @Override
  public AsyncResult<Void> getReady(@Nonnull Object requestor) {
    final AsyncResult<Void> callback = myViewPanel.getActionCallback();
    return myViewPanel == null ? AsyncResult.rejected() : callback != null ? callback : AsyncResult.resolved();
  }
}
