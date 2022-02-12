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
package com.intellij.xdebugger.impl.ui.tree.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.project.Project;
import com.intellij.ui.AppUIUtil;
import consulo.debugger.XSourcePosition;
import consulo.debugger.frame.XNavigatable;
import consulo.debugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author nik
 */
public abstract class XJumpToSourceActionBase extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @Nonnull final String nodeName, final AnActionEvent e) {
    XValue value = node.getValueContainer();
    XNavigatable navigatable = new XNavigatable() {
      public void setSourcePosition(@Nullable final XSourcePosition sourcePosition) {
        if (sourcePosition != null) {
          AppUIUtil.invokeOnEdt(new Runnable() {
            public void run() {
              Project project = node.getTree().getProject();
              if (project.isDisposed()) return;

              sourcePosition.createNavigatable(project).navigate(true);
            }
          });
        }
      }
    };
    startComputingSourcePosition(value, navigatable);
  }

  protected abstract void startComputingSourcePosition(XValue value, XNavigatable navigatable);
}
