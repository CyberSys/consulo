/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.execution.rmi;

import consulo.application.internal.ApplicationManagerEx;
import consulo.application.progress.ProgressManager;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.DefaultProgramRunner;
import consulo.execution.runner.ProgramRunner;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.util.ExceptionUtil;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.logging.Logger;
import consulo.process.BaseProcessHandler;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.util.rmi.RemoteDeadHand;
import consulo.util.rmi.RemoteServer;
import consulo.util.rmi.RemoteUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Gregory.Shrago
 */
public abstract class RemoteProcessSupport<Target, EntryPoint, Parameters> {
  public static final Logger LOG = Logger.getInstance(RemoteProcessSupport.class);

  private final Class<EntryPoint> myValueClass;
  private final HashMap<Pair<Target, Parameters>, Info> myProcMap = new HashMap<>();

  static {
    RemoteServer.setupRMI();
  }

  public RemoteProcessSupport(Class<EntryPoint> valueClass) {
    myValueClass = valueClass;
  }

  protected abstract void fireModificationCountChanged();

  protected abstract String getName(Target target);

  protected void logText(Parameters configuration, ProcessEvent event, Key outputType, Object info) {
  }

  public void stopAll() {
    stopAll(false);
  }

  public void stopAll(boolean wait) {
    ArrayList<ProcessHandler> allHandlers = new ArrayList<>();
    synchronized (myProcMap) {
      for (Info o : myProcMap.values()) {
        ContainerUtil.addIfNotNull(o.handler, allHandlers);
      }
    }
    for (ProcessHandler handler : allHandlers) {
      handler.destroyProcess();
    }
    if (wait) {
      for (ProcessHandler handler : allHandlers) {
        handler.waitFor();
      }
    }
  }

