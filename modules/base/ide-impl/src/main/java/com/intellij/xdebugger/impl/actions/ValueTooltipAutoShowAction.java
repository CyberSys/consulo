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
package com.intellij.xdebugger.impl.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import consulo.debugger.setting.XDebuggerSettingsManager;

public class ValueTooltipAutoShowAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().isValueTooltipAutoShow();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    XDebuggerSettingManagerImpl.getInstanceImpl().getDataViewSettings().setValueTooltipAutoShow(state);
  }
}