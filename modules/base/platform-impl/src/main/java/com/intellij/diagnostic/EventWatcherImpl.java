// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import consulo.container.boot.ContainerPathManager;
import consulo.container.classloader.PluginClassLoader;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.util.lang.reflect.ReflectionUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.diagnostic.RunnablesListener.*;
import static com.intellij.util.ReflectionUtil.isAssignable;

//@ApiStatus.Experimental
public final class EventWatcherImpl implements EventWatcher, Disposable {
  private static final int PUBLISHER_INITIAL_DELAY = 100;
  private static final int PUBLISHER_PERIOD = 1000;

  @Nonnull
  private static final Logger LOG = Logger.getInstance(EventWatcherImpl.class);
  @Nonnull
  private static final Pattern DESCRIPTION_BY_EVENT = Pattern.compile("(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\[(?<description>\\w+(,runnable=(?<runnable>[^,]+))?[^]]*)].*");

  @Nonnull
  private final ConcurrentMap<String, WrapperDescription> myWrappers = new ConcurrentHashMap<>();
  @Nonnull
  private final ConcurrentMap<String, InvocationsInfo> myDurationsByFqn = new ConcurrentHashMap<>();
  @Nonnull
  private final ConcurrentLinkedQueue<InvocationDescription> myRunnables = new ConcurrentLinkedQueue<>();
  @Nonnull
  private final ConcurrentMap<Class<? extends AWTEvent>, ConcurrentLinkedQueue<InvocationDescription>> myEventsByClass = new ConcurrentHashMap<>();
  private final
  @Nonnull
  ConcurrentMap<Long, Class<?>> myRunnablesOrCallablesInProgress = new ConcurrentHashMap<>();
  private final
  @Nonnull
  ConcurrentMap<String, LockAcquirementDescription> myAcquirements = new ConcurrentHashMap<>();

  @Nonnull
  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("EDT Events Logger", 1);
  @Nonnull
  private final ScheduledFuture<?> myThread = myExecutor.scheduleWithFixedDelay(this::dumpDescriptions, PUBLISHER_INITIAL_DELAY, PUBLISHER_PERIOD, TimeUnit.MILLISECONDS);

  private final
  @Nonnull
  LogFileWriter myWriter = new LogFileWriter();
  private final
  @Nonnull
  MessageBus myMessageBus;

  @Nullable
  private MatchResult myCurrentResult = null;

  public EventWatcherImpl(@Nonnull MessageBus messageBus) {
    myMessageBus = messageBus;
    myMessageBus.connect(this).subscribe(TOPIC, myWriter);
  }

  @Override
  public void logTimeMillis(@Nonnull String processId, long startedAt, @Nonnull Class<? extends Runnable> runnableClass) {
    InvocationDescription description = new InvocationDescription(processId, startedAt);
    logTimeMillis(description, runnableClass);
  }

  @Override
  public void runnableStarted(@Nonnull Runnable runnable, long startedAt) {
    Object current = runnable;

    while (current != null) {
      Class<?> rootClass = current.getClass();
      Field field = findCallableOrRunnableField(rootClass);

      if (field != null) {
        myWrappers.compute(rootClass.getName(), WrapperDescription::computeNext);
        current = ReflectionUtil.getFieldValue(field, current);
      }
      else {
        break;
      }
    }

    myRunnablesOrCallablesInProgress.put(startedAt, (current != null ? current : runnable).getClass());
  }

  @Override
  public void runnableFinished(@Nonnull Runnable runnable, long startedAt) {
    Class<?> runnableOrCallableClass = Objects.requireNonNull(myRunnablesOrCallablesInProgress.remove(startedAt));
    String fqn = runnableOrCallableClass.getName();

    InvocationDescription description = new InvocationDescription(fqn, startedAt);
    myRunnables.offer(description);
    myDurationsByFqn.compute(fqn, (ignored, info) -> InvocationsInfo.computeNext(fqn, description.getDuration(), info));

    logTimeMillis(description, runnableOrCallableClass);
  }

  @Override
  public void edtEventStarted(@Nonnull AWTEvent event) {
    Matcher matcher = DESCRIPTION_BY_EVENT.matcher(event.toString());
    myCurrentResult = matcher.find() ? matcher.toMatchResult() : null;
  }

  @Override
  public void edtEventFinished(@Nonnull AWTEvent event, long startedAt) {
    String representation = myCurrentResult instanceof Matcher ? ((Matcher)myCurrentResult).group("description") : event.toString();
    myCurrentResult = null;

    Class<? extends AWTEvent> eventClass = event.getClass();
    myEventsByClass.putIfAbsent(eventClass, new ConcurrentLinkedQueue<>());
    myEventsByClass.get(eventClass).offer(new InvocationDescription(representation, startedAt));
  }

