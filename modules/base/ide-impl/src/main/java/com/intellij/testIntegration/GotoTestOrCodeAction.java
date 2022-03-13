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

package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.idea.ActionsBundle;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import consulo.ui.ex.action.Presentation;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.ide.impl.psi.util.PsiUtilBase;
import javax.annotation.Nonnull;
import consulo.ui.annotation.RequiredUIAccess;

public class GotoTestOrCodeAction extends BaseCodeInsightAction {
  @Override
  @Nonnull
  protected CodeInsightActionHandler getHandler(){
    return new GotoTestOrCodeHandler();
  }

  @RequiredUIAccess
  @Override
  public void update(AnActionEvent event) {
    Presentation p = event.getPresentation();
    if (TestFinderHelper.getFinders().length == 0) {
      p.setVisible(false);
      return;
    }
    p.setEnabled(false);
    Project project = event.getData(CommonDataKeys.PROJECT);
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
    if (editor == null || project == null) return;

    PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
    if (psiFile == null) return;

    PsiElement element = GotoTestOrCodeHandler.getSelectedElement(editor, psiFile);

    if (TestFinderHelper.findSourceElement(element) == null) return;

    p.setEnabled(true);
    if (TestFinderHelper.isTest(element)) {
      p.setText(ActionsBundle.message("action.GotoTestSubject.text"));
      p.setDescription(ActionsBundle.message("action.GotoTestSubject.description"));
    } else {
      p.setText(ActionsBundle.message("action.GotoTest.text"));
      p.setDescription(ActionsBundle.message("action.GotoTest.description"));
    }
  }
}
