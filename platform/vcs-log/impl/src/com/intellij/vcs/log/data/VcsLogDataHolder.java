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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Holds the commit data loaded from the VCS, and is capable to refresh this data by
 * {@link VcsLogProvider#subscribeToRootRefreshEvents(Collection, VcsLogRefresher) events from the VCS}.</p>
 * <p>If refresh is in progress, {@link #getDataPack()} returns the previous data pack (possibly not actual anymore).
 *    When refresh completes, the data pack instance is updated. Refreshes are chained.</p>
 *
 * <p><b>Thread-safety:</b> the class is thread-safe:
 *   <ul>
 *     <li>All write-operations made to the log and data pack are made sequentially, from the same background queue;</li>
 *     <li>Once a refresh request is received, we read logs and refs from all providers, join refresh data, join repositories,
 *         and build the new data pack sequentially in a single thread. After that we substitute the instance of the LogData object.</li>
 *     <li>Whilst we are in the middle of this refresh process, anyone who requests the data pack (and possibly the log if this would
 *         be available in the future), will get the consistent previous version of it.</li>
 *   </ul></p>
 *
 * <p><b>Initialization:</b><ul>
 *    <li>Initially the first part of the log is loaded and is shown to the user.</li>
 *    <li>Right after that, the whole log is loaded in the background. We need the whole log for two reasons:
 *        we don't want to hang for a minute if user decides to go to an old commit or even scroll;
 *        we need the whole log to properly perform the optimized refresh procedure.</li>
 *    <li>Once the whole log information is loaded, we don't rebuild the graphical log, because users rarely need old commits while
 *        memory would be occupied and performance would suffer.</li>
 *    <li>If user requests a commit that is not displayed (by clicking on an old branch reference, by going to a hash,
 *        just by scrolling down to the end of the table), we take the whole log, build the graph and show to the user.</li>
 *    <li>Once the whole log was once requested, we never hide it after refresh, because the reverse could annoy the user
 *        if he was looking at old history when refresh happened.</li>
 *    </li></ul>
 * </p>
 *
 * <p><b>Refresh procedure:</b><ul>
 *    <li>When a refresh request comes, we ask {@link VcsLogProvider log providers} about the last N commits unordered (which
 *        adds a significant performance gain in the case of Git).</li>
 *    <li>Then we order and attach these commits to the log with the help of {@link VcsLogJoiner}.</li>
 *    <li>In the multiple repository case logs from different providers are joined together to a single log.</li>
 *    <li>If user was looking at the top part of the log, the log is rebuilt, and new top of the log is shown to the user.
 *        If, however, he was looking at the whole log, the data pack for the whole log is rebuilt and shown to the user.
 *    </li></ul></p>
 *
 * TODO: error handling
 *
 * @author Kirill Likhodedov
 */
public class VcsLogDataHolder implements Disposable {

  public static final Topic<Runnable> REFRESH_COMPLETED = Topic.create("Vcs.Log.Completed", Runnable.class);

  private static final Logger LOG = Logger.getInstance(VcsLogDataHolder.class);

  /**
   * Don't load more than ourDetailsLoadingRateLimit times of standard recent commits details per repository.
   * If this amount of commits is not enough, ask the VCS directly.
   */
  private static final int MORE_DETAILS_LOADING_RATE_LIMIT = 10;

  /**
   * If more details of recent commits are requested, multiply the number of commits to load by this value on each step,
   * until this value reaches {@link #MORE_DETAILS_LOADING_RATE_LIMIT} times of
   * {@link VcsLogSettings#getRecentCommitsCount() the standard recent commits count}.
   */
  public static final int MORE_DETAILS_LOADING_STEP_MULTIPLIER = 10;

  @NotNull private final Project myProject;
  @NotNull private final VcsLogObjectsFactory myFactory;
  @NotNull private final Map<VirtualFile, VcsLogProvider> myLogProviders;
  @NotNull private final BackgroundTaskQueue myDataLoaderQueue;
  @NotNull private final MiniDetailsGetter myMiniDetailsGetter;
  @NotNull private final CommitDetailsGetter myDetailsGetter;
  @NotNull private final VcsLogJoiner myLogJoiner;
  @NotNull private final VcsLogMultiRepoJoiner myMultiRepoJoiner;
  @NotNull private final VcsLogSettings mySettings;

  /**
   * Current user name, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  private final Map<VirtualFile, VcsUser> myCurrentUser = ContainerUtil.newHashMap();

  /**
   * Encapsulates all information about the log, which can be accessed by external clients.
   * When something changes in the log (on refresh, for example), the whole object is replaced with the new one;
   * all write-access operations with are performed from the myDataLoaderQueue;
   * therefore it is guaranteed that myData doesn't change within a single runInBackground() task.
   *
   * The object is null only before the first refresh, the whole component is not available before the first refresh at all.
   */
  private volatile LogData myLogData;

  /**
   * Indicates if user wants the whole log graph to be shown.
   * Initially we show only the top of the log (even after we've loaded the whole log structure) for performance reasons.
   * However, if user once navigates to some old commit, we build the whole graph and show it until the next full refresh or project reload.
   */
  private volatile boolean myFullLogShowing;

  /**
   * Cached details of the latest commits.
   * We store them separately from the cache of {@link DataGetter}, to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  @NotNull private final Map<Hash, VcsFullCommitDetails> myTopCommitsDetailsCache = ContainerUtil.newConcurrentMap();

  /**
   * Checks if "load more commit details" process is already in progress to avoid scheduling multiple similar processes.
   */
  private final AtomicBoolean myLoadMoreInProgress = new AtomicBoolean(false);

  /**
   * One-time latch that lets wait until the entire log skeleton is loaded.
   * It is reinitialized on full refresh.
   */
  private CountDownLatch myEntireLogLoadWaiter;
  private final VcsUserRegistry myUserRegistry;

  public VcsLogDataHolder(@NotNull Project project,
                          @NotNull Map<VirtualFile, VcsLogProvider> logProviders, @NotNull VcsLogSettings settings) {
    myProject = project;
    myLogProviders = logProviders;
    myDataLoaderQueue = new BackgroundTaskQueue(project, "Loading history...");
    myMiniDetailsGetter = new MiniDetailsGetter(this, logProviders);
    myDetailsGetter = new CommitDetailsGetter(this, logProviders);
    myLogJoiner = new VcsLogJoiner();
    myMultiRepoJoiner = new VcsLogMultiRepoJoiner();
    myFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
    mySettings = settings;
    myUserRegistry = new VcsUserRegistry();
  }

  public void initialize(@NotNull final Consumer<VcsLogDataHolder> onInitialized) {
    // complete refresh => other scheduled refreshes are not interesting
    // TODO: interrupt the current task as well instead of waiting for it to finish, since the result is invalid anyway
    myDataLoaderQueue.clear();

    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        resetState();
        readCurrentUser();
        loadFromVcs(mySettings.getRecentCommitsCount(), indicator, new Consumer<DataPack>() {
          @Override
          public void consume(DataPack dataPack) {
            myEntireLogLoadWaiter.countDown(); // make sure to release any potential waiters of the previous latch
            myEntireLogLoadWaiter = new CountDownLatch(1);

            onInitialized.consume(VcsLogDataHolder.this);
            loadAllLog(); // after first part is loaded and shown to the user, start loading the whole log in background
          }
        });
      }
    }, "Loading recent history...");
  }

  private void readCurrentUser() {
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
      VirtualFile root = entry.getKey();
      try {
        VcsUser me = entry.getValue().getCurrentUser(root);
        if (me != null) {
          myCurrentUser.put(root, me);
        }
        else {
          LOG.info("Username not configured for root " + root);
        }
      }
      catch (VcsException e) {
        LOG.warn("Couldn't read the username from root " + root, e);
      }
    }
  }

  private void resetState() {
    // note: myLogData is not nullified: we want the log still be accessible even during the complete refresh

    myFullLogShowing = false;
    myTopCommitsDetailsCache.clear();
    myLoadMoreInProgress.set(false);

    if (myEntireLogLoadWaiter != null) {
      // make sure that all waiters are released;
      // they may perform some action after this release,
      // but it is not important since the log will be rebuilt soon, once the initial part of refresh completes.
      myEntireLogLoadWaiter.countDown();
    }
    myEntireLogLoadWaiter = new CountDownLatch(1);
  }

  private void loadAllLog() {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        try {
          Consumer<VcsUser> userRegistry = new Consumer<VcsUser>() {
            @Override
            public void consume(VcsUser user) {
              myUserRegistry.addUser(user);
            }
          };
          Map<VirtualFile, List<TimedVcsCommit>> logs = ContainerUtil.newHashMap();
          Map<VirtualFile, Collection<VcsRef>> refs = ContainerUtil.newHashMap();
          for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
            VirtualFile root = entry.getKey();
            VcsLogProvider logProvider = entry.getValue();
            logs.put(root, logProvider.readAllHashes(root, userRegistry));
            refs.put(root, logProvider.readAllRefs(root));
          }
          DataPack existingDataPack = myLogData.getDataPack();
          // keep existing data pack: we don't want to rebuild the graph,
          // we just make the whole log structure available for our cunning refresh procedure of if user requests the whole graph
          myLogData = new LogData(logs, refs, myLogData.getTopCommits(), existingDataPack, true);
        }
        finally {
          myEntireLogLoadWaiter.countDown();
        }
      }
    }, "Loading log structure...");
  }

  /**
   * Show the full log tree to the user.
   * Initially only the top part of the log is shown to avoid memory and performance problems.
   * However, if user wants to navigate to something in the past, we rebuild the log and show it.
   * <p/>
   * Method returns immediately, log building is executed in the background.
   * <p/>
   * TODO: in most cases, users don't need to go to such deep past even if they need to go deeper than to 1000 most recent commits.
   * Therefore optimize: Add a hash parameter, and build only the necessary part of the log + some commits below.
   *
   * @param onSuccess Invoked in the EDT after the log DataPack is built.
   */
  public void showFullLog(@NotNull final Runnable onSuccess) {
    if (myFullLogShowing) {
      return;
    }

    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        if (myFullLogShowing) {
          return;
        }

//        try {
//          myEntireLogLoadWaiter.await();
//        }
//        catch (InterruptedException e) {
//          throw new RuntimeException(e);
//        }

        List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(myLogData.myLogsByRoot.values());
        DataPack fullDataPack = DataPack.build(compoundLog, myLogData.getAllRefs(), indicator);
        myLogData = new LogData(myLogData.getLogs(), myLogData.getRefs(), myLogData.getTopCommits(), fullDataPack, true);
        myFullLogShowing = true;
        invokeAndWait(new Runnable() {
          @Override
          public void run() {
            notifyAboutDataRefresh();
            onSuccess.run();
          }
        });
      }
    }, "Building full log...");
  }

  /**
   * Queries the VCS for the number of recent unordered commits, orders them and connects to the existing log structure.
   * This is done after refresh, when the whole log skeleton has been loaded.
   */
  private void smartRefresh(ProgressIndicator indicator, Consumer<DataPack> onSuccess) throws VcsException {
    if (myLogData == null || !myLogData.isFullLogReady()) {
      LOG.error("The full log is not ready!");
    }

    Map<VirtualFile, List<TimedVcsCommit>> logsToBuild = ContainerUtil.newHashMap();
    Map<VirtualFile, Collection<VcsRef>> refsByRoot = ContainerUtil.newHashMap();
    int topCommitCount = myLogData.getTopCommitsCount();

    for (Map.Entry<VirtualFile, RecentCommitsInfo> entry : collectInfoFromVcs(false, mySettings.getRecentCommitsCount())) {
      VirtualFile root = entry.getKey();
      RecentCommitsInfo info = entry.getValue();

      Pair<List<TimedVcsCommit>, Integer> joinResult = myLogJoiner.addCommits(myLogData.getLog(root), myLogData.getRefs(root),
                                                                              info.firstBlockCommits, info.newRefs);
      List<TimedVcsCommit> refreshedLog = joinResult.getFirst();
      int newCommitsCount = joinResult.getSecond();
      // the value can significantly increase if user keeps IDEA open for a long time, and frequently receives many new commits,
      // but it is expected: we can work with long logs. A limit can be added in future if this becomes a problem.
      topCommitCount += newCommitsCount;

      logsToBuild.put(root, refreshedLog);
      refsByRoot.put(root, info.newRefs);
    }

    List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(logsToBuild.values());
    List<TimedVcsCommit> topPartOfTheLog = compoundLog.subList(0, topCommitCount);

    List<TimedVcsCommit> logToBuild = myFullLogShowing ? compoundLog : topPartOfTheLog; // keep looking at the full log after refresh
    DataPack dataPack = DataPack.build(logToBuild, collectAllRefs(refsByRoot), indicator);

    myLogData = new LogData(logsToBuild, refsByRoot, topPartOfTheLog, dataPack, true);

    handleOnSuccessInEdt(onSuccess, dataPack);
  }

  /**
   * Queries the given number of commits (per each root) from the VCS, already sorted, and substitutes the "top commits" set by them.
   * This is done initially, and when more commit details is needed.
   * <p/>
   * The whole log data is substituted with the retrieved part of the log only if it hasn't been loaded yet.
   * Otherwise the previous log data is reused, and only top commits are substituted.
   * <p/>
   * This is not intended to be used for an ordinary refresh, because it assumes that no new commits have arrived, and therefore
   * doesn't change the saved log skeleton.
   */
  private void loadFromVcs(int commitCount, ProgressIndicator indicator, final Consumer<DataPack> onSuccess) throws VcsException {
    Map<VirtualFile, List<TimedVcsCommit>> logsToBuild = ContainerUtil.newHashMap();
    Map<VirtualFile, Collection<VcsRef>> refsByRoot = ContainerUtil.newHashMap();

    for (Map.Entry<VirtualFile, RecentCommitsInfo> entry : collectInfoFromVcs(true, commitCount)) {
      VirtualFile root = entry.getKey();
      RecentCommitsInfo info = entry.getValue();

      // in this case new commits won't be attached to the log, but will substitute existing ones.
      logsToBuild.put(root, info.firstBlockCommits);
      refsByRoot.put(root, info.newRefs);
    }

    List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(logsToBuild.values());

    // even if the full log was already loaded (and possibly presented to the user),
    // build only the data that was retrieved from the VCS:
    // if it is not one of the initial refreshes, then it is filtering, and then the DataPack will change anyway.
    DataPack dataPack = DataPack.build(compoundLog, collectAllRefs(refsByRoot), indicator);

    if (myLogData != null && myLogData.isFullLogReady()) {
      // reuse the skeleton, since it didn't change, because it is not a refresh
      myLogData = new LogData(myLogData.getLogs(), myLogData.getRefs(), compoundLog, dataPack, true);
    }
    else {
      // full skeleton was not retrieved yet => use commits that we've got from the VCS
      myLogData = new LogData(logsToBuild, refsByRoot, compoundLog, dataPack, false);
    }

    handleOnSuccessInEdt(onSuccess, dataPack);
  }

  private Set<Map.Entry<VirtualFile, RecentCommitsInfo>> collectInfoFromVcs(boolean ordered, int commitsCount) throws VcsException {
    Map<VirtualFile, RecentCommitsInfo> infoByRoot = ContainerUtil.newHashMap();
    for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
      VirtualFile root = entry.getKey();
      VcsLogProvider logProvider = entry.getValue();

      List<? extends VcsFullCommitDetails> firstBlockDetails = logProvider.readFirstBlock(root, ordered, commitsCount);
      Collection<VcsRef> newRefs = logProvider.readAllRefs(root);
      storeTopCommitsDetailsInCache(firstBlockDetails);
      storeUsers(firstBlockDetails);
      List<TimedVcsCommit> firstBlockCommits = getCommitsFromDetails(firstBlockDetails);

      infoByRoot.put(root, new RecentCommitsInfo(firstBlockCommits, newRefs));
    }
    return infoByRoot.entrySet();
  }

  private void storeUsers(@NotNull List<? extends VcsFullCommitDetails> details) {
    for (VcsFullCommitDetails detail : details) {
      myUserRegistry.addUser(detail.getAuthor());
      myUserRegistry.addUser(detail.getCommitter());
    }
  }

  @NotNull
  public Set<VcsUser> getAllUsers() {
    return myUserRegistry.getUsers();
  }

  public void getFilteredDetailsFromTheVcs(final Collection<VcsLogFilter> filters, final Consumer<List<VcsFullCommitDetails>> success) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {

        Collection<List<TimedVcsCommit>> logs = ContainerUtil.newArrayList();
        final Map<Hash, VcsFullCommitDetails> allDetails = ContainerUtil.newHashMap();
        for (Map.Entry<VirtualFile, VcsLogProvider> entry : myLogProviders.entrySet()) {
          List<? extends VcsFullCommitDetails> details = entry.getValue().getFilteredDetails(entry.getKey(), filters);
          logs.add(getCommitsFromDetails(details));
          for (VcsFullCommitDetails detail : details) {
            allDetails.put(detail.getHash(), detail);
          }
        }

        final List<TimedVcsCommit> compoundLog = myMultiRepoJoiner.join(logs);

        final List<VcsFullCommitDetails> list = ContainerUtil.mapNotNull(compoundLog, new Function<TimedVcsCommit, VcsFullCommitDetails>() {
          @Override
          public VcsFullCommitDetails fun(TimedVcsCommit commit) {
            VcsFullCommitDetails detail = allDetails.get(commit.getHash());
            if (detail == null) {
              String message = "Details not stored for commit " + commit;
              if (LOG.isDebugEnabled()) {
                LOG.error(message, new Attachment("filtered_details", allDetails.toString()),
                                   new Attachment("compound_log", compoundLog.toString()));
              }
              else {
                LOG.error(message);
              }
            }
            return detail;
          }
        });

        myDetailsGetter.saveInCache(list);
        myMiniDetailsGetter.saveInCache(list);

        invokeAndWait(new Runnable() {
          @Override
          public void run() {
            success.consume(list);
          }
        });
      }
    }, "Looking for more results...");
  }

  @NotNull
  public Map<VirtualFile, VcsUser> getCurrentUser() {
    return myCurrentUser;
  }

  public boolean isMultiRoot() {
    return myLogProviders.size() > 1;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public VcsUserRegistry getUserRegistry() {
    return myUserRegistry;
  }

  private static class RecentCommitsInfo {
    List<TimedVcsCommit> firstBlockCommits;
    Collection<VcsRef> newRefs;

    RecentCommitsInfo(List<TimedVcsCommit> commits, Collection<VcsRef> refs) {
      firstBlockCommits = commits;
      newRefs = refs;
    }
  }

  private static Collection<VcsRef> collectAllRefs(Map<VirtualFile, Collection<VcsRef>> refsByRoot) {
    Collection<VcsRef> allRefs = new ArrayList<VcsRef>();
    for (Collection<VcsRef> refs : refsByRoot.values()) {
      allRefs.addAll(refs);
    }
    return allRefs;
  }

  private void handleOnSuccessInEdt(final Consumer<DataPack> onSuccess, final DataPack dataPack) {
    invokeAndWait(new Runnable() {
      @Override
      public void run() {
        onSuccess.consume(dataPack);
      }
    });
  }

  private void storeTopCommitsDetailsInCache(List<? extends VcsFullCommitDetails> firstBlockDetails) {
    // some commits may be no longer available (e.g. rewritten after rebase), but let them stay in the cache:
    // they won't occupy too much place, while checking & removing them is not easy.
    for (VcsFullCommitDetails detail : firstBlockDetails) {
      myTopCommitsDetailsCache.put(detail.getHash(), detail);
    }
  }

  private List<TimedVcsCommit> getCommitsFromDetails(List<? extends VcsFullCommitDetails> firstBlockDetails) {
    return ContainerUtil.map(firstBlockDetails, new Function<VcsFullCommitDetails, TimedVcsCommit>() {
      @Override
      public TimedVcsCommit fun(VcsFullCommitDetails details) {
        return myFactory.createTimedCommit(details.getHash(), details.getParents(), details.getAuthorTime());
      }
    });
  }

  /**
   * The state of "load more" procedure.
   */
  public enum LoadingState {
    /**
     * currently in the process of loading more details;
     */
    LOADING,
    /**
     * details of all commit of the entire history have been loaded, nothing to load more
     */
    ALL_COMMITS_LOADED,
    /**
     * reached the limit to load more commit details; need to query the VCS directly if needed.
     */
    LIMIT_REACHED
  }

  @NotNull
  public LoadingState loadMoreDetails(@NotNull final Runnable onSuccess) {
    if (myLoadMoreInProgress.get()) { // quick check
      return LoadingState.LOADING;
    }

    final int topCommitsCount = myLogData.getTopCommitsCount();
    int rootsCount = getRootCount();
    if (topCommitsCount >= MORE_DETAILS_LOADING_RATE_LIMIT * mySettings.getRecentCommitsCount() * rootsCount) {
      return LoadingState.LIMIT_REACHED;
    }

    if (entireLogDetailsLoaded()) { // the entire history was small enough
      return LoadingState.ALL_COMMITS_LOADED;
    }

    if (myLogData.isFullLogReady()) {
      int totalSize = 0;
      for (List<TimedVcsCommit> commits : myLogData.getLogs().values()) {
        totalSize += commits.size();
      }
      if (topCommitsCount >= totalSize) {
        // nothing more to load
        return LoadingState.ALL_COMMITS_LOADED;
      }
    }

    if (myLoadMoreInProgress.compareAndSet(false, true)) {
      runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
        @Override
        public void consume(ProgressIndicator indicator) throws VcsException {
          loadFromVcs(topCommitsCount * MORE_DETAILS_LOADING_STEP_MULTIPLIER, indicator, new Consumer<DataPack>() {
            @Override
            public void consume(DataPack dataPack) {
              myLoadMoreInProgress.set(false);
              onSuccess.run();
            }
          });
        }
      }, "Loading more details to filter...");
    }
    return LoadingState.LOADING;
  }

  private int getRootCount() {
    return myLogProviders.keySet().size();
  }

  private boolean entireLogDetailsLoaded() {
    LogData logData = myLogData;
    if (logData.isFullLogReady()) {
      int totalSize = 0;
      for (List<TimedVcsCommit> commits : logData.getLogs().values()) {
        totalSize += commits.size();
      }
      if (logData.getTopCommitsCount() >= totalSize) {
        return true;
      }
    }
    return false;
  }

  private void runInBackground(final ThrowableConsumer<ProgressIndicator, VcsException> task, final String title) {
    myDataLoaderQueue.run(new Task.Backgroundable(myProject, title) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          task.consume(indicator);
        }
        catch (VcsException e) {
          throw new RuntimeException(e); // TODO
        }
      }
    });
  }

  private void refresh(@NotNull final Runnable onSuccess) {
    runInBackground(new ThrowableConsumer<ProgressIndicator, VcsException>() {
      @Override
      public void consume(ProgressIndicator indicator) throws VcsException {
        Consumer<DataPack> success = new Consumer<DataPack>() {
          @Override
          public void consume(DataPack dataPack) {
            onSuccess.run();
          }
        };

        if (myLogData.isFullLogReady()) {
          smartRefresh(indicator, success);
        }
        else {
          loadFromVcs(mySettings.getRecentCommitsCount(), indicator, success);
        }
      }
    }, "Refreshing history...");
  }

  /**
   * Makes the log perform complete refresh for all roots.
   * It fairly retrieves the data from the VCS and rebuilds the whole log.
   */
  public void refreshCompletely() {
    initialize(new Consumer<VcsLogDataHolder>() {
      @Override
      public void consume(VcsLogDataHolder holder) {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i. e. it can query VCS just for the part of the log.
   */
  public void refresh(@NotNull VirtualFile root) {
    // TODO refresh only the given root, not all roots
    refresh(new Runnable() {
      @Override
      public void run() {
        notifyAboutDataRefresh();
      }
    });
  }

  /**
   * Makes the log refresh only the reference labels for the given root.
   */
  public void refreshRefs(@NotNull VirtualFile root) {
    // TODO no need to query the VCS for commit & rebuild the whole log; just replace refs labels.
    refresh(root);
  }

  @NotNull
  public DataPack getDataPack() {
    return myLogData.getDataPack();
  }

  @Nullable
  public VcsFullCommitDetails getTopCommitDetails(@NotNull Hash hash) {
    return myTopCommitsDetailsCache.get(hash);
  }

  private void notifyAboutDataRefresh() {
    if (!myProject.isDisposed()) {
      myProject.getMessageBus().syncPublisher(REFRESH_COMPLETED).run();
    }
  }

  public CommitDetailsGetter getCommitDetailsGetter() {
    return myDetailsGetter;
  }

  @NotNull
  public MiniDetailsGetter getMiniDetailsGetter() {
    return myMiniDetailsGetter;
  }

  @Override
  public void dispose() {
    myDataLoaderQueue.clear();
    myLogData = null;
    resetState();
  }

  @NotNull
  public VcsLogProvider getLogProvider(@NotNull VirtualFile root) {
    return myLogProviders.get(root);
  }

  /**
   * Returns the ordered part of the recent commits, details of which are always stored in the cache.
   */
  @NotNull
  public Collection<TimedVcsCommit> getTopCommits() {
    return myLogData.getTopCommits();
  }

  /**
   * Returns details of recent commits ordered in the "right way" (as in the log).
   */
  @NotNull
  public Collection<VcsFullCommitDetails> getTopCommitDetails() {
    final Collection<TimedVcsCommit> topCommits = getTopCommits();
    final AtomicBoolean errorDetailsAttached = new AtomicBoolean();
    return ContainerUtil.mapNotNull(topCommits, new Function<TimedVcsCommit, VcsFullCommitDetails>() {
      @Nullable
      @Override
      public VcsFullCommitDetails fun(TimedVcsCommit commit) {
        Hash hash = commit.getHash();
        VcsFullCommitDetails details = myTopCommitsDetailsCache.get(hash);
        if (details != null) {
          return details;
        }

        // shouldn't happen
        String errorMessage = "No details were stored for commit " + hash;
        // log the error only once for the getTopCommitDetails request
        if (!errorDetailsAttached.get()) {
          errorDetailsAttached.set(true);
          // temporary disable the error message until the bug is properly fixed.
          // the bug is: we store 1000 commit details per root, but iterate through the roots_num * 1000 latest commits
          // therefore this error shouldn't happen only in the case when commits of all repositories are evently distributed in time.
//          LOG.error(errorMessage,
//                    new Attachment("details_cache.txt", myTopCommitsDetailsCache.toString()),
//                    new Attachment("top_commits.txt", topCommits.toString()));
        }
        return null;
      }
    });
  }

  /**
   * Simply checks for isDisposed.
   */
  private void invokeAndWait(final Runnable task) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!Disposer.isDisposed(VcsLogDataHolder.this)) {
          task.run();
        }
      }
    });
  }

  /**
   * Contains full logs per repository root & references per root.
   *
   * Until we have loaded the full log of the repository, we store the top part of the log which was loaded.
   * When we load the full structure, it is substituted.
   */
  private static class LogData {
    @NotNull private final Map<VirtualFile, List<TimedVcsCommit>> myLogsByRoot;
    @NotNull private final Map<VirtualFile, Collection<VcsRef>> myRefsByRoot;
    @NotNull private final List<TimedVcsCommit> myCompoundTopCommits;
    @NotNull private final DataPack myDataPack;
    private final boolean myFullLog;

    private LogData(@NotNull Map<VirtualFile, List<TimedVcsCommit>> logsByRoot,
                    @NotNull Map<VirtualFile, Collection<VcsRef>> refsByRoot, @NotNull List<TimedVcsCommit> compoundTopCommits,
                    @NotNull DataPack dataPack, boolean fullLog) {
      myLogsByRoot = logsByRoot;
      myRefsByRoot = refsByRoot;
      myCompoundTopCommits = compoundTopCommits;
      myDataPack = dataPack;
      myFullLog = fullLog;
    }

    @NotNull
    public List<TimedVcsCommit> getLog(@NotNull VirtualFile root) {
      return myLogsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getRefs(@NotNull VirtualFile root) {
      return myRefsByRoot.get(root);
    }

    @NotNull
    public Collection<VcsRef> getAllRefs() {
      Collection<VcsRef> allRefs = new HashSet<VcsRef>();
      for (Collection<VcsRef> refs : myRefsByRoot.values()) {
        allRefs.addAll(refs);
      }
      return allRefs;
    }

    @NotNull
    public DataPack getDataPack() {
      return myDataPack;
    }

    public boolean isFullLogReady() {
      return myFullLog;
    }

    @NotNull
    public Map<VirtualFile,List<TimedVcsCommit>> getLogs() {
      return myLogsByRoot;
    }

    @NotNull
    public Map<VirtualFile, Collection<VcsRef>> getRefs() {
      return myRefsByRoot;
    }

    public int getTopCommitsCount() {
      return myCompoundTopCommits.size();
    }

    @NotNull
    public List<TimedVcsCommit> getTopCommits() {
      return myCompoundTopCommits;
    }
  }

}