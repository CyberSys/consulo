/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.process;

import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ProcessHandlerFactory;
import consulo.process.local.OSProcessHandler;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

@Singleton
public class ProcessHandlerFactoryImpl extends ProcessHandlerFactory {

  @Nonnull
  @Override
  @SuppressWarnings("deprecation")
  public OSProcessHandler createProcessHandler(@Nonnull GeneralCommandLine commandLine) throws ExecutionException {
    return new OSProcessHandler(commandLine);
  }

  @Override
  @Nonnull
  public OSProcessHandler createColoredProcessHandler(@Nonnull GeneralCommandLine commandLine) throws ExecutionException {
    return new ColoredProcessHandler(commandLine);
  }
}
