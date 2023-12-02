/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.desktop.awt.application.impl;

import consulo.annotation.component.ComponentProfiles;
import consulo.application.*;
import consulo.application.impl.internal.ApplicationNamesInfo;
import consulo.application.impl.internal.BaseApplication;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.application.impl.internal.progress.ProgressResult;
import consulo.application.impl.internal.progress.ProgressRunner;
import consulo.application.impl.internal.start.CommandLineArgs;
import consulo.application.impl.internal.start.StartupProgress;
import consulo.application.impl.internal.start.StartupUtil;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.ComponentManager;
import consulo.component.ProcessCanceledException;
import consulo.component.impl.internal.ComponentBinding;
import consulo.component.internal.inject.InjectingContainerBuilder;
import consulo.desktop.application.util.Restarter;
import consulo.desktop.awt.ui.IdeEventQueue;
import consulo.desktop.awt.ui.impl.AWTUIAccessImpl;
import consulo.desktop.boot.main.windows.WindowsCommandLineProcessor;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.ide.AppLifecycleListener;
import consulo.ide.impl.idea.ide.ApplicationActivationStateManager;
import consulo.ide.impl.idea.ide.CommandLineProcessor;
import consulo.ide.impl.idea.ide.GeneralSettings;
import consulo.ide.impl.idea.openapi.progress.util.ProgressWindow;
import consulo.ide.impl.idea.openapi.project.impl.ProjectManagerImpl;
import consulo.ide.impl.idea.openapi.ui.MessageDialogBuilder;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.project.internal.ProjectManagerEx;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ModalityState;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.AppIcon;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.ShutDownTracker;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class DesktopApplicationImpl extends BaseApplication {
  private static final Logger LOG = Logger.getInstance(DesktopApplicationImpl.class);

  private final boolean myHeadlessMode;
  private final boolean myIsInternal;

  private DesktopTransactionGuardImpl myTransactionGuardImpl;

  private volatile boolean myDisposeInProgress;

  private final AtomicBoolean myExitState = new AtomicBoolean();

  public DesktopApplicationImpl(ComponentBinding componentBinding,
                                boolean isHeadless,
                                @Nonnull SimpleReference<? extends StartupProgress> splashRef) {
    super(componentBinding, splashRef);

    ApplicationManager.setApplication(this);

    myIsInternal = ApplicationProperties.isInternal();

    String debugDisposer = System.getProperty("idea.disposer.debug");
    Disposer.setDebugMode((myIsInternal || "on".equals(debugDisposer)) && !"off".equals(debugDisposer));

    myHeadlessMode = isHeadless;

    myDoNotSave = isHeadless;
    myGatherStatistics = LOG.isDebugEnabled() || isInternal();

    if (!isHeadless) {
      Disposer.register(this, Disposable.newDisposable(), "ui");

      StartupUtil.addExternalInstanceListener(commandLineArgs -> {
        LOG.info("ApplicationImpl.externalInstanceListener invocation");

        CommandLineProcessor.processExternalCommandLine(commandLineArgs, null).doWhenDone(project -> {
          final IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);

          if (frame != null) AppIcon.getInstance().requestFocus(frame.getWindow());
        });
      });

      WindowsCommandLineProcessor.LISTENER = (currentDirectory, commandLine) -> {
        LOG.info("Received external Windows command line: current directory " + currentDirectory + ", command line " + commandLine);
        invokeLater(() -> {
          final List<String> args = StringUtil.splitHonorQuotes(commandLine, ' ');
          args.remove(0);   // process name
          CommandLineProcessor.processExternalCommandLine(CommandLineArgs.parse(ArrayUtil.toStringArray(args)), currentDirectory);
        });
      };
    }

    NoSwingUnderWriteAction.watchForEvents(this);
  }

  @Override
  public int getProfiles() {
    return super.getProfiles() | ComponentProfiles.AWT;
  }

  private DesktopTransactionGuardImpl transactionGuard() {
    if (myTransactionGuardImpl == null) {
      myTransactionGuardImpl = new DesktopTransactionGuardImpl();
    }
    return myTransactionGuardImpl;
  }

  @Override
  protected void bootstrapInjectingContainer(@Nonnull InjectingContainerBuilder builder) {
    super.bootstrapInjectingContainer(builder);

    builder.bind(TransactionGuard.class).to(transactionGuard());
  }

  @RequiredUIAccess
  private boolean disposeSelf(final boolean checkCanCloseProject) {
    final ProjectManagerImpl manager = (ProjectManagerImpl)ProjectManagerEx.getInstanceEx();
    final boolean[] canClose = {true};
    for (final Project project : manager.getOpenProjects()) {
      try {
        CommandProcessor.getInstance().executeCommand(project, () -> {
          if (!manager.closeProject(project, true, true, checkCanCloseProject)) {
            canClose[0] = false;
          }
        }, ApplicationBundle.message("command.exit"), null);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      if (!canClose[0]) {
        return false;
      }
    }
    runWriteAction(() -> Disposer.dispose(DesktopApplicationImpl.this));

    Disposer.assertIsEmpty();
    return true;
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isHeadlessEnvironment() {
    return myHeadlessMode;
  }

  @Override
  public void invokeLater(@Nonnull final Runnable runnable) {
    invokeLater(runnable, getDisposed());
  }

  @Override
  public void invokeLater(@Nonnull final Runnable runnable, @Nonnull final BooleanSupplier expired) {
    invokeLater(runnable, getDefaultModalityState(), expired);
  }

  @Override
  public void invokeLater(@Nonnull final Runnable runnable, @Nonnull final ModalityState state) {
    invokeLater(runnable, state, getDisposed());
  }

  @Override
  public void invokeLater(@Nonnull final Runnable runnable, @Nonnull final ModalityState state, @Nonnull final BooleanSupplier expired) {
    if (expired.getAsBoolean()) {
      return;
    }

    getLastUIAccess().give(runnable);
  }

  @RequiredUIAccess
  @Override
  public boolean runProcessWithProgressSynchronously(@Nonnull final Runnable process,
                                                     @Nonnull final String progressTitle,
                                                     final boolean canBeCanceled,
                                                     boolean shouldShowModalWindow,
                                                     @Nullable final ComponentManager project,
                                                     final JComponent parentComponent,
                                                     final String cancelText) {
    if (isDispatchThread() && isWriteAccessAllowed()
      // Disallow running process in separate thread from under write action.
      // The thread will deadlock trying to get read action otherwise.
    ) {
      LOG.debug("Starting process with progress from within write action makes no sense");
      try {
        ProgressManager.getInstance().runProcess(process, new EmptyProgressIndicator());
      }
      catch (ProcessCanceledException e) {
        // ok to ignore.
        return false;
      }
      return true;
    }

    CompletableFuture<ProgressWindow> progress =
      createProgressWindowAsyncIfNeeded(progressTitle, canBeCanceled, shouldShowModalWindow, project, parentComponent, cancelText);

    ProgressRunner<?> progressRunner = new ProgressRunner<>(process).sync().modal().withProgress(progress);

    ProgressResult<?> result = progressRunner.submitAndGet();

    Throwable exception = result.getThrowable();
    if (!(exception instanceof ProcessCanceledException)) {
      ExceptionUtil.rethrowUnchecked(exception);
    }
    return !result.isCanceled();
  }

  @Nonnull
  public final CompletableFuture<ProgressWindow> createProgressWindowAsyncIfNeeded(@Nonnull String progressTitle,
                                                                                   boolean canBeCanceled,
                                                                                   boolean shouldShowModalWindow,
                                                                                   @Nullable ComponentManager project,
                                                                                   @Nullable JComponent parentComponent,
                                                                                   @Nullable String cancelText) {
    if (SwingUtilities.isEventDispatchThread()) {
      return CompletableFuture.completedFuture(createProgressWindow(progressTitle,
                                                                    canBeCanceled,
                                                                    shouldShowModalWindow,
                                                                    project,
                                                                    parentComponent,
                                                                    cancelText));
    }
    return CompletableFuture.supplyAsync(() -> createProgressWindow(progressTitle,
                                                                    canBeCanceled,
                                                                    shouldShowModalWindow,
                                                                    project,
                                                                    parentComponent,
                                                                    cancelText), this::invokeLater);
  }

  @Override
  public void invokeAndWait(@Nonnull Runnable runnable, @Nonnull ModalityState modalityState) {
    getLastUIAccess().giveAndWaitIfNeed(runnable);
  }

  @Override
  @Nonnull
  public ModalityState getCurrentModalityState() {
    return ModalityState.nonModal();
  }

  @Override
  @Nonnull
  public ModalityState getModalityStateForComponent(@Nonnull Component c) {
    return ModalityState.nonModal();
  }

  @Override
  @Nonnull
  public ModalityState getDefaultModalityState() {
    return ModalityState.nonModal();
  }

  @RequiredUIAccess
  @Override
  public long getIdleTime() {
    assertIsDispatchThread();
    return IdeEventQueue.getInstance().getIdleTime();
  }

  @Override
  public void exit() {
    exit(false, false);
  }

  @Override
  public void exit(boolean force, final boolean exitConfirmed) {
    exit(false, exitConfirmed, true, false);
  }

  @Override
  public void restart(final boolean exitConfirmed) {
    exit(false, exitConfirmed, true, true);
  }

  /*
   * There are two ways we can get an exit notification.
   *  1. From user input i.e. ExitAction
   *  2. From the native system.
   *  We should not process any quit notifications if we are handling another one
   *
   *  Note: there are possible scenarios when we get a quit notification at a moment when another
   *  quit message is shown. In that case, showing multiple messages sounds contra-intuitive as well
   */
  private static volatile boolean exiting = false;

  public void exitAsync(boolean force, boolean exitConfirmed, boolean allowListenersToCancel, boolean restart) {
    if (!force && myExitState.get()) {
      return;
    }

    if (!myExitState.compareAndSet(false, true)) {
      return;
    }

    UIAccess uiAccess = getLastUIAccess();
    uiAccess.give(() -> {
      confirmExitIfNeededAsync(exitConfirmed).whenComplete((exitConfirmNext, e) -> {
        if (exitConfirmNext == null) {
          return;
        }

        if (!force && !exitConfirmNext) {
          myExitState.compareAndSet(true, false);
          saveAllAsync();
          return;
        }

        getMessageBus().syncPublisher(AppLifecycleListener.class).appClosing();

        // TODO run exit
      });
    });


    exiting = true;
    try {
      if (!force && !exitConfirmed) {
        return;
      }

      Runnable runnable = new Runnable() {
        @Override
        @RequiredUIAccess
        public void run() {
          if (!force && !confirmExitIfNeeded(exitConfirmed)) {
            saveAll();
            return;
          }

          getMessageBus().syncPublisher(AppLifecycleListener.class).appClosing();
          myDisposeInProgress = true;
          doExit(allowListenersToCancel, restart);
          myDisposeInProgress = false;
        }
      };

      if (isDispatchThread()) {
        runnable.run();
      }
      else {
        invokeLater(runnable, IdeaModalityState.NON_MODAL);
      }
    }
    finally {
      exiting = false;
    }
  }

  public void exit(final boolean force, final boolean exitConfirmed, final boolean allowListenersToCancel, final boolean restart) {
    if (!force && exiting) {
      return;
    }

    exiting = true;
    try {
      if (!force && !exitConfirmed) {
        return;
      }

      Runnable runnable = new Runnable() {
        @Override
        @RequiredUIAccess
        public void run() {
          if (!force && !confirmExitIfNeeded(exitConfirmed)) {
            saveAll();
            return;
          }

          getMessageBus().syncPublisher(AppLifecycleListener.class).appClosing();
          myDisposeInProgress = true;
          doExit(allowListenersToCancel, restart);
          myDisposeInProgress = false;
        }
      };

      if (isDispatchThread()) {
        runnable.run();
      }
      else {
        invokeLater(runnable, IdeaModalityState.NON_MODAL);
      }
    }
    finally {
      exiting = false;
    }
  }

  @RequiredUIAccess
  private boolean doExit(boolean allowListenersToCancel, boolean restart) {
    saveSettings();

    if (allowListenersToCancel && !canExit()) {
      return false;
    }

    final boolean success = disposeSelf(allowListenersToCancel);
    if (!success || isUnitTestMode()) {
      return false;
    }

    int exitCode = 0;
    if (restart && Restarter.isSupported()) {
      try {
        exitCode = Restarter.scheduleRestart();
      }
      catch (IOException e) {
        LOG.warn("Cannot restart", e);
      }
    }
    System.exit(exitCode);
    return true;
  }

  private CompletableFuture<Boolean> confirmExitIfNeededAsync(boolean exitConfirmed) {
    final boolean hasUnsafeBgTasks = getProgressManager().hasUnsafeProgressIndicator();
    if (exitConfirmed && !hasUnsafeBgTasks) {
      return CompletableFuture.completedFuture(true);
    }

// TODO [VISTALL] impl task ask
//    if (hasUnsafeBgTasks || option.isToBeShown()) {
//      String message = ApplicationBundle.message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt",
//                                                 ApplicationNamesInfo.getInstance().getFullProductName());
//
//      if (MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), message)
//                              .yesText(ApplicationBundle.message("command.exit"))
//                              .noText(CommonBundle.message("button.cancel"))
//                              .doNotAsk(option)
//                              .show() != Messages.YES) {
//        return false;
//    }

    return CompletableFuture.completedFuture(true);
  }

  private static boolean confirmExitIfNeeded(boolean exitConfirmed) {
    final boolean hasUnsafeBgTasks = ProgressManager.getInstance().hasUnsafeProgressIndicator();
    if (exitConfirmed && !hasUnsafeBgTasks) {
      return true;
    }

    DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
      @Override
      public boolean isToBeShown() {
        return GeneralSettings.getInstance().isConfirmExit() && ProjectManager.getInstance().getOpenProjects().length > 0;
      }

      @Override
      public void setToBeShown(boolean value, int exitCode) {
        GeneralSettings.getInstance().setConfirmExit(value);
      }

      @Override
      public boolean canBeHidden() {
        return !hasUnsafeBgTasks;
      }

      @Override
      public boolean shouldSaveOptionsOnCancel() {
        return false;
      }

      @Nonnull
      @Override
      public String getDoNotShowMessage() {
        return "Do not ask me again";
      }
    };

    if (hasUnsafeBgTasks || option.isToBeShown()) {
      String message = ApplicationBundle.message(hasUnsafeBgTasks ? "exit.confirm.prompt.tasks" : "exit.confirm.prompt",
                                                 ApplicationNamesInfo.getInstance().getFullProductName());

      if (MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"), message)
                              .yesText(ApplicationBundle.message("command.exit"))
                              .noText(CommonBundle.message("button.cancel"))
                              .doNotAsk(option)
                              .show() != Messages.YES) {
        return false;
      }
    }
    return true;
  }

  @Nonnull
  private ProgressWindow createProgressWindow(@Nonnull String progressTitle,
                                              boolean canBeCanceled,
                                              boolean shouldShowModalWindow,
                                              @Nullable ComponentManager project,
                                              @Nullable JComponent parentComponent,
                                              @Nullable String cancelText) {
    ProgressWindow progress = new ProgressWindow(canBeCanceled, !shouldShowModalWindow, (Project)project, parentComponent, cancelText);
    // in case of abrupt application exit when 'ProgressManager.getInstance().runProcess(process, progress)' below
    // does not have a chance to run, and as a result the progress won't be disposed
    Disposer.register(this, progress);
    progress.setTitle(progressTitle);
    return progress;
  }

  private static String describe(Thread o) {
    if (o == null) return "null";
    return o + " " + System.identityHashCode(o);
  }

  @Override
  public void assertTimeConsuming() {
    if (myHeadlessMode || ShutDownTracker.isShutdownHookRunning()) return;
    LOG.assertTrue(!isDispatchThread(), "This operation is time consuming and must not be called on EDT");
  }

  @Nonnull
  @Override
  public UIAccess getLastUIAccess() {
    return AWTUIAccessImpl.ourInstance;
  }

  @Override
  public boolean isActive() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();

    if (ApplicationActivationStateManager.getState().isInactive() && activeWindow != null) {
      ApplicationActivationStateManager.updateState(activeWindow);
    }

    return ApplicationActivationStateManager.getState().isActive();
  }

  @Override
  public boolean isDisposeInProgress() {
    return myDisposeInProgress || ShutDownTracker.isShutdownHookRunning();
  }

  @Override
  public boolean isRestartCapable() {
    return Restarter.isSupported();
  }

  @Override
  public boolean isSwingApplication() {
    return true;
  }

  @TestOnly
  public void setDisposeInProgress(boolean disposeInProgress) {
    myDisposeInProgress = disposeInProgress;
  }
}
