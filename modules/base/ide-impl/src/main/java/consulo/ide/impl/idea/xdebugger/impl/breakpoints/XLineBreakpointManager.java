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
package consulo.ide.impl.idea.xdebugger.impl.breakpoints;

import consulo.application.ApplicationManager;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.EditorKind;
import consulo.codeEditor.event.EditorEventMulticaster;
import consulo.codeEditor.event.EditorMouseEvent;
import consulo.codeEditor.event.EditorMouseEventArea;
import consulo.codeEditor.event.EditorMouseListener;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.event.EditorColorsListener;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.SuspendPolicy;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.TextEditor;
import consulo.ide.impl.idea.execution.impl.ConsoleViewUtil;
import consulo.ide.impl.idea.ide.startup.StartupManagerEx;
import consulo.ui.ex.internal.ActionManagerEx;
import consulo.ide.impl.idea.openapi.editor.event.EditorMouseMotionAdapter;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.xdebugger.impl.XSourcePositionImpl;
import consulo.ide.impl.idea.xdebugger.impl.ui.DebuggerUIUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.util.collection.BidirectionalMap;
import consulo.util.collection.SmartList;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import consulo.util.concurrent.AsyncResult;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileUrlChangeAdapter;

import javax.annotation.Nonnull;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class XLineBreakpointManager {
  private final BidirectionalMap<XLineBreakpointImpl, Document> myBreakpoints = new BidirectionalMap<>();
  private final MergingUpdateQueue myBreakpointsUpdateQueue;
  private final Project myProject;
  private final XDependentBreakpointManager myDependentBreakpointManager;
  private final StartupManagerEx myStartupManager;

  public XLineBreakpointManager(@Nonnull Project project, final XDependentBreakpointManager dependentBreakpointManager, final StartupManager startupManager) {
    myProject = project;
    myDependentBreakpointManager = dependentBreakpointManager;
    myStartupManager = (StartupManagerEx)startupManager;

    if (!myProject.isDefault()) {
      EditorEventMulticaster editorEventMulticaster = EditorFactory.getInstance().getEventMulticaster();
      editorEventMulticaster.addDocumentListener(new MyDocumentListener(), project);
      editorEventMulticaster.addEditorMouseListener(new MyEditorMouseListener(), project);
      editorEventMulticaster.addEditorMouseMotionListener(new MyEditorMouseMotionListener(), project);

      final MyDependentBreakpointListener myDependentBreakpointListener = new MyDependentBreakpointListener();
      myDependentBreakpointManager.addListener(myDependentBreakpointListener);
      Disposer.register(project, () -> myDependentBreakpointManager.removeListener(myDependentBreakpointListener));
      VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileUrlChangeAdapter() {
        @Override
        protected void fileUrlChanged(String oldUrl, String newUrl) {
          for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
            final String url = breakpoint.getFileUrl();
            if (FileUtil.startsWith(url, oldUrl)) {
              breakpoint.setFileUrl(newUrl + url.substring(oldUrl.length()));
            }
          }
        }

        @Override
        public void fileDeleted(@Nonnull VirtualFileEvent event) {
          List<XBreakpoint<?>> toRemove = new SmartList<>();
          for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
            if (breakpoint.getFileUrl().equals(event.getFile().getUrl())) {
              toRemove.add(breakpoint);
            }
          }
          removeBreakpoints(toRemove);
        }
      }, project);
    }
    myBreakpointsUpdateQueue = new MergingUpdateQueue("XLine breakpoints", 300, true, null, project);

    // Update breakpoints colors if global color schema was changed
    project.getMessageBus().connect().subscribe(EditorColorsListener.class, new MyEditorColorsListener());
  }

  public void updateBreakpointsUI() {
    if (myProject.isDefault()) return;

    myStartupManager.runAfterOpened((project, uiAccess) -> {
      for (XLineBreakpointImpl<?> breakpoint : myBreakpoints.keySet()) {
        project.getApplication().invokeLater(breakpoint::updateUI, IdeaModalityState.NON_MODAL, project.getDisposed());
      }
    });
  }

  public void registerBreakpoint(XLineBreakpointImpl breakpoint, final boolean initUI) {
    if (initUI) {
      breakpoint.updateUI();
    }
    Document document = breakpoint.getDocument();
    if (document != null) {
      myBreakpoints.put(breakpoint, document);
    }
  }

  public void unregisterBreakpoint(final XLineBreakpointImpl breakpoint) {
    RangeHighlighter highlighter = breakpoint.getHighlighter();
    if (highlighter != null) {
      myBreakpoints.remove(breakpoint);
    }
  }

  @Nonnull
  public Collection<XLineBreakpointImpl> getDocumentBreakpoints(Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) {
      breakpoints = Collections.emptyList();
    }
    return breakpoints;
  }

  private void updateBreakpoints(@Nonnull Document document) {
    Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
    if (breakpoints == null) {
      return;
    }

    IntSet lines = IntSets.newHashSet();
    List<XBreakpoint<?>> toRemove = new SmartList<>();
    for (XLineBreakpointImpl breakpoint : breakpoints) {
      breakpoint.updatePosition();
      if (!breakpoint.isValid() || !lines.add(breakpoint.getLine())) {
        toRemove.add(breakpoint);
      }
    }

    removeBreakpoints(toRemove);
  }

  private void removeBreakpoints(final List<? extends XBreakpoint<?>> toRemove) {
    if (toRemove.isEmpty()) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (XBreakpoint<?> breakpoint : toRemove) {
        XDebuggerManager.getInstance(myProject).getBreakpointManager().removeBreakpoint(breakpoint);
      }
    });
  }

  public void breakpointChanged(final XLineBreakpointImpl breakpoint) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      breakpoint.updateUI();
    }
    else {
      queueBreakpointUpdate(breakpoint);
    }
  }

  public void queueBreakpointUpdate(final XBreakpoint<?> slave) {
    if (slave instanceof XLineBreakpointImpl<?>) {
      queueBreakpointUpdate((XLineBreakpointImpl<?>)slave);
    }
  }

  public void queueBreakpointUpdate(@Nonnull final XLineBreakpointImpl<?> breakpoint) {
    myBreakpointsUpdateQueue.queue(new Update(breakpoint) {
      @Override
      public void run() {
        breakpoint.updateUI();
      }
    });
  }

  public void queueAllBreakpointsUpdate() {
    myBreakpointsUpdateQueue.queue(new Update("all breakpoints") {
      @Override
      public void run() {
        for (XLineBreakpointImpl breakpoint : myBreakpoints.keySet()) {
          breakpoint.updateUI();
        }
      }
    });
  }

  private class MyDocumentListener extends DocumentAdapter {
    @Override
    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      Collection<XLineBreakpointImpl> breakpoints = myBreakpoints.getKeysByValue(document);
      if (breakpoints != null && !breakpoints.isEmpty()) {
        myBreakpointsUpdateQueue.queue(new Update(document) {
          @Override
          public void run() {
            updateBreakpoints(document);
          }
        });
      }
    }
  }

  private boolean myDragDetected = false;

  private class MyEditorMouseMotionListener extends EditorMouseMotionAdapter {
    @Override
    public void mouseDragged(EditorMouseEvent e) {
      myDragDetected = true;
    }
  }

  private class MyEditorMouseListener implements EditorMouseListener {
    @Override
    public void mousePressed(EditorMouseEvent e) {
      myDragDetected = false;
    }

    @Override
    public void mouseClicked(final EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final MouseEvent mouseEvent = e.getMouseEvent();
      if (mouseEvent.isPopupTrigger() ||
          mouseEvent.isMetaDown() ||
          mouseEvent.isControlDown() ||
          mouseEvent.getButton() != MouseEvent.BUTTON1 ||
          editor.getEditorKind() == EditorKind.DIFF ||
          !isInsideGutter(e, editor) ||
          ConsoleViewUtil.isConsoleViewEditor(editor) ||
          !isFromMyProject(editor) ||
          (editor.getSelectionModel().hasSelection() && myDragDetected)) {
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      final int line = EditorUtil.yPositionToLogicalLine(editor, mouseEvent);
      final Document document = editor.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (line >= 0 && line < document.getLineCount() && file != null) {
        ActionManagerEx.getInstanceEx().fireBeforeActionPerformed(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT, e.getMouseEvent());

        final AsyncResult<XLineBreakpoint> lineBreakpoint = XBreakpointUtil.toggleLineBreakpoint(myProject, XSourcePositionImpl.create(file, line), editor, mouseEvent.isAltDown(), false);
        lineBreakpoint.doWhenDone(breakpoint -> {
          if (!mouseEvent.isAltDown() && mouseEvent.isShiftDown() && breakpoint != null) {
            breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
            String selection = editor.getSelectionModel().getSelectedText();
            if (selection != null) {
              breakpoint.setLogExpression(selection);
            }
            else {
              breakpoint.setLogMessage(true);
            }
            // edit breakpoint
            DebuggerUIUtil.showXBreakpointEditorBalloon(myProject, mouseEvent.getPoint(), ((EditorEx)editor).getGutterComponentEx().getComponent(), false, breakpoint);
          }
        });
      }
    }

    private boolean isInsideGutter(EditorMouseEvent e, Editor editor) {
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA && e.getArea() != EditorMouseEventArea.FOLDING_OUTLINE_AREA) {
        return false;
      }
      return e.getMouseEvent().getX() <= ((EditorEx)editor).getGutterComponentEx().getWhitespaceSeparatorOffset();
    }
  }

  private boolean isFromMyProject(@Nonnull Editor editor) {
    if (myProject == editor.getProject()) {
      return true;
    }

    for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      if (fileEditor instanceof TextEditor && ((TextEditor)fileEditor).getEditor().equals(editor)) {
        return true;
      }
    }
    return false;
  }

  private class MyDependentBreakpointListener implements XDependentBreakpointListener {
    @Override
    public void dependencySet(final XBreakpoint<?> slave, final XBreakpoint<?> master) {
      queueBreakpointUpdate(slave);
    }

    @Override
    public void dependencyCleared(final XBreakpoint<?> breakpoint) {
      queueBreakpointUpdate(breakpoint);
    }
  }

  private class MyEditorColorsListener implements EditorColorsListener {
    @Override
    public void globalSchemeChange(EditorColorsScheme scheme) {
      updateBreakpointsUI();
    }
  }
}
