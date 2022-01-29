/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnActionEvent;
import consulo.dataContext.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import consulo.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author max
 */
public abstract class TreeExpandAllActionBase extends DumbAwareAction implements DumbAware {
  @Nullable
  protected abstract TreeExpander getExpander(DataContext dataContext);

  @RequiredUIAccess
  @Override
  public final void actionPerformed(@Nonnull AnActionEvent e) {
    TreeExpander expander = TreeCollapseAllActionBase.getExpanderMaybeFromToolWindow(e, this::getExpander);
    if (expander == null) return;
    if (!expander.canExpand()) return;
    expander.expandAll();
  }

  @RequiredUIAccess
  @Override
  public final void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    TreeExpander expander = TreeCollapseAllActionBase.getExpanderMaybeFromToolWindow(e, this::getExpander);
    presentation.setEnabled(expander != null && expander.canExpand() && expander.isExpandAllVisible());
  }
}
