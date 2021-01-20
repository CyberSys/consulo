// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

//@ApiStatus.Experimental
public interface EventWatcher {

  final class InstanceHolder {
    private
    @Nullable
    EventWatcher myInstance = null;
    private final boolean myIsEnabled = Boolean.getBoolean("idea.event.queue.dispatch.listen");

    private InstanceHolder() {
    }
  }

  @Nonnull
  InstanceHolder HOLDER = new InstanceHolder();

  static boolean isEnabled() {
    return HOLDER.myIsEnabled;
  }

  @Nullable
  static EventWatcher getInstance() {
    if (!isEnabled()) return null;

    EventWatcher result = HOLDER.myInstance;
    if (result != null) return result;

    Application application = getApplication();
    if (application == null || application.isDisposed()) return null;

    HOLDER.myInstance = result = application.getInstance(EventWatcher.class);

    return result;
  }

  void runnableStarted(@Nonnull Runnable runnable, long startedAt);

  void runnableFinished(@Nonnull Runnable runnable, long startedAt);

  void edtEventStarted(@Nonnull AWTEvent event);

  void edtEventFinished(@Nonnull AWTEvent event, long startedAt);

  void lockAcquired(@Nonnull String invokedClassFqn, @Nonnull LockKind lockKind);

  void logTimeMillis(@Nonnull String processId, long startedAt, @Nonnull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@Nonnull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }
}
