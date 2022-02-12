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
package com.intellij.xdebugger.impl.actions.handlers;

import consulo.debugger.XDebugSession;
import consulo.ui.ex.action.AnActionEvent;

/**
 * @author nik
*/
public class XDebuggerMuteBreakpointsHandler extends XDebuggerToggleActionHandler {
  protected boolean isEnabled(final XDebugSession session, final AnActionEvent event) {
    return true;
  }

  protected boolean isSelected(final XDebugSession session, final AnActionEvent event) {
    return session.areBreakpointsMuted();
  }

  protected void setSelected(final XDebugSession session, final AnActionEvent event, final boolean state) {
    session.setBreakpointMuted(state);
  }
}
