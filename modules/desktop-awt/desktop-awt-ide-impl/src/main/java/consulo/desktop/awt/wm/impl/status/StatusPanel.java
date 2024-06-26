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
package consulo.desktop.awt.wm.impl.status;

import consulo.application.ApplicationManager;
import consulo.application.util.DateFormatUtil;
import consulo.ide.impl.idea.ide.ClipboardSynchronizer;
import consulo.desktop.awt.internal.notification.EventLog;
import consulo.ide.impl.idea.openapi.wm.impl.status.TextPanel;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.ClickListener;
import consulo.ui.ex.awt.JBMenuItem;
import consulo.ui.ex.awt.JBPopupMenu;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.StringUtil;
import consulo.util.lang.Trinity;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * @author peter
 */
class StatusPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(StatusPanel.class);
  private Notification myCurrentNotification;
  private int myTimeStart;
  private boolean myDirty;
  private boolean myAfterClick;
  private Alarm myLogAlarm;
  private final Action myCopyAction;
  private final TextPanel myTextPanel = new TextPanel() {
    @Override
    protected String getTextForPreferredSize() {
      return getText();
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
      super.setBounds(x, y, Math.min(w, StatusPanel.this.getWidth()), h);
    }

    @Override
    protected String truncateText(String text, Rectangle bounds, FontMetrics fm, Rectangle textR, Rectangle iconR, int maxWidth) {
      if (myTimeStart > 0) {
        if (myTimeStart >= text.length()) {
          LOG.error(myTimeStart + " " + text.length());
        }
        final String time = text.substring(myTimeStart);
        final int withoutTime = maxWidth - fm.stringWidth(time);

        int end = Math.min(myTimeStart - 1, 1000);
        while (end > 0) {
          final String truncated = text.substring(0, end) + "... ";
          if (fm.stringWidth(truncated) < withoutTime) {
            text = truncated + time;
            break;
          }
          end--;
        }
      }

      return super.truncateText(text, bounds, fm, textR, iconR, maxWidth);
    }
  };

  private final Supplier<Project> myProjectSupplier;

  StatusPanel(@Nonnull Supplier<Project> getProjectFunc) {
    super(new BorderLayout());

    myProjectSupplier = getProjectFunc;

    setOpaque(false);

    myTextPanel.setBorder(new EmptyBorder(0, 5, 0, 0));
    new ClickListener() {
      @Override
      public boolean onClick(MouseEvent e, int clickCount) {
        if (myCurrentNotification != null || myAfterClick) {
          EventLog.toggleLog(getProject(), myCurrentNotification);
          myAfterClick = true;
          myTextPanel.setExplicitSize(myTextPanel.getSize());
          myTextPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        return true;
      }
    }.installOn(myTextPanel);
    myCopyAction = createCopyAction();

    myTextPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent e) {
        myTextPanel.setExplicitSize(null);
        myTextPanel.revalidate();
        myAfterClick = false;
        if (myCurrentNotification == null) {
          myTextPanel.setCursor(Cursor.getDefaultCursor());
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (myCopyAction != null && e.isPopupTrigger()) {
          JBPopupMenu menu = new JBPopupMenu();
          menu.add(new JBMenuItem(myCopyAction));
          menu.show(myTextPanel, e.getX(), e.getY());
        }
      }
    });

    add(myTextPanel, BorderLayout.WEST);

    JPanel panel = new JPanel();
    panel.setOpaque(isOpaque());
    JLabel label = new JLabel("aaa");
    label.setBackground(JBColor.YELLOW);
    add(panel, BorderLayout.CENTER);

  }

  private Action createCopyAction() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(IdeActions.ACTION_COPY);
    if (action == null) return null;
    return new AbstractAction(action.getTemplatePresentation().getText(), TargetAWT.to(action.getTemplatePresentation().getIcon())) {
      @Override
      public void actionPerformed(ActionEvent e) {
        StringSelection content = new StringSelection(getText());
        ClipboardSynchronizer.getInstance().setContent(content, content);
      }

      @Override
      public boolean isEnabled() {
        return !getText().isEmpty();
      }
    };
  }


  @Nullable
  private Project getProject() {
    return myProjectSupplier.get();
  }

  // Returns the alarm used for displaying status messages in the status bar, or null if the status bar is attached to a floating
  // editor window.
  @Nullable
  private Alarm getAlarm() {
    if (myLogAlarm == null || myLogAlarm.isDisposed()) {
      myLogAlarm = null; //Welcome screen
      Project project = getProject();
      if (project != null) {
        myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
      }
    }
    return myLogAlarm;
  }

  public boolean updateText(@Nullable String nonLogText) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Project project = getProject();
    final Trinity<Notification, String, Long> statusMessage = EventLog.getStatusMessage(project);
    final Alarm alarm = getAlarm();
    myCurrentNotification = StringUtil.isEmpty(nonLogText) && statusMessage != null && alarm != null ? statusMessage.first : null;

    if (alarm != null) {
      alarm.cancelAllRequests();
    }

    if (myCurrentNotification != null) {
      myTextPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      new Runnable() {
        @Override
        public void run() {
          assert statusMessage != null;
          String text = statusMessage.second;
          if (myDirty || System.currentTimeMillis() - statusMessage.third >= DateFormatUtil.MINUTE) {
            myTimeStart = text.length() + 1;
            text += " (" + StringUtil.decapitalize(DateFormatUtil.formatPrettyDateTime(statusMessage.third)) + ")";
          } else {
            myTimeStart = -1;
          }
          setStatusText(text);
          alarm.addRequest(this, 30000);
        }
      }.run();
    }
    else {
      myTimeStart = -1;
      myTextPanel.setCursor(Cursor.getDefaultCursor());
      myDirty = true;
      setStatusText(nonLogText);
    }

    return myCurrentNotification != null;
  }

  private void setStatusText(String text) {
    myTextPanel.setText(text);
    if (!myAfterClick) {
      myTextPanel.revalidate();
    }
  }

  public String getText() {
    return myTextPanel.getText();
  }

}
