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
package com.intellij.openapi.command;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotations.DeprecationInfo;
import consulo.ui.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public abstract class CommandProcessor {
  @Nonnull
  public static CommandProcessor getInstance() {
    return ServiceManager.getService(CommandProcessor.class);
  }

  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nonnull Runnable runnable, @Nullable String name, @Nullable Object groupId);

  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nullable Project project, @Nonnull Runnable runnable, @Nullable String name, @Nullable Object groupId);

  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nullable Project project, @Nonnull Runnable runnable, @Nullable String name, @Nullable Object groupId, @Nullable Document document);

  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nullable Project project, @Nonnull Runnable runnable, @Nullable String name, @Nullable Object groupId, @Nonnull UndoConfirmationPolicy confirmationPolicy);

  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nullable Project project,
                                      @Nonnull Runnable command,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @Nonnull UndoConfirmationPolicy confirmationPolicy,
                                      @Nullable Document document);

  /**
   * @param shouldRecordCommandForActiveDocument false if the action is not supposed to be recorded into the currently open document's history.
   *                                             Examples of such actions: Create New File, Change Project Settings etc.
   *                                             Default is true.
   */
  @Deprecated
  @DeprecationInfo("Use #executeCommandAsync()")
  public abstract void executeCommand(@Nullable Project project,
                                      @Nonnull Runnable command,
                                      @Nullable String name,
                                      @Nullable Object groupId,
                                      @Nonnull UndoConfirmationPolicy confirmationPolicy,
                                      boolean shouldRecordCommandForActiveDocument);

  @RequiredUIAccess
  public <T> AsyncResult<T> executeCommandAsync(@Nullable Project project, @Nonnull Consumer<AsyncResult<T>> runnable, @Nullable String name, @Nullable Object groupId) {
    return executeCommandAsync(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT);
  }

  @RequiredUIAccess
  public <T> AsyncResult<T> executeCommandAsync(Project project, @Nonnull Consumer<AsyncResult<T>> runnable, String name, Object groupId, Document document) {
    return executeCommandAsync(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT, document);
  }

  @RequiredUIAccess
  public <T> AsyncResult<T> executeCommandAsync(Project project,
                                               @Nonnull final Consumer<AsyncResult<T>> command,
                                               final String name,
                                               final Object groupId,
                                               @Nonnull UndoConfirmationPolicy confirmationPolicy) {
    return executeCommandAsync(project, command, name, groupId, confirmationPolicy, null);
  }

  @RequiredUIAccess
  public abstract <T> AsyncResult<T> executeCommandAsync(@Nullable Project project,
                                                        @Nonnull Consumer<AsyncResult<T>> command,
                                                        @Nullable String name,
                                                        @Nullable Object groupId,
                                                        @Nonnull UndoConfirmationPolicy confirmationPolicy,
                                                        @Nullable Document document);

  /**
   * @param shouldRecordCommandForActiveDocument false if the action is not supposed to be recorded into the currently open document's history.
   *                                             Examples of such actions: Create New File, Change Project Settings etc.
   *                                             Default is true.
   */
  @RequiredUIAccess
  public abstract <T> AsyncResult<T> executeCommandAsync(@Nullable Project project,
                                           @Nonnull Consumer<AsyncResult<T>> command,
                                           @Nullable String name,
                                           @Nullable Object groupId,
                                           @Nonnull UndoConfirmationPolicy confirmationPolicy,
                                           boolean shouldRecordCommandForActiveDocument);

  public abstract void setCurrentCommandName(@Nullable String name);

  public abstract void setCurrentCommandGroupId(@Nullable Object groupId);

  @Nullable
  @Deprecated
  @DeprecationInfo("Use #hasCurrentCommand()")
  public final Runnable getCurrentCommand() {
    return hasCurrentCommand() ? EmptyRunnable.getInstance() : null;
  }

  public abstract boolean hasCurrentCommand();

  @Nullable
  public abstract String getCurrentCommandName();

  @Nullable
  public abstract Object getCurrentCommandGroupId();

  @Nullable
  public abstract Project getCurrentCommandProject();

  public abstract void runUndoTransparentAction(@Nonnull Runnable action);

  @Nonnull
  public abstract <T> AsyncResult<T> runUndoTransparentActionAsync(@Nonnull Consumer<AsyncResult<T>> consumer);

  public abstract boolean isUndoTransparentActionInProgress();

  public abstract void markCurrentCommandAsGlobal(@Nullable Project project);

  public abstract void addAffectedDocuments(@Nullable Project project, @Nonnull Document... docs);

  public abstract void addAffectedFiles(@Nullable Project project, @Nonnull VirtualFile... files);

  public abstract void addCommandListener(@Nonnull CommandListener listener);

  public abstract void addCommandListener(@Nonnull CommandListener listener, @Nonnull Disposable parentDisposable);

  public abstract void removeCommandListener(@Nonnull CommandListener listener);
}
