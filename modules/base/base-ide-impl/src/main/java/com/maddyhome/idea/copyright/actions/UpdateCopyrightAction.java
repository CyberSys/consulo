/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import consulo.dataContext.DataContext;
import consulo.language.util.ModuleUtilCore;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.pattern.FileUtil;
import javax.annotation.Nonnull;

public class UpdateCopyrightAction extends BaseAnalysisAction {
  public UpdateCopyrightAction() {
    super(UpdateCopyrightProcessor.TITLE, UpdateCopyrightProcessor.TITLE);
  }

  @Override
  public void update(AnActionEvent event) {
    final boolean enabled = isEnabled(event);
    event.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return false;
    }

    if (!CopyrightManager.getInstance(project).hasAnyCopyrights()) {
      return false;
    }
    final VirtualFile[] files = context.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    final Editor editor = context.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !CopyrightUpdaters.hasExtension(file)) {
        return false;
      }
    }
    else if (files != null && FileUtil.areFiles(files)) {
      boolean copyrightEnabled  = false;
      for (VirtualFile vfile : files) {
        if (CopyrightUpdaters.hasExtension(vfile)) {
          copyrightEnabled = true;
          break;
        }
      }
      if (!copyrightEnabled) {
        return false;
      }

    }
    else {
      if ((files == null || files.length != 1) &&
          context.getData(LangDataKeys.MODULE_CONTEXT) == null &&
          context.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) == null &&
          context.getData(PlatformDataKeys.PROJECT_CONTEXT) == null) {
        final PsiElement[] elems = context.getData(LangDataKeys.PSI_ELEMENT_ARRAY);
        if (elems != null) {
          boolean copyrightEnabled = false;
          for (PsiElement elem : elems) {
            if (!(elem instanceof PsiDirectory)) {
              final PsiFile file = elem.getContainingFile();
              if (file == null || !CopyrightUpdaters.hasExtension(file.getVirtualFile())) {
                copyrightEnabled = true;
                break;
              }
            }
          }
          if (!copyrightEnabled){
            return false;
          }
        }
      }
    }
    return true;
  }

  @Override
  protected void analyze(@Nonnull final Project project, @Nonnull AnalysisScope scope) {
    if (scope.checkScopeWritable(project)) return;
    scope.accept(new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        new UpdateCopyrightProcessor(project, ModuleUtilCore.findModuleForPsiElement(file), file).run();
      }
    });
  }
}