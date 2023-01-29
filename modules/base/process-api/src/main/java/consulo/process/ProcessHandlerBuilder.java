/*
 * Copyright 2013-2022 consulo.io
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
package consulo.process;

import consulo.application.Application;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.io.BaseOutputReader;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 07/11/2022
 */
public interface ProcessHandlerBuilder {
  @Nonnull
  @Deprecated
  static ProcessHandlerBuilder create(@Nonnull GeneralCommandLine commandLine) {
    return Application.get().getInstance(ProcessHandlerBuilderFactory.class).newBuilder(commandLine);
  }

  @Nonnull
  ProcessHandlerBuilder colored();

  /**
   * @return object instance of {@link KillableProcess}
   */
  @Nonnull
  ProcessHandlerBuilder killable();

  /**
   * {@link BaseOutputReader.Options#forMostlySilentProcess()}
   */
  @Nonnull
  ProcessHandlerBuilder silentReader();

  /**
   * {@link BaseOutputReader.Options#BLOCKING}
   */
  @Nonnull
  ProcessHandlerBuilder blockingReader();

  @Nonnull
  ProcessHandlerBuilder consoleType(@Nonnull ProcessConsoleType type);

  @Nonnull
  ProcessHandlerBuilder shouldDestroyProcessRecursively(boolean destroyRecursive);

  @Nonnull
  ProcessHandler build() throws ExecutionException;
}
