/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.openapi.editor.actions;

import consulo.dataContext.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import javax.annotation.Nullable;

/**
 * @author max
 */
public class EscapeAction extends EditorAction {
  public EscapeAction() {
    super(new Handler());
    setInjectedContext(true);
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (editor instanceof EditorEx) {
        EditorEx editorEx = (EditorEx)editor;
        if (editorEx.isStickySelection()) {
          editorEx.setStickySelection(false);
        }
      }
      boolean scrollNeeded = editor.getCaretModel().getCaretCount() > 1;
      retainOldestCaret(editor.getCaretModel());
      editor.getSelectionModel().removeSelection();
      if (scrollNeeded) {
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }

    private static void retainOldestCaret(CaretModel caretModel) {
      while(caretModel.getCaretCount() > 1) {
        caretModel.removeCaret(caretModel.getPrimaryCaret());
      }
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      SelectionModel selectionModel = editor.getSelectionModel();
      CaretModel caretModel = editor.getCaretModel();
      return selectionModel.hasSelection() || caretModel.getCaretCount() > 1;
    }
  }
}
