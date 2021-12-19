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
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.IncorrectOperationException;
import consulo.ide.actions.CreateDirectoryOrPackageType;
import consulo.psi.PsiPackageManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.File;
import java.util.StringTokenizer;

public class CreateDirectoryOrPackageHandler implements InputValidatorEx {
  @Nullable
  private final Project myProject;
  @Nonnull
  private final PsiDirectory myDirectory;
  @Nonnull
  private final CreateDirectoryOrPackageType myType;
  @Nullable
  private PsiFileSystemItem myCreatedElement = null;
  @Nonnull
  private final String myDelimiters;
  @Nullable
  private final Component myDialogParent;
  private String myErrorText;

  public CreateDirectoryOrPackageHandler(@Nullable Project project, @Nonnull PsiDirectory directory, CreateDirectoryOrPackageType type, @Nonnull final String delimiters) {
    this(project, directory, type, delimiters, null);
  }

  public CreateDirectoryOrPackageHandler(@Nullable Project project,
                                         @Nonnull PsiDirectory directory,
                                         CreateDirectoryOrPackageType type,
                                         @Nonnull final String delimiters,
                                         @Nullable Component dialogParent) {
    myProject = project;
    myDirectory = directory;
    myType = type;
    myDelimiters = delimiters;
    myDialogParent = dialogParent;
  }

  @Override
  @RequiredUIAccess
  public boolean checkInput(String inputString) {
    final StringTokenizer tokenizer = new StringTokenizer(inputString, myDelimiters);
    VirtualFile vFile = myDirectory.getVirtualFile();
    boolean firstToken = true;
    while (tokenizer.hasMoreTokens()) {
      final String token = tokenizer.nextToken();
      if (!tokenizer.hasMoreTokens() && (token.equals(".") || token.equals(".."))) {
        myErrorText = "Can't create a directory with name '" + token + "'";
        return false;
      }
      if (vFile != null) {
        if (firstToken && "~".equals(token)) {
          final VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
          if (userHomeDir == null) {
            myErrorText = "User home directory not found";
            return false;
          }
          vFile = userHomeDir;
        }
        else if ("..".equals(token)) {
          vFile = vFile.getParent();
          if (vFile == null) {
            myErrorText = "Not a valid directory";
            return false;
          }
        }
        else if (!".".equals(token)) {
          final VirtualFile child = vFile.findChild(token);
          if (child != null) {
            if (!child.isDirectory()) {
              myErrorText = "A file with name '" + token + "' already exists";
              return false;
            }
            else if (!tokenizer.hasMoreTokens()) {
              myErrorText = "A directory with name '" + token + "' already exists";
              return false;
            }
          }
          vFile = child;
        }
      }

      boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;
      if (FileTypeManager.getInstance().isFileIgnored(token)) {
        myErrorText = "Trying to create a " + (isDirectory ? "directory" : "package") + " with an ignored name, the result will not be visible";
        return true;
      }
      if (!isDirectory && token.length() > 0 && !PsiPackageManager.getInstance(myDirectory.getProject()).isValidPackageName(myDirectory, token)) {
        myErrorText = "Not a valid package name, it will not be possible to create a class inside";
        return true;
      }
      firstToken = false;
    }
    myErrorText = null;
    return true;
  }

  @Override
  public String getErrorText(String inputString) {
    return myErrorText;
  }

  @Override
  public boolean canClose(final String subDirName) {

    if (subDirName.length() == 0) {
      showErrorDialog(IdeBundle.message("error.name.should.be.specified"));
      return false;
    }

    final boolean multiCreation = StringUtil.containsAnyChar(subDirName, myDelimiters);
    if (!multiCreation) {
      try {
        myDirectory.checkCreateSubdirectory(subDirName);
      }
      catch (IncorrectOperationException ex) {
        showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage()));
        return false;
      }
    }

    final Boolean createFile = suggestCreatingFileInstead(subDirName);
    if (createFile == null) {
      return false;
    }

    doCreateElement(subDirName, createFile);

    return myCreatedElement != null;
  }

  @Nullable
  private Boolean suggestCreatingFileInstead(String subDirName) {
    boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;

    Boolean createFile = false;
    if (StringUtil.countChars(subDirName, '.') == 1 && Registry.is("ide.suggest.file.when.creating.filename.like.directory")) {
      FileType fileType = findFileTypeBoundToName(subDirName);
      if (fileType != null) {
        String message = "The name you entered looks like a file name. Do you want to create a file named " + subDirName + " instead?";
        int ec = Messages.showYesNoCancelDialog(myProject, message, "File Name Detected", "&Yes, create file", "&No, create " + (isDirectory ? "directory" : "packages"),
                                                CommonBundle.getCancelButtonText(), fileType.getIcon());
        if (ec == Messages.CANCEL) {
          createFile = null;
        }
        if (ec == Messages.YES) {
          createFile = true;
        }
      }
    }
    return createFile;
  }

  @Nullable
  public static FileType findFileTypeBoundToName(String name) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    return fileType instanceof UnknownFileType ? null : fileType;
  }

  private void doCreateElement(final String subDirName, final boolean createFile) {
    boolean isDirectory = myType == CreateDirectoryOrPackageType.Directory;

    Runnable command = () -> {
      final Runnable run = () -> {
        String dirPath = myDirectory.getVirtualFile().getPresentableUrl();
        String actionName = IdeBundle.message("progress.creating.directory", dirPath, File.separator, subDirName);
        LocalHistoryAction action = LocalHistory.getInstance().startAction(actionName);
        try {
          if (createFile) {
            CreateFileAction.MkDirs mkdirs = new CreateFileAction.MkDirs(subDirName, myDirectory);
            myCreatedElement = myType.createDirectory(mkdirs.directory, mkdirs.newName);
          }
          else {
            createDirectories(subDirName);
          }
        }
        catch (final IncorrectOperationException ex) {
          ApplicationManager.getApplication().invokeLater(() -> showErrorDialog(CreateElementActionBase.filterMessage(ex.getMessage())));
        }
        finally {
          action.finish();
        }
      };
      ApplicationManager.getApplication().runWriteAction(run);
    };
    CommandProcessor.getInstance().executeCommand(myProject, command, createFile
                                                                      ? IdeBundle.message("command.create.file")
                                                                      : isDirectory ? IdeBundle.message("command.create.directory") : IdeBundle.message("command.create.package"), null);
  }

  private void showErrorDialog(String message) {
    String title = CommonBundle.getErrorTitle();
    Image icon = Messages.getErrorIcon();
    if (myDialogParent != null) {
      Messages.showMessageDialog(myDialogParent, message, title, icon);
    }
    else {
      Messages.showMessageDialog(myProject, message, title, icon);
    }
  }

  @RequiredUIAccess
  protected void createDirectories(String subDirName) {
    myCreatedElement = myType.createDirectory(myDirectory, subDirName);
  }

  @Nullable
  public PsiFileSystemItem getCreatedElement() {
    return myCreatedElement;
  }
}
