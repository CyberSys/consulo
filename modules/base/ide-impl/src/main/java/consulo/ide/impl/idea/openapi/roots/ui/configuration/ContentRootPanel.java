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

package consulo.ide.impl.idea.openapi.roots.ui.configuration;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import consulo.content.ContentFolderTypeProvider;
import consulo.content.base.ExcludedContentFolderTypeProvider;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.ui.HoverHyperlinkLabel;
import consulo.ide.impl.idea.ui.roots.FilePathClipper;
import consulo.ide.impl.idea.ui.roots.IconActionComponent;
import consulo.ide.impl.idea.ui.roots.ResizingWrapper;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.localize.ProjectLocalize;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.style.StyleManager;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * Date: Jan 19, 2004
 */
public abstract class ContentRootPanel extends JPanel {
  private static final Color SELECTED_HEADER_COLOR = new JBColor(new Color(0xDEF2FF), UIUtil.getPanelBackground().darker());
  private static final Color HEADER_COLOR = new JBColor(new Color(0xF5F5F5), Gray._82);
  private static final Color SELECTED_CONTENT_COLOR = new Color(0xF0F9FF);
  private static final Color CONTENT_COLOR = new JBColor(Color.WHITE, UIUtil.getPanelBackground());
  private static final Color UNSELECTED_TEXT_COLOR = Gray._51;

  protected final ActionCallback myCallback;
  private JComponent myHeader;
  private JComponent myBottom;
  private final Map<JComponent, Color> myComponentToForegroundMap = new HashMap<JComponent, Color>();

  public interface ActionCallback {
    void deleteContentEntry();

    void deleteContentFolder(ContentEntry contentEntry, ContentFolder contentFolder);

    void showChangeOptionsDialog(ContentEntry contentEntry, ContentFolder contentFolder);

    void navigateFolder(ContentEntry contentEntry, ContentFolder contentFolder);
  }

  public ContentRootPanel(ActionCallback callback) {
    super(new GridBagLayout());
    myCallback = callback;
  }

  @Nonnull
  protected abstract ContentEntry getContentEntry();

  public void initUI() {
    myHeader = createHeader();
    this.add(myHeader,
             new GridBagConstraints(0,
                                    GridBagConstraints.RELATIVE,
                                    1,
                                    1,
                                    1.0,
                                    0.0,
                                    GridBagConstraints.NORTHWEST,
                                    GridBagConstraints.HORIZONTAL,
                                    new Insets(0, 0, 8, 0),
                                    0,
                                    0));

    addFolderGroupComponents();

    myBottom = new JPanel(new BorderLayout());
    myBottom.add(Box.createVerticalStrut(3), BorderLayout.NORTH);
    this.add(myBottom,
             new GridBagConstraints(0,
                                    GridBagConstraints.RELATIVE,
                                    1,
                                    1,
                                    1.0,
                                    1.0,
                                    GridBagConstraints.NORTH,
                                    GridBagConstraints.HORIZONTAL,
                                    new Insets(0, 0, 0, 0),
                                    0,
                                    0));

    setSelected(false);
  }

  protected void addFolderGroupComponents() {
    final ContentFolder[] contentFolders = getContentEntry().getFolders(LanguageContentFolderScopes.all());
    MultiMap<ContentFolderTypeProvider, ContentFolder> folderByType = new MultiMap<>();
    for (ContentFolder folder : contentFolders) {
      if (folder.isSynthetic()) {
        continue;
      }
      final VirtualFile folderFile = folder.getFile();
      if (folderFile != null && isExcludedOrUnderExcludedDirectory(folderFile)) {
        continue;
      }
      folderByType.putValue(folder.getType(), folder);
    }

    Insets insets = new Insets(0, 0, 10, 0);
    GridBagConstraints constraints = new GridBagConstraints(
      0,
      GridBagConstraints.RELATIVE,
      1,
      1,
      1.0,
      0.0,
      GridBagConstraints.NORTH,
      GridBagConstraints.HORIZONTAL,
      insets,
      0,
      0
    );
    for (Map.Entry<ContentFolderTypeProvider, Collection<ContentFolder>> entry : folderByType.entrySet()) {
      Collection<ContentFolder> folders = entry.getValue();
      if (folders.isEmpty()) continue;

      ContentFolderTypeProvider contentFolderTypeProvider = entry.getKey();

      ContentFolder[] foldersArray = folders.toArray(new ContentFolder[folders.size()]);
      final JComponent sourcesComponent = createFolderGroupComponent(
        contentFolderTypeProvider.getName(),
        foldersArray,
        TargetAWT.to(contentFolderTypeProvider.getGroupColor()),
        contentFolderTypeProvider
      );
      add(sourcesComponent, constraints);
    }
  }

