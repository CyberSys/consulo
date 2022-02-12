/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.requests;

import com.intellij.diff.DiffContext;
import com.intellij.diff.DiffContextEx;
import consulo.virtualFileSystem.fileType.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.application.ui.awt.JBUI;
import consulo.application.ui.awt.UIUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;

public class UnknownFileTypeDiffRequest extends ComponentDiffRequest {
  @javax.annotation.Nullable
  private final String myFileName;
  @javax.annotation.Nullable
  private final String myTitle;

  public UnknownFileTypeDiffRequest(@Nonnull VirtualFile file, @javax.annotation.Nullable String title) {
    this(file.getName(), title);
  }

  public UnknownFileTypeDiffRequest(@Nonnull String fileName, @Nullable String title) {
    boolean knownFileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName) != UnknownFileType.INSTANCE;
    myFileName = knownFileType ? null : fileName;
    myTitle = title;
  }

  @Nonnull
  @Override
  public JComponent getComponent(@Nonnull final DiffContext context) {
    final SimpleColoredComponent label = new SimpleColoredComponent();
    label.setTextAlign(SwingConstants.CENTER);
    label.append("Can't show diff for unknown file type. ",
                 new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getInactiveTextColor()));
    if (myFileName != null) {
      label.append("Associate", SimpleTextAttributes.LINK_ATTRIBUTES, new Runnable() {
        @Override
        public void run() {
          FileType type = FileTypeChooser.associateFileType(myFileName);
          if (type != null) onSuccess(context);
        }
      });
      LinkMouseListenerBase.installSingleTagOn(label);
    }
    return JBUI.Panels.simplePanel(label).withBorder(JBUI.Borders.empty(5));
  }

  @javax.annotation.Nullable
  public String getFileName() {
    return myFileName;
  }

  @javax.annotation.Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  protected void onSuccess(@Nonnull DiffContext context) {
    if (context instanceof DiffContextEx) ((DiffContextEx)context).reloadDiffRequest();
  }
}
