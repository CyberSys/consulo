/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.impl;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.diff.*;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.actions.impl.*;
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.ErrorDiffTool;
import com.intellij.diff.tools.external.ExternalDiffTool;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineRange;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.ContainerUtil;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.base.BaseDataManager;
import consulo.ide.ui.popup.JBPopupFactory;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.internal.ProjectIdeFocusManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.Wrapper;
import consulo.ui.ex.popup.Balloon;
import consulo.ui.ex.popup.ListPopup;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.function.Condition;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("InnerClassMayBeStatic")
public abstract class DiffRequestProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(DiffRequestProcessor.class);

  private boolean myDisposed;

  @javax.annotation.Nullable
  private final Project myProject;
  @Nonnull
  private final DiffContext myContext;

  @Nonnull
  private final DiffSettings mySettings;
  @Nonnull
  private final List<DiffTool> myAvailableTools;
  @Nonnull
  private final LinkedList<DiffTool> myToolOrder;

  @Nonnull
  private final OpenInEditorAction myOpenInEditorAction;
  @Nullable private DefaultActionGroup myPopupActionGroup;

  @Nonnull
  private final JPanel myPanel;
  @Nonnull
  private final MyPanel myMainPanel;
  @Nonnull
  private final Wrapper myContentPanel;
  @Nonnull
  private final Wrapper myToolbarPanel; // TODO: allow to call 'updateToolbar' from Viewer ?
  @Nonnull
  private final Wrapper myToolbarStatusPanel;
  @Nonnull
  private final MyProgressBar myProgressBar;

  @Nonnull
  private DiffRequest myActiveRequest;

  @Nonnull
  private ViewerState myState;

  public DiffRequestProcessor(@javax.annotation.Nullable Project project) {
    this(project, new UserDataHolderBase());
  }

  public DiffRequestProcessor(@Nullable Project project, @Nonnull String place) {
    this(project, DiffUtil.createUserDataHolder(DiffUserDataKeys.PLACE, place));
  }

  public DiffRequestProcessor(@javax.annotation.Nullable Project project, @Nonnull UserDataHolder context) {
    myProject = project;

    myContext = new MyDiffContext(context);
    myActiveRequest = new LoadingDiffRequest();

    mySettings = DiffSettingsHolder.getInstance().getSettings(myContext.getUserData(DiffUserDataKeys.PLACE));

    myAvailableTools = DiffManagerEx.getInstance().getDiffTools();
    myToolOrder = new LinkedList<DiffTool>(getToolOrderFromSettings(myAvailableTools));

    // UI

    myMainPanel = new MyPanel();
    myContentPanel = new Wrapper();
    myToolbarPanel = new Wrapper();
    myToolbarPanel.setFocusable(true);
    myToolbarStatusPanel = new Wrapper();
    myProgressBar = new MyProgressBar();

    myPanel = JBUI.Panels.simplePanel(myMainPanel);

    JPanel statusPanel = JBUI.Panels.simplePanel(myToolbarStatusPanel).addToLeft(myProgressBar);
    JPanel topPanel = JBUI.Panels.simplePanel(myToolbarPanel).addToRight(statusPanel);

    myMainPanel.add(topPanel, BorderLayout.NORTH);
    myMainPanel.add(myContentPanel, BorderLayout.CENTER);

    myMainPanel.setFocusTraversalPolicyProvider(true);
    myMainPanel.setFocusTraversalPolicy(new MyFocusTraversalPolicy());

    JComponent bottomPanel = myContext.getUserData(DiffUserDataKeysEx.BOTTOM_PANEL);
    if (bottomPanel != null) myMainPanel.add(bottomPanel, BorderLayout.SOUTH);
    if (bottomPanel instanceof Disposable) Disposer.register(this, (Disposable)bottomPanel);

    myState = EmptyState.INSTANCE;
    myContentPanel.setContent(DiffUtil.createMessagePanel(((LoadingDiffRequest)myActiveRequest).getMessage()));

    myOpenInEditorAction = new OpenInEditorAction(new Runnable() {
      @Override
      public void run() {
        onAfterNavigate();
      }
    });
  }

  //
  // Update
  //

  @RequiredUIAccess
  protected void reloadRequest() {
    updateRequest(true);
  }

  @RequiredUIAccess
  public void updateRequest() {
    updateRequest(false);
  }

  @RequiredUIAccess
  public void updateRequest(boolean force) {
    updateRequest(force, null);
  }

  @RequiredUIAccess
  public abstract void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy);

  @Nonnull
  private FrameDiffTool getFittedTool() {
    List<FrameDiffTool> tools = new ArrayList<FrameDiffTool>();
    for (DiffTool tool : myToolOrder) {
      try {
        if (tool instanceof FrameDiffTool && tool.canShow(myContext, myActiveRequest)) {
          tools.add((FrameDiffTool)tool);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    tools = DiffUtil.filterSuppressedTools(tools);

    return tools.isEmpty() ? ErrorDiffTool.INSTANCE : tools.get(0);
  }

  @Nonnull
  private List<FrameDiffTool> getAvailableFittedTools() {
    List<FrameDiffTool> tools = new ArrayList<FrameDiffTool>();
    for (DiffTool tool : myAvailableTools) {
      try {
        if (tool instanceof FrameDiffTool && tool.canShow(myContext, myActiveRequest)) {
          tools.add((FrameDiffTool)tool);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    return DiffUtil.filterSuppressedTools(tools);
  }

  private void moveToolOnTop(@Nonnull DiffTool tool) {
    myToolOrder.remove(tool);

    FrameDiffTool toolToReplace = getFittedTool();

    int index;
    for (index = 0; index < myToolOrder.size(); index++) {
      if (myToolOrder.get(index) == toolToReplace) break;
    }
    myToolOrder.add(index, tool);

    updateToolOrderSettings(myToolOrder);
  }

  @Nonnull
  private ViewerState createState() {
    FrameDiffTool frameTool = getFittedTool();

    DiffViewer viewer = frameTool.createComponent(myContext, myActiveRequest);

    for (DiffExtension extension : DiffExtension.EP_NAME.getExtensionList()) {
      extension.onViewerCreated(viewer, myContext, myActiveRequest);
    }

    DiffViewerWrapper wrapper = myActiveRequest.getUserData(DiffViewerWrapper.KEY);
    if (wrapper == null) {
      return new DefaultState(viewer, frameTool);
    }
    else {
      return new WrapperState(viewer, frameTool, wrapper);
    }
  }

  //
  // Abstract
  //

  @javax.annotation.Nullable
  private ApplyData myQueuedApplyRequest;

  @RequiredUIAccess
  protected void applyRequest(@Nonnull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    myIterationState = IterationState.NONE;

    force = force || (myQueuedApplyRequest != null && myQueuedApplyRequest.force);
    myQueuedApplyRequest = new ApplyData(request, force, scrollToChangePolicy);
    Runnable task = new Runnable() {
      @Override
      public void run() {
        if (myQueuedApplyRequest == null || myDisposed) return;
        doApplyRequest(myQueuedApplyRequest.request, myQueuedApplyRequest.force, myQueuedApplyRequest.scrollToChangePolicy);
        myQueuedApplyRequest = null;
      }
    };

    ProjectIdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(task);
  }

  @RequiredUIAccess
  private void doApplyRequest(@Nonnull DiffRequest request, boolean force, @javax.annotation.Nullable ScrollToPolicy scrollToChangePolicy) {
    if (!force && request == myActiveRequest) return;

    request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, scrollToChangePolicy);

    boolean hadFocus = isFocused();

    myState.destroy();
    myToolbarStatusPanel.setContent(null);
    myToolbarPanel.setContent(null);
    myContentPanel.setContent(null);
    ActionUtil.clearActions(myMainPanel);

    myActiveRequest.onAssigned(false);
    myActiveRequest = request;
    myActiveRequest.onAssigned(true);

    try {
      myState = createState();
      myState.init();
    }
    catch (Throwable e) {
      LOG.error(e);
      myState = new ErrorState(new ErrorDiffRequest("Error: can't show diff"), getFittedTool());
      myState.init();
    }

    if (hadFocus) requestFocusInternal();
  }

  protected void setWindowTitle(@Nonnull String title) {
  }

  protected void onAfterNavigate() {
  }

  @RequiredUIAccess
  protected void onDispose() {
  }

  @javax.annotation.Nullable
  public <T> T getContextUserData(@Nonnull Key<T> key) {
    return myContext.getUserData(key);
  }

  public <T> void putContextUserData(@Nonnull Key<T> key, @javax.annotation.Nullable T value) {
    myContext.putUserData(key, value);
  }

  @Nonnull
  protected List<AnAction> getNavigationActions() {
    return ContainerUtil.<AnAction>list(
            new MyPrevDifferenceAction(),
            new MyNextDifferenceAction(),
            new MyPrevChangeAction(),
            new MyNextChangeAction()
    );
  }

  //
  // Misc
  //

  public boolean isWindowFocused() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    return window != null && window.isFocused();
  }

  public boolean isFocused() {
    return DiffUtil.isFocusedComponent(myProject, myPanel);
  }

  public void requestFocus() {
    DiffUtil.requestFocus(myProject, getPreferredFocusedComponent());
  }

  protected void requestFocusInternal() {
    JComponent component = getPreferredFocusedComponent();
    if (component != null) component.requestFocusInWindow();
  }

  @Nonnull
  protected List<DiffTool> getToolOrderFromSettings(@Nonnull List<DiffTool> availableTools) {
    List<DiffTool> result = new ArrayList<DiffTool>();
    List<String> savedOrder = getSettings().getDiffToolsOrder();

    for (final String clazz : savedOrder) {
      DiffTool tool = ContainerUtil.find(availableTools, new Condition<DiffTool>() {
        @Override
        public boolean value(DiffTool tool) {
          return tool.getClass().getCanonicalName().equals(clazz);
        }
      });
      if (tool != null) result.add(tool);
    }

    for (DiffTool tool : availableTools) {
      if (!result.contains(tool)) result.add(tool);
    }

    return result;
  }

  protected void updateToolOrderSettings(@Nonnull List<DiffTool> toolOrder) {
    List<String> savedOrder = new ArrayList<String>();
    for (DiffTool tool : toolOrder) {
      savedOrder.add(tool.getClass().getCanonicalName());
    }
    getSettings().setDiffToolsOrder(savedOrder);
  }

  @Override
  public void dispose() {
    if (myDisposed) return;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myDisposed) return;
        myDisposed = true;

        onDispose();

        myState.destroy();
        myToolbarStatusPanel.setContent(null);
        myToolbarPanel.setContent(null);
        myContentPanel.setContent(null);

        myActiveRequest.onAssigned(false);

        myState = EmptyState.INSTANCE;
        myActiveRequest = NoDiffRequest.INSTANCE;
      }
    });
  }

  @Nonnull
  protected DefaultActionGroup collectToolbarActions(@javax.annotation.Nullable List<AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    List<AnAction> navigationActions = new ArrayList<AnAction>();
    navigationActions.addAll(getNavigationActions());
    navigationActions.add(myOpenInEditorAction);
    navigationActions.add(new MyChangeDiffToolAction());
    DiffUtil.addActionBlock(group,
                            navigationActions);

    DiffUtil.addActionBlock(group, viewerActions);

    List<AnAction> requestContextActions = myActiveRequest.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, requestContextActions);

    List<AnAction> contextActions = myContext.getUserData(DiffUserDataKeys.CONTEXT_ACTIONS);
    DiffUtil.addActionBlock(group, contextActions);

    DiffUtil.addActionBlock(group,
                            new ShowInExternalToolAction(),
                            ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP));

    return group;
  }

  @Nonnull
  protected DefaultActionGroup collectPopupActions(@javax.annotation.Nullable List<AnAction> viewerActions) {
    DefaultActionGroup group = new DefaultActionGroup();

    List<AnAction> selectToolActions = new ArrayList<AnAction>();
    for (DiffTool tool : getAvailableFittedTools()) {
      if (tool == myState.getActiveTool()) continue;
      selectToolActions.add(new DiffToolToggleAction(tool));
    }
    DiffUtil.addActionBlock(group, selectToolActions);

    DiffUtil.addActionBlock(group, viewerActions);

    return group;
  }

  protected void buildToolbar(@Nullable List<AnAction> viewerActions) {
    ActionGroup group = collectToolbarActions(viewerActions);
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.DIFF_TOOLBAR, group, true);

    DataManager.registerDataProvider(toolbar.getComponent(), myMainPanel);
    toolbar.setTargetComponent(toolbar.getComponent());

    myToolbarPanel.setContent(toolbar.getComponent());
    for (AnAction action : group.getChildren(null)) {
      DiffUtil.registerAction(action, myMainPanel);
    }
  }

  protected void buildActionPopup(@Nullable List<AnAction> viewerActions) {
    ShowActionGroupPopupAction action = new ShowActionGroupPopupAction();
    DiffUtil.registerAction(action, myMainPanel);

    myPopupActionGroup = collectPopupActions(viewerActions);
  }

  private void setTitle(@javax.annotation.Nullable String title) {
    if (getContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE) == Boolean.TRUE) return;
    if (title == null) title = "Diff";
    setWindowTitle(title);
  }

  //
  // Getters
  //

  @Nonnull
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    JComponent component = myState.getPreferredFocusedComponent();
    return component != null ? component : myToolbarPanel.getTargetComponent();
  }

  @Nullable
  public DiffRequest getActiveRequest() {
    return myActiveRequest;
  }

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @Nonnull
  public DiffContext getContext() {
    return myContext;
  }

  @Nonnull
  protected DiffSettings getSettings() {
    return mySettings;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  //
  // Actions
  //

  private class ShowInExternalToolAction extends DumbAwareAction {
    public ShowInExternalToolAction() {
      EmptyAction.setupAction(this, "Diff.ShowInExternalTool", null);
    }

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
      if (!ExternalDiffTool.isEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      e.getPresentation().setEnabled(ExternalDiffTool.canShow(myActiveRequest));
      e.getPresentation().setVisible(true);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
      try {
        ExternalDiffTool.showRequest(e.getData(CommonDataKeys.PROJECT), myActiveRequest);
      }
      catch (Throwable ex) {
        Messages.showErrorDialog(e.getData(CommonDataKeys.PROJECT), ex.getMessage(), "Can't Show Diff In External Tool");
      }
    }
  }

  private class MyChangeDiffToolAction extends ComboBoxAction implements DumbAware {
    public MyChangeDiffToolAction() {
      // TODO: add icons for diff tools, show only icon in toolbar - to reduce jumping on change ?
      setEnabledInModalContext(true);
    }

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      DiffTool activeTool = myState.getActiveTool();
      presentation.setText(activeTool.getName());

      if (activeTool == ErrorDiffTool.INSTANCE) {
        presentation.setEnabledAndVisible(false);
      }

      for (DiffTool tool : getAvailableFittedTools()) {
        if (tool != activeTool) {
          presentation.setEnabledAndVisible(true);
          return;
        }
      }

      presentation.setEnabledAndVisible(false);
    }

    @Nonnull
    @Override
    public DefaultActionGroup createPopupActionGroup(JComponent button) {
      DefaultActionGroup group = new DefaultActionGroup();
      for (DiffTool tool : getAvailableFittedTools()) {
        group.add(new DiffToolToggleAction(tool));
      }

      return group;
    }
  }

  private class DiffToolToggleAction extends AnAction implements DumbAware {
    @Nonnull
    private final DiffTool myDiffTool;

    private DiffToolToggleAction(@Nonnull DiffTool tool) {
      super(tool.getName());
      setEnabledInModalContext(true);
      myDiffTool = tool;
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      if (myState.getActiveTool() == myDiffTool) return;

      moveToolOnTop(myDiffTool);

      updateRequest(true);
    }
  }

  private class ShowActionGroupPopupAction extends DumbAwareAction {
    public ShowActionGroupPopupAction() {
      EmptyAction.setupAction(this, "Diff.ShowSettingsPopup", null);
    }

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myPopupActionGroup != null && myPopupActionGroup.getChildrenCount() > 0);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
      assert myPopupActionGroup != null;
      ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup("Diff Actions", myPopupActionGroup, e.getDataContext(),
                                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
      popup.showInCenterOf(myPanel);
    }
  }

  //
  // Navigation
  //

  private enum IterationState {NEXT, PREV, NONE}

  @Nonnull
  private IterationState myIterationState = IterationState.NONE;

  @RequiredUIAccess
  protected boolean hasNextChange() {
    return false;
  }

  @RequiredUIAccess
  protected boolean hasPrevChange() {
    return false;
  }

  @RequiredUIAccess
  protected void goToNextChange(boolean fromDifferences) {
  }

  @RequiredUIAccess
  protected void goToPrevChange(boolean fromDifferences) {
  }

  @RequiredUIAccess
  protected boolean isNavigationEnabled() {
    return false;
  }

  protected class MyNextDifferenceAction extends NextDifferenceAction {
    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoNext()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasNextChange()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoNext()) {
        iterable.goNext();
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasNextChange() || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != IterationState.NEXT) {
        notifyMessage(e, true);
        myIterationState = IterationState.NEXT;
        return;
      }

      goToNextChange(true);
    }
  }

  protected class MyPrevDifferenceAction extends PrevDifferenceAction {
    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      PrevNextDifferenceIterable iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoPrev()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      if (getSettings().isGoToNextFileOnNextDifference() && isNavigationEnabled() && hasPrevChange()) {
        e.getPresentation().setEnabled(true);
        return;
      }

      e.getPresentation().setEnabled(false);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      PrevNextDifferenceIterable iterable = e.getDataContext().getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE);
      if (iterable != null && iterable.canGoPrev()) {
        iterable.goPrev();
        myIterationState = IterationState.NONE;
        return;
      }

      if (!isNavigationEnabled() || !hasPrevChange() || !getSettings().isGoToNextFileOnNextDifference()) return;

      if (myIterationState != IterationState.PREV) {
        notifyMessage(e, false);
        myIterationState = IterationState.PREV;
        return;
      }

      goToPrevChange(true);
    }
  }

  private void notifyMessage(@Nonnull AnActionEvent e, boolean next) {
    Editor editor = e.getData(DiffDataKeys.CURRENT_EDITOR);

    // TODO: provide "change" word in chain UserData - for tests/etc
    String message = DiffUtil.createNotificationText(next ? "Press again to go to the next file" : "Press again to go to the previous file",
                                                     "You can disable this feature in " + DiffUtil.getSettingsConfigurablePath());

    final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(message));
    Point point = new Point(myContentPanel.getWidth() / 2, next ? myContentPanel.getHeight() - JBUI.scale(40) : JBUI.scale(40));

    if (editor == null) {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      final HintHint hintHint = createNotifyHint(myContentPanel, point, next);
      hint.show(myContentPanel, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hintHint);
    }
    else {
      int x = SwingUtilities.convertPoint(myContentPanel, point, editor.getComponent()).x;

      JComponent header = editor.getHeaderComponent();
      int shift = editor.getScrollingModel().getVerticalScrollOffset() - (header != null ? header.getHeight() : 0);

      LogicalPosition position;
      LineRange changeRange = e.getData(DiffDataKeys.CURRENT_CHANGE_RANGE);
      if (changeRange == null) {
        position = new LogicalPosition(editor.getCaretModel().getLogicalPosition().line + (next ? 1 : 0), 0);
      }
      else {
        position = new LogicalPosition(next ? changeRange.end : changeRange.start, 0);
      }
      int y = editor.logicalPositionToXY(position).y - shift;

      Point editorPoint = new Point(x, y);
      final HintHint hintHint = createNotifyHint(editor.getComponent(), editorPoint, !next);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, editorPoint, HintManager.HIDE_BY_ANY_KEY |
                                                                                  HintManager.HIDE_BY_TEXT_CHANGE |
                                                                                  HintManager.HIDE_BY_SCROLLING, 0, false, hintHint);
    }
  }

  @Nonnull
  private static HintHint createNotifyHint(@Nonnull JComponent component, @Nonnull Point point, boolean above) {
    return new HintHint(component, point)
            .setPreferredPosition(above ? Balloon.Position.above : Balloon.Position.below)
            .setAwtTooltip(true)
            .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
            .setTextBg(HintUtil.INFORMATION_COLOR)
            .setShowImmediately(true);
  }

  // Iterate requests

  protected class MyNextChangeAction extends NextChangeAction {
    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasNextChange());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasNextChange()) return;

      goToNextChange(false);
    }
  }

  protected class MyPrevChangeAction extends PrevChangeAction {
    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
      if (!ActionPlaces.DIFF_TOOLBAR.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(true);
        return;
      }

      if (!isNavigationEnabled()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(hasPrevChange());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      if (!isNavigationEnabled() || !hasPrevChange()) return;

      goToPrevChange(false);
    }
  }

  //
  // Helpers
  //

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension windowSize = DiffUtil.getDefaultDiffPanelSize();
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(windowSize.width, size.width), Math.max(windowSize.height, size.height));
    }

    @javax.annotation.Nullable
    @Override
    public Object getData(@Nonnull @NonNls Key<?> dataId) {
      Object data;

      DataProvider contentProvider = ((BaseDataManager)DataManager.getInstance()).getDataProviderEx(myContentPanel.getTargetComponent());
      if (contentProvider != null) {
        data = contentProvider.getData(dataId);
        if (data != null) return data;
      }

      if (OpenInEditorAction.KEY == dataId) {
        return myOpenInEditorAction;
      }
      else if (DiffDataKeys.DIFF_REQUEST == dataId) {
        return myActiveRequest;
      }
      else if (CommonDataKeys.PROJECT == dataId) {
        return myProject;
      }
      else if (PlatformDataKeys.HELP_ID == dataId) {
        if (myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID) != null) {
          return myActiveRequest.getUserData(DiffUserDataKeys.HELP_ID);
        }
        else {
          return "reference.dialogs.diff.file";
        }
      }
      else if (DiffDataKeys.DIFF_CONTEXT == dataId) {
        return myContext;
      }

      data = myState.getData(dataId);
      if (data != null) return data;

      DataProvider requestProvider = myActiveRequest.getUserData(DiffUserDataKeys.DATA_PROVIDER);
      if (requestProvider != null) {
        data = requestProvider.getData(dataId);
        if (data != null) return data;
      }

      DataProvider contextProvider = myContext.getUserData(DiffUserDataKeys.DATA_PROVIDER);
      if (contextProvider != null) {
        data = contextProvider.getData(dataId);
        if (data != null) return data;
      }
      return null;
    }
  }

  private static class MyProgressBar extends JProgressBar {
    private int myProgressCount = 0;

    public MyProgressBar() {
      setIndeterminate(true);
      setVisible(false);
    }

    public void startProgress() {
      myProgressCount++;
      setVisible(true);
    }

    public void stopProgress() {
      myProgressCount--;
      LOG.assertTrue(myProgressCount >= 0);
      if (myProgressCount == 0) setVisible(false);
    }
  }

  private class MyFocusTraversalPolicy extends IdeFocusTraversalPolicy {
    @Override
    public final Component getDefaultComponentImpl(final Container focusCycleRoot) {
      JComponent component = DiffRequestProcessor.this.getPreferredFocusedComponent();
      if (component == null) return null;
      return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component, this);
    }
  }

  private class MyDiffContext extends DiffContextEx {
    @Nonnull
    private final UserDataHolder myContext;

    public MyDiffContext(@Nonnull UserDataHolder context) {
      myContext = context;
    }

    @RequiredUIAccess
    @Override
    public void reopenDiffRequest() {
      updateRequest(true);
    }

    @RequiredUIAccess
    @Override
    public void reloadDiffRequest() {
      reloadRequest();
    }

    @RequiredUIAccess
    @Override
    public void showProgressBar(boolean enabled) {
      if (enabled) {
        myProgressBar.startProgress();
      }
      else {
        myProgressBar.stopProgress();
      }
    }

    @javax.annotation.Nullable
    @Override
    public Project getProject() {
      return DiffRequestProcessor.this.getProject();
    }

    @Override
    public boolean isFocused() {
      return DiffRequestProcessor.this.isFocused();
    }

    @Override
    public boolean isWindowFocused() {
      return DiffRequestProcessor.this.isWindowFocused();
    }

    @Override
    public void requestFocus() {
      DiffRequestProcessor.this.requestFocusInternal();
    }

    @javax.annotation.Nullable
    @Override
    public <T> T getUserData(@Nonnull Key<T> key) {
      return myContext.getUserData(key);
    }

    @Override
    public <T> void putUserData(@Nonnull Key<T> key, @Nullable T value) {
      myContext.putUserData(key, value);
    }
  }

  private static class ApplyData {
    @Nonnull
    private final DiffRequest request;
    private final boolean force;
    @javax.annotation.Nullable
    private final ScrollToPolicy scrollToChangePolicy;

    public ApplyData(@Nonnull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
      this.request = request;
      this.force = force;
      this.scrollToChangePolicy = scrollToChangePolicy;
    }
  }

  //
  // States
  //

  private interface ViewerState {
    @RequiredUIAccess
    void init();

    @RequiredUIAccess
    void destroy();

    @javax.annotation.Nullable
    JComponent getPreferredFocusedComponent();

    @javax.annotation.Nullable
    Object getData(@NonNls Key<?> dataId);

    @Nonnull
    DiffTool getActiveTool();
  }

  private static class EmptyState implements ViewerState {
    private static final EmptyState INSTANCE = new EmptyState();

    @RequiredUIAccess
    @Override
    public void init() {
    }

    @RequiredUIAccess
    @Override
    public void destroy() {
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @javax.annotation.Nullable
    @Override
    public Object getData(@NonNls Key<?> dataId) {
      return null;
    }

    @Nonnull
    @Override
    public DiffTool getActiveTool() {
      return ErrorDiffTool.INSTANCE;
    }
  }

  private class ErrorState implements ViewerState {
    @javax.annotation.Nullable
    private final DiffTool myDiffTool;
    @Nonnull
    private final MessageDiffRequest myRequest;

    @Nonnull
    private final DiffViewer myViewer;

    public ErrorState(@Nonnull MessageDiffRequest request) {
      this(request, null);
    }

    public ErrorState(@Nonnull MessageDiffRequest request, @javax.annotation.Nullable DiffTool diffTool) {
      myDiffTool = diffTool;
      myRequest = request;

      myViewer = ErrorDiffTool.INSTANCE.createComponent(myContext, myRequest);
    }

    @Override
    @RequiredUIAccess
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());

      FrameDiffTool.ToolbarComponents init = myViewer.init();
      buildToolbar(init.toolbarActions);
    }

    @Override
    @RequiredUIAccess
    public void destroy() {
      Disposer.dispose(myViewer);
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return null;
    }

    @javax.annotation.Nullable
    @Override
    public Object getData(@NonNls Key<?> dataId) {
      return null;
    }

    @Nonnull
    @Override
    public DiffTool getActiveTool() {
      return myDiffTool != null ? myDiffTool : ErrorDiffTool.INSTANCE;
    }
  }

  private class DefaultState implements ViewerState {
    @Nonnull
    private final DiffViewer myViewer;
    @Nonnull
    private final FrameDiffTool myTool;

    public DefaultState(@Nonnull DiffViewer viewer, @Nonnull FrameDiffTool tool) {
      myViewer = viewer;
      myTool = tool;
    }

    @Override
    @RequiredUIAccess
    public void init() {
      myContentPanel.setContent(myViewer.getComponent());
      setTitle(myActiveRequest.getTitle());

      FrameDiffTool.ToolbarComponents toolbarComponents = myViewer.init();

      buildToolbar(toolbarComponents.toolbarActions);
      buildActionPopup(toolbarComponents.popupActions);

      myToolbarStatusPanel.setContent(toolbarComponents.statusPanel);
    }

    @Override
    @RequiredUIAccess
    public void destroy() {
      Disposer.dispose(myViewer);
    }

    @javax.annotation.Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myViewer.getPreferredFocusedComponent();
    }

    @Nonnull
    @Override
    public DiffTool getActiveTool() {
      return myTool;
    }

    @javax.annotation.Nullable
    @Override
    public Object getData(@NonNls Key<?> dataId) {
      if (DiffDataKeys.DIFF_VIEWER == dataId) {
        return myViewer;
      }
      return null;
    }
  }

  private class WrapperState implements ViewerState {
    @Nonnull
    private final DiffViewer myViewer;
    @Nonnull
    private final FrameDiffTool myTool;

    @Nonnull
    private DiffViewer myWrapperViewer;

    public WrapperState(@Nonnull DiffViewer viewer, @Nonnull FrameDiffTool tool, @Nonnull DiffViewerWrapper wrapper) {
      myViewer = viewer;
      myTool = tool;
      myWrapperViewer = wrapper.createComponent(myContext, myActiveRequest, myViewer);
    }

    @Override
    @RequiredUIAccess
    public void init() {
      myContentPanel.setContent(myWrapperViewer.getComponent());
      setTitle(myActiveRequest.getTitle());


      FrameDiffTool.ToolbarComponents toolbarComponents1 = myViewer.init();
      FrameDiffTool.ToolbarComponents toolbarComponents2 = myWrapperViewer.init();

      List<AnAction> toolbarActions = new ArrayList<AnAction>();
      if (toolbarComponents1.toolbarActions != null) toolbarActions.addAll(toolbarComponents1.toolbarActions);
      if (toolbarComponents2.toolbarActions != null) {
        if (!toolbarActions.isEmpty() && !toolbarComponents2.toolbarActions.isEmpty()) toolbarActions.add(AnSeparator.getInstance());
        toolbarActions.addAll(toolbarComponents2.toolbarActions);
      }
      buildToolbar(toolbarActions);

      List<AnAction> popupActions = new ArrayList<AnAction>();
      if (toolbarComponents1.popupActions != null) popupActions.addAll(toolbarComponents1.popupActions);
      if (toolbarComponents2.popupActions != null) {
        if (!popupActions.isEmpty() && !toolbarComponents2.popupActions.isEmpty()) popupActions.add(AnSeparator.getInstance());
        popupActions.addAll(toolbarComponents2.popupActions);
      }
      buildActionPopup(popupActions);


      myToolbarStatusPanel.setContent(toolbarComponents1.statusPanel); // TODO: combine both panels ?
    }

    @Override
    @RequiredUIAccess
    public void destroy() {
      Disposer.dispose(myViewer);
      Disposer.dispose(myWrapperViewer);
    }

    @javax.annotation.Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myWrapperViewer.getPreferredFocusedComponent();
    }

    @Nonnull
    @Override
    public DiffTool getActiveTool() {
      return myTool;
    }

    @javax.annotation.Nullable
    @Override
    public Object getData(@NonNls Key<?> dataId) {
      if (DiffDataKeys.WRAPPING_DIFF_VIEWER == dataId) {
        return myWrapperViewer;
      }
      if (DiffDataKeys.DIFF_VIEWER == dataId) {
        return myViewer;
      }
      return null;
    }
  }
}
