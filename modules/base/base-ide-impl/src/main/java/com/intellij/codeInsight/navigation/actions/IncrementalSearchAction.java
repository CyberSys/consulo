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

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import consulo.dataContext.DataContext;
import consulo.project.DumbAware;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

public class IncrementalSearchAction extends AnAction implements DumbAware {
  public IncrementalSearchAction() {
    setEnabledInModalContext(true);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;

    new IncrementalSearchHandler().invoke(project, editor);
  }

  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }
}