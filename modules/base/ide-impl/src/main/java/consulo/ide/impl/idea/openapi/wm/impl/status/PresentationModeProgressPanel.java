/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.wm.impl.status;

import consulo.application.ui.UISettings;
import consulo.colorScheme.EditorColorsManager;
import consulo.application.util.registry.Registry;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.ui.TransparentPanel;
import consulo.util.collection.JBIterable;
import consulo.ui.ex.awt.EmptyIcon;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.color.ColorValue;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PresentationModeProgressPanel {
  private final InlineProgressIndicator myProgress;
  private final JBIterable<ProgressButton> myEastButtons;
  private JLabel myText;
  private JProgressBar myProgressBar;
  private JLabel myText2;
  private JPanel myRootPanel;
  private JPanel myButtonPanel;
  private MergingUpdateQueue myUpdateQueue;
  private Update myUpdate;

  public PresentationModeProgressPanel(InlineProgressIndicator progress) {
    myProgress = progress;
    Font font = JBUI.Fonts.label(11);
    myText.setFont(font);
    myText2.setFont(font);
    myText.setIcon(JBUI.scale(EmptyIcon.create(1, 16)));
    myText2.setIcon(JBUI.scale(EmptyIcon.create(1, 16)));
    myUpdateQueue = new MergingUpdateQueue("Presentation Mode Progress", 100, true, null);
    myUpdate = new Update("Update UI") {
      @Override
      public void run() {
        updateImpl();
      }
    };
    myEastButtons = myProgress.createEastButtons();
    myButtonPanel.add(InlineProgressIndicator.createButtonPanel(myEastButtons.map(b -> b.button)));
  }

  public void update() {
    myUpdateQueue.queue(myUpdate);
  }

  @Nonnull
  private static ColorValue getTextForeground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultForeground();
  }

  private void updateImpl() {
    Color color = TargetAWT.to(getTextForeground());
    myText.setForeground(color);
    myText2.setForeground(color);
    myProgressBar.setForeground(color);

    if (!StringUtil.equals(myText.getText(), myProgress.getText())) {
      myText.setText(myProgress.getText());
    }
    if (!StringUtil.equals(myText2.getText(), myProgress.getText2())) {
      myText2.setText(myProgress.getText2());
    }
    if ((myProgress.isIndeterminate() || myProgress.getFraction() == 0.0) != myProgressBar.isIndeterminate()) {
      myProgressBar.setIndeterminate(myProgress.isIndeterminate() || myProgress.getFraction() == 0.0);
      myProgressBar.revalidate();
    }

    if (!myProgressBar.isIndeterminate()) {
      myProgressBar.setValue((int)(myProgress.getFraction() * 99) + 1);
    }

    myEastButtons.forEach(b -> b.updateAction.run());
  }

  @Nonnull
  public JComponent getProgressPanel() {
    return myRootPanel;
  }

  private void createUIComponents() {
    myRootPanel = new TransparentPanel(0.5f) {
      @Override
      public boolean isVisible() {
        UISettings ui = UISettings.getInstance();
        return ui.getPresentationMode() || !ui.getShowStatusBar() && Registry.is("ide.show.progress.without.status.bar");
      }
    };
  }
}
