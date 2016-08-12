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
package com.intellij.ide;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.RequiredReadAction;
import consulo.editor.notifications.EditorNotificationProvider;

/**
 * @author nik
 */
public class GeneratedFileEditingNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel>, DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("generated.source.file.editing.notification.panel");

  private final Project myProject;

  public GeneratedFileEditingNotificationProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @RequiredReadAction
  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!GeneratedSourcesFilter.isGenerated(myProject, file)) return null;

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("Generated source files should not be edited. The changes will be lost when sources are regenerated.");
    return panel;
  }
}
