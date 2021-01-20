/*
 * Copyright 2013-2020 consulo.io
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
package consulo.application.internal;

import com.intellij.openapi.application.Application;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 2020-05-24
 */
public interface ApplicationWithIntentWriteLock extends Application {
  /**
   * Acquires IW lock if it's not acquired by the current thread.
   *
   * @param invokedClassFqn fully qualified name of the class requiring the write intent lock.
   */
  default void acquireWriteIntentLock(@NotNull String invokedClassFqn) {
  }

  /**
   * Releases IW lock.
   */
  default void releaseWriteIntentLock() {
  }

  /**
   * Runs the specified action under Write Intent lock. Can be called from any thread. The action is executed immediately
   * if no write intent action is currently running, or blocked until the currently running write intent action completes.
   * <p>
   * This method is used to implement higher-level API, please do not use it directly.
   * Use {@link #invokeLaterOnWriteThread}, {@link com.intellij.openapi.application.WriteThread} or {@link com.intellij.openapi.application.AppUIExecutor#onWriteThread()} to
   * run code under Write Intent lock asynchronously.
   *
   * @param action the action to run
   */
  default void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    action.run();
  }
}
