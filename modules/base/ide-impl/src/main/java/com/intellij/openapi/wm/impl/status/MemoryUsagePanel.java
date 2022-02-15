// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import consulo.application.ui.UISettings;
import consulo.application.ui.awt.*;
import consulo.application.ui.event.UISettingsListener;
import consulo.project.ui.wm.CustomStatusBarWidget;
import consulo.project.ui.wm.StatusBar;
import com.intellij.ui.UIBundle;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.concurrent.EdtExecutorService;
import com.intellij.util.ui.*;
import consulo.ui.ex.update.Activatable;
import consulo.ui.ex.update.UiNotifyConnector;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.openapi.util.io.FileUtil.MEGABYTE;

public final class MemoryUsagePanel extends JButton implements CustomStatusBarWidget, UISettingsListener, Activatable {
  public static final String WIDGET_ID = "Memory";

  private static final int INDENT = 6;
  private static final Color USED_COLOR = JBColor.namedColor("MemoryIndicator.usedBackground", new JBColor(Gray._185, Gray._110));
  private static final Color UNUSED_COLOR = JBColor.namedColor("MemoryIndicator.allocatedBackground", new JBColor(Gray._215, Gray._90));

  private final String mySample;
  private long myLastAllocated = -1;
  private long myLastUsed = -1;
  private BufferedImage myBufferedImage;
  private boolean myWasPressed;
  private ScheduledFuture<?> myFuture;

  public MemoryUsagePanel() {
    long max = Math.min(Runtime.getRuntime().maxMemory() / MEGABYTE, 9999);
    mySample = UIBundle.message("memory.usage.panel.message.text", max, max);

    setOpaque(false);
    setFocusable(false);

    addActionListener(e -> {
      //noinspection CallToSystemGC
      System.gc();
      updateState();
    });

    setBorder(JBUI.Borders.empty(0, 2));
    updateUI();

    new UiNotifyConnector(this, this);
  }

  @Override
  public void showNotify() {
    myFuture = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(this::updateState, 1, 5, TimeUnit.SECONDS);
  }

  @Override
  public void hideNotify() {
    if (myFuture != null) {
      myFuture.cancel(true);
      myFuture = null;
    }
  }

  @Override
  public void dispose() {
  }

  @Override
  public void install(@Nonnull StatusBar statusBar) {
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Nonnull
  @Override
  public String ID() {
    return WIDGET_ID;
  }

  public void setShowing(boolean showing) {
    if (showing != isVisible()) {
      setVisible(showing);
      revalidate();
    }
  }

  @Override
  public void updateUI() {
    myBufferedImage = null;
    super.updateUI();
    setFont(getWidgetFont());
  }

  @Override
  public void uiSettingsChanged(UISettings uiSettings) {
    myBufferedImage = null;
  }

  private static Font getWidgetFont() {
    return JBUI.Fonts.label(11);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void paintComponent(Graphics g) {
    boolean pressed = getModel().isPressed();
    boolean stateChanged = myWasPressed != pressed;
    myWasPressed = pressed;

    if (myBufferedImage == null || stateChanged) {
      Dimension size = getSize();
      Insets insets = getInsets();

      int barWidth = size.width - INDENT;
      myBufferedImage = ImageUtil.createImage(g, barWidth, size.height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2 = JBSwingUtilities.runGlobalCGTransform(this, myBufferedImage.createGraphics());
      UISettings.setupAntialiasing(g2);

      g2.setFont(getFont());
      int textHeight = g2.getFontMetrics().getAscent();

      Runtime rt = Runtime.getRuntime();
      long maxMem = rt.maxMemory();
      long allocatedMem = rt.totalMemory();
      long unusedMem = rt.freeMemory();
      long usedMem = allocatedMem - unusedMem;

      int usedBarLength = (int)(barWidth * usedMem / maxMem);
      int unusedBarLength = (int)(size.height * unusedMem / maxMem);

      // background
      g2.setColor(UIUtil.getPanelBackground());
      g2.fillRect(0, 0, barWidth, size.height);

      // gauge (used)
      g2.setColor(USED_COLOR);
      g2.fillRect(0, 0, usedBarLength, size.height);

      // gauge (unused)
      g2.setColor(UNUSED_COLOR);
      g2.fillRect(usedBarLength, 0, unusedBarLength, size.height);

      // label
      g2.setColor(pressed ? UIUtil.getLabelDisabledForeground() : JBColor.foreground());
      String text = UIBundle.message("memory.usage.panel.message.text", usedMem / MEGABYTE, maxMem / MEGABYTE);
      int textX = insets.left;
      int textY = insets.top + (size.height - insets.top - insets.bottom - textHeight) / 2 + textHeight - JBUIScale.scale(1);
      g2.drawString(text, textX, textY);

      g2.dispose();
    }

    UIUtil.drawImage(g, myBufferedImage, INDENT, 0, null);
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics metrics = getFontMetrics(getWidgetFont());
    Insets insets = getInsets();
    int width = metrics.stringWidth(mySample) + insets.left + insets.right + JBUIScale.scale(2) + INDENT;
    int height = metrics.getHeight() + insets.top + insets.bottom + JBUIScale.scale(2);
    return new Dimension(width, height);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  private void updateState() {
    if (!isShowing()) {
      return;
    }

    Runtime rt = Runtime.getRuntime();
    long maxMem = rt.maxMemory() / MEGABYTE;
    long allocatedMem = rt.totalMemory() / MEGABYTE;
    long usedMem = allocatedMem - rt.freeMemory() / MEGABYTE;

    if (allocatedMem != myLastAllocated || usedMem != myLastUsed) {
      myLastAllocated = allocatedMem;
      myLastUsed = usedMem;
      UIUtil.invokeLaterIfNeeded(() -> {
        myBufferedImage = null;
        repaint();
      });

      setToolTipText(UIBundle.message("memory.usage.panel.statistics.message", maxMem, allocatedMem, usedMem));
    }
  }
}