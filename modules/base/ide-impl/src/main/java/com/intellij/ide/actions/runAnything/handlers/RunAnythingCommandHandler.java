// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.handlers;

import consulo.execution.ui.console.TextConsoleBuilder;
import com.intellij.execution.process.KillableProcessHandler;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;

/**
 * This class customizes 'run anything' command execution settings depending on input command
 */
public abstract class RunAnythingCommandHandler {
  public static final ExtensionPointName<RunAnythingCommandHandler> EP_NAME = ExtensionPointName.create("consulo.runAnything.commandHandler");

  public abstract boolean isMatched(@Nonnull String commandLine);

  /**
   * See {@link KillableProcessHandler#shouldKillProcessSoftly()} for details.
   */
  public boolean shouldKillProcessSoftly() {
    return true;
  }

  /**
   * Provides custom output to be printed in console on the process terminated.
   * E.g. command execution time could be reported on a command execution terminating.
   */
  @Nullable
  public String getProcessTerminatedCustomOutput() {
    return null;
  }

  /**
   * Creates console builder for matched command
   */
  public abstract TextConsoleBuilder getConsoleBuilder(@Nonnull Project project);

  @Nullable
  public static RunAnythingCommandHandler getMatchedHandler(@Nonnull String commandLine) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(handler -> handler.isMatched(commandLine)).findFirst().orElse(null);
  }
}