  public List<Parameters> getActiveConfigurations(@Nonnull Target target) {
    ArrayList<Parameters> result = new ArrayList<>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target) {
          result.add(pair.second);
        }
      }
    }
    return result;
  }

  public EntryPoint acquire(@Nonnull Target target, @Nonnull Parameters configuration) throws Exception {
    ApplicationManagerEx.getApplicationEx().assertTimeConsuming();

    Ref<RunningInfo> ref = Ref.create(null);
    Pair<Target, Parameters> key = Pair.create(target, configuration);
    if (!getExistingInfo(ref, key)) {
      startProcess(target, configuration, key);
      if (ref.isNull()) {
        try {
          //noinspection SynchronizationOnLocalVariableOrMethodParameter
          synchronized (ref) {
            while (ref.isNull()) {
              ref.wait(1000);
              ProgressManager.checkCanceled();
            }
          }
        }
        catch (InterruptedException e) {
          ProgressManager.checkCanceled();
        }
      }
    }
    if (ref.isNull()) throw new RuntimeException("Unable to acquire remote proxy for: " + getName(target));
    RunningInfo info = ref.get();
    if (info.handler == null) {
      String message = info.name;
      if (message != null && message.startsWith("ERROR: transport error 202:")) {
        message = "Unable to start java process in debug mode: -Xdebug parameters are already in use.";
      }
      throw new ExecutionException(message);
    }
    return acquire(info);
  }

  public void release(@Nonnull Target target, @Nullable Parameters configuration) {
    ArrayList<ProcessHandler> handlers = new ArrayList<>();
    synchronized (myProcMap) {
      for (Pair<Target, Parameters> pair : myProcMap.keySet()) {
        if (pair.first == target && (configuration == null || pair.second == configuration)) {
          ContainerUtil.addIfNotNull(handlers, myProcMap.get(pair).handler);
        }
      }
    }
    for (ProcessHandler handler : handlers) {
      handler.destroyProcess();
    }
    fireModificationCountChanged();
  }

  private void startProcess(Target target, Parameters configuration, Pair<Target, Parameters> key) {
    ProgramRunner runner = new DefaultProgramRunner() {
      @Override
      @Nonnull
      public String getRunnerId() {
        return "MyRunner";
      }

      @Override
      public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
        return true;
      }
    };
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    ProcessHandler processHandler = null;
    try {
      RunProfileState state = getRunProfileState(target, configuration, executor);
      ExecutionResult result = state.execute(executor, runner);
      //noinspection ConstantConditions
      processHandler = result.getProcessHandler();
    }
    catch (Exception e) {
      dropProcessInfo(key, e instanceof ExecutionException? e.getMessage() : ExceptionUtil.getUserStackTrace(e, LOG), processHandler);
      return;
    }
    processHandler.addProcessListener(getProcessListener(key));
    processHandler.startNotify();
  }

  protected abstract RunProfileState getRunProfileState(Target target, Parameters configuration, Executor executor)
    throws ExecutionException;

  private boolean getExistingInfo(Ref<RunningInfo> ref, Pair<Target, Parameters> key) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      try {
        while (info != null && (!(info instanceof RunningInfo) ||
                                info.handler.isProcessTerminating() ||
                                info.handler.isProcessTerminated())) {
          myProcMap.wait(1000);
          ProgressManager.checkCanceled();
          info = myProcMap.get(key);
        }
      }
      catch (InterruptedException e) {
        ProgressManager.checkCanceled();
      }
      if (info == null) {
        myProcMap.put(key, new PendingInfo(ref, null));
      }
    }
    if (info instanceof RunningInfo) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (ref) {
        ref.set((RunningInfo)info);
        ref.notifyAll();
      }
    }
    return info != null;
  }

  private EntryPoint acquire(final RunningInfo port) throws Exception {
    EntryPoint result = RemoteUtil.executeWithClassLoader(() -> {
      Registry registry = LocateRegistry.getRegistry("localhost", port.port);
      Remote remote = registry.lookup(port.name);
      if (Remote.class.isAssignableFrom(myValueClass)) {
        return RemoteUtil.substituteClassLoader(narrowImpl(remote, myValueClass), myValueClass.getClassLoader());
      }
      else {
        return RemoteUtil.castToLocal(remote, myValueClass);
      }
    }, getClass().getClassLoader()); // should be the loader of client plugin
    // init hard ref that will keep it from DGC and thus preventing from System.exit
    port.entryPointHardRef = result;
    return result;
  }

  private static <T> T narrowImpl(Remote remote, Class<T> to) {
    //noinspection unchecked
    return to.isInstance(remote) ? (T)remote : null;
  }

  private ProcessListener getProcessListener(final Pair<Target, Parameters> key) {
    return new ProcessListener() {
      @Override
      public void startNotified(ProcessEvent event) {
        ProcessHandler processHandler = event.getProcessHandler();
        processHandler.putUserData(BaseProcessHandler.SILENTLY_DESTROY_ON_CLOSE, Boolean.TRUE);
        Info o;
        synchronized (myProcMap) {
          o = myProcMap.get(key);
          if (o instanceof PendingInfo) {
            myProcMap.put(key, new PendingInfo(((PendingInfo)o).ref, processHandler));
          }
        }
      }

      @Override
      public void processTerminated(ProcessEvent event) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        if (dropProcessInfo(key, null, event.getProcessHandler())) {
          fireModificationCountChanged();
        }
      }

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String text = StringUtil.notNullize(event.getText());
        if (outputType == ProcessOutputTypes.STDERR) {
          LOG.warn(text.trim());
        }
        else {
          LOG.info(text.trim());
        }

        RunningInfo result = null;
        PendingInfo info;
        synchronized (myProcMap) {
          Info o = myProcMap.get(key);
          logText(key.second, event, outputType, o);
          if (o instanceof PendingInfo) {
            info = (PendingInfo)o;
            if (outputType == ProcessOutputTypes.STDOUT) {
              String prefix = "Port/ID:";
              if (text.startsWith(prefix)) {
                String pair = text.substring(prefix.length()).trim();
                int idx = pair.indexOf("/");
                result = new RunningInfo(info.handler, Integer.parseInt(pair.substring(0, idx)), pair.substring(idx + 1));
                myProcMap.put(key, result);
                myProcMap.notifyAll();
              }
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
              info.stderr.append(text);
            }
          }
          else {
            info = null;
          }
        }
        if (result != null) {
          synchronized (info.ref) {
            info.ref.set(result);
            info.ref.notifyAll();
          }
          fireModificationCountChanged();
          try {
            RemoteDeadHand.TwoMinutesTurkish.startCooking("localhost", result.port);
          }
          catch (Exception e) {
            LOG.warn("The cook failed to start due to " + ExceptionUtil.getRootCause(e));
          }
        }
      }
    };
  }

  private boolean dropProcessInfo(Pair<Target, Parameters> key, @Nullable String errorMessage, @Nullable ProcessHandler handler) {
    Info info;
    synchronized (myProcMap) {
      info = myProcMap.get(key);
      if (info != null && (handler == null || info.handler == handler)) {
        myProcMap.remove(key);
        myProcMap.notifyAll();
      }
      else {
        // different processHandler
        info = null;
      }
    }
    if (info instanceof PendingInfo) {
      PendingInfo pendingInfo = (PendingInfo)info;
      if (pendingInfo.stderr.length() > 0 || pendingInfo.ref.isNull()) {
        if (errorMessage != null) pendingInfo.stderr.append(errorMessage);
        pendingInfo.ref.set(new RunningInfo(null, -1, pendingInfo.stderr.toString()));
      }
      synchronized (pendingInfo.ref) {
        pendingInfo.ref.notifyAll();
      }
    }
    return info != null;
  }

  private static class Info {
    final ProcessHandler handler;

    Info(ProcessHandler handler) {
      this.handler = handler;
    }
  }

  private static class PendingInfo extends Info {
    final Ref<RunningInfo> ref;
    final StringBuilder stderr = new StringBuilder();

    PendingInfo(Ref<RunningInfo> ref, ProcessHandler handler) {
      super(handler);
      this.ref = ref;
    }

  }

  private static class RunningInfo extends Info {
    final int port;
    final String name;
    Object entryPointHardRef;

    RunningInfo(ProcessHandler handler, int port, String name) {
      super(handler);
      this.port = port;
      this.name = name;
    }
  }

}
