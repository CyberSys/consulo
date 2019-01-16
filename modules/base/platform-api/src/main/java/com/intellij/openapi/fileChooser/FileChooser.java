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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import consulo.ui.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;

public class FileChooser {
  private static final Logger LOG = Logger.getInstance(FileChooser.class);

  private FileChooser() {
  }

  // region dead stuff

  /**
   * Normally, callback isn't invoked if a chooser was cancelled.
   * If the situation should be handled separately this interface may be used.
   */
  public interface FileChooserConsumer extends Consumer<List<VirtualFile>> {
    void cancelled();
  }

  @Nonnull
  public static VirtualFile[] chooseFiles(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    return chooseFiles(descriptor, null, project, toSelect);
  }

  @Nonnull
  @Deprecated
  public static VirtualFile[] chooseFiles(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Component parent, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.choose(project, toSelect == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{toSelect});
  }

  @Nullable
  @Deprecated
  public static VirtualFile chooseFile(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    return chooseFile(descriptor, null, project, toSelect);
  }

  @Nullable
  @Deprecated
  public static VirtualFile chooseFile(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Component parent, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    return ArrayUtil.getFirstElement(chooseFiles(descriptor, parent, project, toSelect));
  }

  @Deprecated
  public static void chooseFiles(@Nonnull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final VirtualFile toSelect,
                                 @RequiredUIAccess @Nonnull final Consumer<List<VirtualFile>> callback) {
    chooseFiles(descriptor, project, null, toSelect, callback);
  }

  @Deprecated
  public static void chooseFiles(@Nonnull final FileChooserDescriptor descriptor,
                                 @Nullable final Project project,
                                 @Nullable final Component parent,
                                 @Nullable final VirtualFile toSelect,
                                 @Nonnull final Consumer<List<VirtualFile>> callback) {
    final FileChooserFactory factory = FileChooserFactory.getInstance();
    final PathChooserDialog pathChooser = factory.createPathChooser(descriptor, project, parent);
    pathChooser.choose(toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog and only if there is file selected
   * @since 13
   */
  @Deprecated
  public static void chooseFile(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Project project, @Nullable final VirtualFile toSelect, @Nonnull final Consumer<VirtualFile> callback) {
    chooseFile(descriptor, project, null, toSelect, callback);
  }

  /**
   * Shows file/folder open dialog, allows user to choose file/folder and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog and only if there is file selected
   * @since 13
   */
  @Deprecated
  public static void chooseFile(@Nonnull final FileChooserDescriptor descriptor,
                                @Nullable final Project project,
                                @Nullable final Component parent,
                                @Nullable final VirtualFile toSelect,
                                @Nonnull final Consumer<VirtualFile> callback) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    chooseFiles(descriptor, project, parent, toSelect, new Consumer<List<VirtualFile>>() {
      @Override
      public void consume(List<VirtualFile> files) {
        callback.consume(files.get(0));
      }
    });
  }

  // endregion

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param toSelect   file to preselect
   * @param callback   callback will be invoked after user have closed dialog and only if there are files selected
   * @see FileChooserConsumer
   * @since 11.1
   */
  @RequiredUIAccess
  @Nonnull
  public static AsyncResult<VirtualFile[]> chooseFilesAsync(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    return chooseFilesAsync(descriptor, project, null, toSelect);
  }

  /**
   * Shows file/folder open dialog, allows user to choose files/folders and then passes result to callback in EDT.
   * On MacOS Open Dialog will be shown with slide effect if Macish UI is turned on.
   *
   * @param descriptor file chooser descriptor
   * @param project    project
   * @param parent     parent component
   * @param toSelect   file to preselect
   * @see FileChooserConsumer
   * @since 11.1
   */
  @Nonnull
  @RequiredUIAccess
  public static AsyncResult<VirtualFile[]> chooseFilesAsync(@Nonnull final FileChooserDescriptor descriptor,
                                                            @Nullable final Project project,
                                                            @Nullable final Component parent,
                                                            @Nullable final VirtualFile toSelect) {
    final FileChooserFactory factory = FileChooserFactory.getInstance();
    final PathChooserDialog pathChooser = factory.createPathChooser(descriptor, project, parent);
    return pathChooser.chooseAsync(toSelect);
  }

  @Nonnull
  @RequiredUIAccess
  public static AsyncResult<VirtualFile[]> chooseFilesAsync(@Nonnull final FileChooserDescriptor descriptor,
                                                            @Nullable final Component parent,
                                                            @Nullable final Project project,
                                                            @Nullable final VirtualFile toSelect) {
    final FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, parent);
    return chooser.chooseAsync(project, toSelect == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{toSelect});
  }

  @Nonnull
  @RequiredUIAccess
  public static AsyncResult<VirtualFile> chooseFileAsync(@Nonnull final FileChooserDescriptor descriptor, @Nullable final Project project, @Nullable final VirtualFile toSelect) {
    return chooseFileAsync(descriptor, null, project, toSelect);
  }

  @Nonnull
  @RequiredUIAccess
  public static AsyncResult<VirtualFile> chooseFileAsync(@Nonnull final FileChooserDescriptor descriptor,
                                                         @Nullable final Component parent,
                                                         @Nullable final Project project,
                                                         @Nullable final VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    AsyncResult<VirtualFile> fileAsyncResult = AsyncResult.undefined();
    chooseFilesAsync(descriptor, parent, project, toSelect).doWhenDone(files -> fileAsyncResult.setDone(ArrayUtil.getFirstElement(files)));
    return fileAsyncResult;
  }

  @Nonnull
  @RequiredUIAccess
  public static AsyncResult<VirtualFile> chooseFileAsync(@Nonnull final FileChooserDescriptor descriptor,
                                                         @Nullable final Project project,
                                                         @Nullable final Component parent,
                                                         @Nullable final VirtualFile toSelect) {
    LOG.assertTrue(!descriptor.isChooseMultiple());
    AsyncResult<VirtualFile> result = AsyncResult.undefined();
    chooseFilesAsync(descriptor, project, parent, toSelect).doWhenDone(virtualFiles -> result.setDone(virtualFiles[0]));
    return result;
  }
}