  private JComponent createHeader() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JLabel headerLabel = new JLabel(toDisplayPath(getContentEntry().getUrl()));
    headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));
    headerLabel.setOpaque(false);
    if (getContentEntry().getFile() == null) {
      headerLabel.setForeground(Color.RED);
    }
    final IconActionComponent deleteIconComponent = new IconActionComponent(
      PlatformIconGroup.actionsClose(),
      PlatformIconGroup.actionsClosehovered(),
      ProjectLocalize.modulePathsRemoveContentTooltip().get(),
      myCallback::deleteContentEntry
    );
    final ResizingWrapper wrapper = new ResizingWrapper(headerLabel);
    panel.add(
      wrapper,
      new GridBagConstraints(
        0,
        GridBagConstraints.RELATIVE,
        1,
        1,
        1.0,
        1.0,
        GridBagConstraints.NORTHWEST,
        GridBagConstraints.HORIZONTAL,
        new Insets(0, 2, 0, 0),
        0,
        0
      )
    );
    panel.add(
      deleteIconComponent,
      new GridBagConstraints(
        1,
        GridBagConstraints.RELATIVE,
        1,
        1,
        0.0,
        1.0,
        GridBagConstraints.EAST,
        GridBagConstraints.NONE,
        new Insets(0, 0, 0, 2),
        0,
        0
      )
    );
    FilePathClipper.install(headerLabel, wrapper);
    return panel;
  }

  protected JComponent createFolderGroupComponent(
    String title,
    ContentFolder[] folders,
    Color foregroundColor,
    @Nonnull ContentFolderTypeProvider editor
  ) {
    final JPanel panel = new JPanel(new GridLayoutManager(folders.length, 3, new Insets(1, 17, 0, 2), 0, 1));
    panel.setOpaque(false);

    for (int idx = 0; idx < folders.length; idx++) {
      final ContentFolder folder = folders[idx];
      final int verticalPolicy = idx == folders.length - 1 ? GridConstraints.SIZEPOLICY_CAN_GROW : GridConstraints.SIZEPOLICY_FIXED;
      panel.add(
        createFolderComponent(folder, foregroundColor),
        new GridConstraints(
          idx,
          0,
          1,
          1,
          GridConstraints.ANCHOR_NORTHWEST,
          GridConstraints.FILL_HORIZONTAL,
          GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_CAN_SHRINK,
          verticalPolicy,
          null,
          null,
          null
        )
      );

      panel.add(
        createFolderChangeOptionsComponent(folder, editor),
        new GridConstraints(
          idx,
          1,
          1,
          1,
          GridConstraints.ANCHOR_CENTER,
          GridConstraints.FILL_NONE,
          GridConstraints.SIZEPOLICY_FIXED,
          verticalPolicy,
          null,
          null,
          null
        )
      );

      panel.add(
        createFolderDeleteComponent(folder, editor),
        new GridConstraints(
          idx,
          2,
          1,
          1,
          GridConstraints.ANCHOR_EAST,
          GridConstraints.FILL_NONE,
          GridConstraints.SIZEPOLICY_FIXED,
          verticalPolicy,
          null,
          null,
          null
        )
      );
    }

    final JLabel titleLabel = new JLabel(title);
    final Font labelFont = UIUtil.getLabelFont();
    titleLabel.setFont(labelFont.deriveFont(Font.BOLD));
    titleLabel.setOpaque(false);
    titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
    registerTextComponent(titleLabel, foregroundColor);

    final JPanel groupPanel = new JPanel(new BorderLayout());
    groupPanel.setOpaque(false);
    groupPanel.add(titleLabel, BorderLayout.NORTH);
    groupPanel.add(panel, BorderLayout.CENTER);

    return groupPanel;
  }

  private void registerTextComponent(final JComponent component, final Color foreground) {
    component.setForeground(foreground);
    myComponentToForegroundMap.put(component, foreground);
  }

  private JComponent createFolderComponent(final ContentFolder folder, Color foreground) {
    final VirtualFile folderFile = folder.getFile();
    final VirtualFile contentEntryFile = getContentEntry().getFile();
    final String properties = "";
    if (folderFile != null && contentEntryFile != null) {
      String path =
        folderFile.equals(contentEntryFile) ? "." : VfsUtilCore.getRelativePath(folderFile, contentEntryFile, File.separatorChar);
      HoverHyperlinkLabel hyperlinkLabel = new HoverHyperlinkLabel(path + properties, foreground);
      hyperlinkLabel.setMinimumSize(new Dimension(0, 0));
      hyperlinkLabel.addHyperlinkListener(e -> myCallback.navigateFolder(getContentEntry(), folder));
      registerTextComponent(hyperlinkLabel, foreground);
      return new UnderlinedPathLabel(hyperlinkLabel);
    }
    else {
      String path = toRelativeDisplayPath(folder.getUrl(), getContentEntry().getUrl());
      final JLabel pathLabel = new JLabel(path + properties);
      pathLabel.setOpaque(false);
      pathLabel.setForeground(Color.RED);

      return new UnderlinedPathLabel(pathLabel);
    }
  }

  private JComponent createFolderChangeOptionsComponent(
    final ContentFolder folder,
    @Nonnull ContentFolderTypeProvider editor
  ) {
    return new IconActionComponent(
      PlatformIconGroup.generalInline_edit(),
      PlatformIconGroup.generalInline_edit_hovered(),
      ProjectLocalize.modulePathsPropertiesTooltip().get(),
      () -> myCallback.showChangeOptionsDialog(getContentEntry(), folder)
    );
  }

  private JComponent createFolderDeleteComponent(final ContentFolder folder, @Nonnull ContentFolderTypeProvider editor) {
    final LocalizeValue tooltipText;
    if (folder.getFile() != null && getContentEntry().getFile() != null) {
      tooltipText = ProjectLocalize.modulePathsUnmark0Tooltip(editor.getName());
    }
    else {
      tooltipText = ProjectLocalize.modulePathsRemoveTooltip();
    }
    return new IconActionComponent(
      PlatformIconGroup.actionsClose(),
      PlatformIconGroup.actionsClosehovered(),
      tooltipText.get(),
      () -> myCallback.deleteContentFolder(getContentEntry(), folder)
    );
  }

  public boolean isExcludedOrUnderExcludedDirectory(final VirtualFile file) {
    final ContentEntry contentEntry = getContentEntry();
    for (VirtualFile excludedDir : contentEntry.getFolderFiles(LanguageContentFolderScopes.of(ExcludedContentFolderTypeProvider.getInstance()))) {
      if (VfsUtilCore.isAncestor(excludedDir, file, false)) {
        return true;
      }
    }
    return false;
  }

  protected static String toRelativeDisplayPath(String url, String ancestorUrl) {
    if (!StringUtil.endsWithChar(ancestorUrl, '/')) {
      ancestorUrl += "/";
    }
    if (url.startsWith(ancestorUrl)) {
      return url.substring(ancestorUrl.length()).replace('/', File.separatorChar);
    }
    return toDisplayPath(url);
  }

  private static String toDisplayPath(final String url) {
    return VirtualFileManager.extractPath(url).replace('/', File.separatorChar);
  }


  public void setSelected(boolean selected) {
    boolean darkMode = StyleManager.get().getCurrentStyle().isDark();
    if (selected) {
      myHeader.setBackground(SELECTED_HEADER_COLOR);
      setBackground(darkMode ? UIUtil.getPanelBackground() : SELECTED_CONTENT_COLOR);
      myBottom.setBackground(darkMode ? UIUtil.getPanelBackground() : SELECTED_HEADER_COLOR);
      for (final JComponent component : myComponentToForegroundMap.keySet()) {
        component.setForeground(myComponentToForegroundMap.get(component));
      }
    }
    else {
      myHeader.setBackground(HEADER_COLOR);
      setBackground(CONTENT_COLOR);
      myBottom.setBackground(darkMode ? UIUtil.getPanelBackground() : HEADER_COLOR);
      for (final JComponent component : myComponentToForegroundMap.keySet()) {
        component.setForeground(UNSELECTED_TEXT_COLOR);
      }
    }
  }

  private static class UnderlinedPathLabel extends ResizingWrapper {
    private static final float[] DASH = {0, 2, 0, 2};
    private static final Color DASH_LINE_COLOR = new JBColor(Gray._201, Gray._100);

    public UnderlinedPathLabel(JLabel wrappedComponent) {
      super(wrappedComponent);
      FilePathClipper.install(wrappedComponent, this);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      final int startX = myWrappedComponent.getWidth();
      final int endX = getWidth();
      if (endX > startX) {
        final FontMetrics fontMetrics = myWrappedComponent.getFontMetrics(myWrappedComponent.getFont());
        final int y = fontMetrics.getMaxAscent();
        final Color savedColor = g.getColor();
        g.setColor(DASH_LINE_COLOR);
        drawDottedLine((Graphics2D)g, startX, y, endX, y);
        g.setColor(savedColor);
      }
    }

    private void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2) {
      /*
      // TODO!!!
      final Color color = g.getColor();
      g.setColor(getBackground());
      g.setColor(color);
      for (int i = x1 / 2 * 2; i < x2; i += 2) {
        g.drawRect(i, y1, 0, 0);
      }
      */
      final Stroke saved = g.getStroke();
      boolean darkMode = StyleManager.get().getCurrentStyle().isDark();
      if (!Platform.current().os().isMac() && !darkMode) {
        g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, DASH, y1 % 2));
      }

      if (darkMode) {
        UIUtil.drawDottedLine(g, x1, y1, x2, y2, null, g.getColor());
      }
      else {
        UIUtil.drawLine(g, x1, y1, x2, y2);
      }

      g.setStroke(saved);
    }
  }
}
