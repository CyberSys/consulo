/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.language.editor.CommonDataKeys;
import consulo.ui.ex.action.ToggleAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.EditorSettings;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.impl.EditorSettingsExternalizable;
import consulo.codeEditor.impl.SettingsImpl;
import consulo.codeEditor.SoftWrapAppliancePlaces;
import consulo.application.dumb.DumbAware;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;

/**
 * Provides common functionality for <code>'toggle soft wraps usage'</code> actions.
 *
 * @author Denis Zhdanov
 * @since Aug 23, 2010 11:33:35 AM
 */
public abstract class AbstractToggleUseSoftWrapsAction extends ToggleAction implements DumbAware {

  private final SoftWrapAppliancePlaces myAppliancePlace;
  private final boolean myGlobal;

  /**
   * Creates new <code>AbstractToggleUseSoftWrapsAction</code> object.
   *
   * @param appliancePlace    defines type of the place where soft wraps are applied
   * @param global            indicates if soft wraps should be changed for the current editor only or for the all editors
   *                          used at the target appliance place
   */
  public AbstractToggleUseSoftWrapsAction(@Nonnull SoftWrapAppliancePlaces appliancePlace, boolean global) {
    myAppliancePlace = appliancePlace;
    myGlobal = global;
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    if (myGlobal) {
      Editor editor = getEditor(e);
      if (editor != null) {
        EditorSettings settings = editor.getSettings();
        if (settings instanceof SettingsImpl settingsImpl && settingsImpl.getSoftWrapAppliancePlace() != myAppliancePlace) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
      }
    }
    super.update(e);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    if (myGlobal) return EditorSettingsExternalizable.getInstance().isUseSoftWraps(myAppliancePlace);
    Editor editor = getEditor(e);
    return editor != null && editor.getSettings().isUseSoftWraps();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Editor editor = getEditor(e);
    if (editor == null) {
      return;
    }

    toggleSoftWraps(editor, myGlobal ? myAppliancePlace : null, state);
  }

  public static void toggleSoftWraps(@Nonnull Editor editor, @Nullable SoftWrapAppliancePlaces places, boolean state) {
    Point point = editor.getScrollingModel().getVisibleArea().getLocation();
    LogicalPosition anchorPosition = editor.xyToLogicalPosition(point);
    int intraLineShift = point.y - editor.logicalPositionToXY(anchorPosition).y;

    if (places != null) {
      EditorSettingsExternalizable.getInstance().setUseSoftWraps(state, places);
      EditorFactory.getInstance().refreshAllEditors();
    }
    if (editor.getSettings().isUseSoftWraps() != state) {
      editor.getSettings().setUseSoftWraps(state);
    }

    editor.getScrollingModel().disableAnimation();
    editor.getScrollingModel().scrollVertically(editor.logicalPositionToXY(anchorPosition).y + intraLineShift);
    editor.getScrollingModel().enableAnimation();
  }

  @Nullable
  protected Editor getEditor(AnActionEvent e) {
    return e.getData(CommonDataKeys.EDITOR);
  }
}
