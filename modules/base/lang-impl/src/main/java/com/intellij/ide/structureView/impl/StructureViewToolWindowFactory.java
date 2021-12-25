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

package com.intellij.ide.structureView.impl;

import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public class StructureViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  @RequiredUIAccess
  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    StructureViewFactoryImpl factory = (StructureViewFactoryImpl)StructureViewFactory.getInstance(project);
    factory.initToolWindow((ToolWindowEx)toolWindow);
  }

  @Override
  public boolean shouldBeAvailable(@Nonnull Project project) {
    return !project.isWelcome();
  }
}
