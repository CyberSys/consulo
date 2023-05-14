// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ide.actions;

import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.util.EditorGotoLineNumberDialog;
import consulo.ide.impl.idea.ide.util.GotoLineNumberDialog;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.undoRedo.CommandProcessor;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.openapi.fileEditor.ex.IdeDocumentHistory;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

import jakarta.annotation.Nonnull;

public class GotoLineAction extends AnAction implements DumbAware {
  public GotoLineAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Editor editor = e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
      GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
      dialog.show();
    }
    else {
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(project, () -> {
        GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
        dialog.show();
        IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
      }, IdeBundle.message("command.go.to.line"), null);
    }
  }

  @Override
  public void update(@Nonnull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Editor editor = event.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE);
    presentation.setEnabledAndVisible(editor != null);
  }
}
