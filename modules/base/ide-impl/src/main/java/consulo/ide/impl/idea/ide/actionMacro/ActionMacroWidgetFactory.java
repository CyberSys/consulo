/*
 * Copyright 2013-2024 consulo.io
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
package consulo.ide.impl.idea.ide.actionMacro;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;
import consulo.project.ui.wm.StatusBarWidgetFactory;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.annotations.Nls;

/**
 * @author VISTALL
 * @since 03.05.2024
 */
@ExtensionImpl(id = "actionMacroWidget", order = "after readOnlyWidget")
public class ActionMacroWidgetFactory implements StatusBarWidgetFactory {
  private final ActionMacroManager myActionMacroManager;

  @Inject
  public ActionMacroWidgetFactory(ActionMacroManager actionMacroManager) {
    myActionMacroManager = actionMacroManager;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Action Macro";
  }

  @Override
  public boolean isAvailable(@Nonnull Project project) {
    return myActionMacroManager.isRecording();
  }

  @Nonnull
  @Override
  public StatusBarWidget createWidget(@Nonnull Project project) {
    return new ActionMacroWidget();
  }

  @Override
  public boolean canBeEnabledOn(@Nonnull StatusBar statusBar) {
    return false;
  }
}