  @Override
  public void lockAcquired(@Nonnull String invokedClassFqn, @Nonnull LockKind lockKind) {
    myAcquirements.compute(invokedClassFqn, (fqn, description) -> LockAcquirementDescription.computeNext(fqn, description, lockKind));
  }

  @Override
  public void dispose() {
    Disposer.dispose(myWriter);

    myThread.cancel(true);
    myExecutor.shutdownNow();
  }

  private void dumpDescriptions() {
    if (myMessageBus.isDisposed()) return;

    RunnablesListener publisher = myMessageBus.syncPublisher(TOPIC);
    myEventsByClass.forEach((eventClass, events) -> publisher.eventsProcessed(eventClass, joinPolling(events)));
    publisher.runnablesProcessed(joinPolling(myRunnables), myDurationsByFqn.values(), myWrappers.values());
    publisher.locksAcquired(myAcquirements.values());
  }

  @Nullable
  private static Field findCallableOrRunnableField(@Nonnull Class<?> rootClass) {
    return ReflectionUtil.findFieldInHierarchy(rootClass, field -> ReflectionUtil.isInstanceField(field) && isCallableOrRunnable(field));
  }

  private static boolean isCallableOrRunnable(@Nonnull Field field) {
    Class<?> fieldType = field.getType();
    return isAssignable(Runnable.class, fieldType) || isAssignable(Callable.class, fieldType);
  }

  @Nonnull
  private static <T> List<T> joinPolling(@Nonnull Queue<T> queue) {
    ArrayList<T> builder = new ArrayList<>();
    while (!queue.isEmpty()) {
      builder.add(queue.poll());
    }
    return Collections.unmodifiableList(builder);
  }

  private static void logTimeMillis(@Nonnull InvocationDescription description, @Nonnull Class<?> runnableClass) {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();

    int threshold = Registry.intValue("ide.event.queue.dispatch.threshold", -1);
    if (threshold < 0 || threshold > description.getDuration()) {
      return; // do not measure a time if the threshold is too small
    }

    LOG.warn(description.toString());

    if (runnableClass != Runnable.class) {
      addPluginCost(runnableClass, description.getDuration());
    }
  }

  private static void addPluginCost(@Nonnull Class<?> runnableClass, long duration) {
    ClassLoader loader = runnableClass.getClassLoader();
    String pluginId = loader instanceof PluginClassLoader ? ((PluginClassLoader)loader).getPluginId().getIdString() : PluginManagerCore.CORE_PLUGIN_ID;

    StartUpMeasurer.addPluginCost(pluginId, "invokeLater", TimeUnit.MILLISECONDS.toNanos(duration));
  }

  private static final class LogFileWriter implements RunnablesListener, Disposable {

    private final
    @Nonnull
    File myLogDir = new File(new File(ContainerPathManager.get().getLogPath(), "edt-log"), String.format("%tY%<tm%<td-%<tH%<tM%<tS", System.currentTimeMillis()));

    private final
    @Nonnull
    Map<String, InvocationsInfo> myInfos = new HashMap<>();
    private final
    @Nonnull
    Map<String, WrapperDescription> myWrappers = new HashMap<>();

    @Override
    public void eventsProcessed(@Nonnull Class<? extends AWTEvent> eventClass, @Nonnull Collection<InvocationDescription> descriptions) {
      appendToFile(eventClass.getSimpleName(), descriptions.stream());
    }

    @Override
    public void runnablesProcessed(@Nonnull Collection<InvocationDescription> invocations, @Nonnull Collection<InvocationsInfo> infos, @Nonnull Collection<WrapperDescription> wrappers) {
      appendToFile("Runnables", invocations.stream());

      putAllTo(infos, InvocationsInfo::getFQN, myInfos);
      putAllTo(wrappers, WrapperDescription::getFQN, myWrappers);
    }

    @Override
    public void dispose() {
      writeToFile("Timings", myInfos);
      writeToFile("Wrappers", myWrappers);
    }

    private <T> void appendToFile(@Nonnull String kind, @Nonnull Stream<T> lines) {
      if (!(myLogDir.isDirectory() || myLogDir.mkdirs())) {
        LOG.debug(myLogDir.getAbsolutePath() + " cannot be created");
        return;
      }

      try {
        FileUtil.writeToFile(new File(myLogDir, kind + ".log"), lines.map(Objects::toString).collect(Collectors.joining("\n")), true);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }

    private <K, V> void writeToFile(@Nonnull String kind, @Nonnull Map<K, V> entities) {
      appendToFile(kind, entities.values().stream().sorted());
    }

    private static <E> void putAllTo(@Nonnull Collection<E> entities, @Nonnull Function<? super E, String> mapper, @Nonnull Map<String, E> map) {
      Map<String, E> entitiesMap = entities.stream().collect(Collectors.toMap(mapper, Function.identity()));
      map.putAll(entitiesMap);
    }
  }
}
