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
package com.intellij.xdebugger.impl.ui.tree;

import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AbstractExpandableItemsHandler;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import consulo.ui.ex.SimpleTextAttributes;
import com.intellij.util.ui.tree.TreeUtil;
import consulo.debugger.XDebuggerBundle;
import consulo.debugger.frame.ImmediateFullValueEvaluator;
import consulo.debugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author nik
 */
class XDebuggerTreeRenderer extends ColoredTreeCellRenderer {
  private static final Logger LOG = Logger.getInstance(XDebuggerTreeRenderer.class);

  private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
  private boolean myHaveLink;
  private int myLinkOffset;
  private int myLinkWidth;

  private final MyLongTextHyperlink myLongTextLink = new MyLongTextHyperlink();

  public XDebuggerTreeRenderer() {
    getIpad().right = 0;
    myLink.getIpad().left = 0;
  }

  @Override
  public void customizeCellRenderer(@Nonnull final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {
    myHaveLink = false;
    myLink.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    XDebuggerTreeNode node = (XDebuggerTreeNode)value;
    node.appendToComponent(this);
    setIcon(node.getIcon());

    Rectangle treeVisibleRect = tree.getParent() instanceof JViewport ? ((JViewport)tree.getParent()).getViewRect() : tree.getVisibleRect();
    TreePath path = tree.getPathForRow(row);
    int rowX = path != null ? TreeUtil.getNodeRowX(tree, row) : 0;

    if (myHaveLink) {
      setupLinkDimensions(treeVisibleRect, rowX);
    }
    else {
      int visibleRectRightX = treeVisibleRect.x + treeVisibleRect.width;
      int notFittingWidth = rowX + super.getPreferredSize().width - visibleRectRightX;
      if (node instanceof XValueNodeImpl && notFittingWidth > 0) {
        // text does not fit visible area - show link
        String rawValue = DebuggerUIUtil.getNodeRawValue((XValueNodeImpl)node);
        if (!StringUtil.isEmpty(rawValue) && tree.isShowing()) {
          Point treeRightSideOnScreen = new Point(visibleRectRightX, 0);
          SwingUtilities.convertPointToScreen(treeRightSideOnScreen, tree);
          Rectangle screen = AbstractExpandableItemsHandler.getScreenRectangle(treeRightSideOnScreen);
          // text may fit the screen in ExpandableItemsHandler
          if (screen.x + screen.width < treeRightSideOnScreen.x + notFittingWidth) {
            myLongTextLink.setupComponent(rawValue, ((XDebuggerTree)tree).getProject());
            append(myLongTextLink.getLinkText(), myLongTextLink.getTextAttributes(), myLongTextLink);
            setupLinkDimensions(treeVisibleRect, rowX);
            myLinkWidth = 0;
          }
        }
      }
    }
    putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myHaveLink);
  }

  private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
    Dimension linkSize = myLink.getPreferredSize();
    myLinkWidth = linkSize.width;
    myLinkOffset = Math.min(super.getPreferredSize().width, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
  }

  @Override
  public void append(@Nonnull String fragment, @Nonnull SimpleTextAttributes attributes, Object tag) {
    if (tag instanceof XDebuggerTreeNodeHyperlink && ((XDebuggerTreeNodeHyperlink)tag).alwaysOnScreen()) {
      myHaveLink = true;
      myLink.append(fragment, attributes, tag);
    }
    else {
      super.append(fragment, attributes, tag);
    }
  }

  @Override
  protected void doPaint(Graphics2D g) {
    if (myHaveLink) {
      Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, g.getClipBounds().height);
      try {
        super.doPaint(textGraphics);
      } finally {
        textGraphics.dispose();
      }
      g.translate(myLinkOffset, 0);
      myLink.setHeight(getHeight());
      myLink.doPaint(g);
      g.translate(-myLinkOffset, 0);
    }
    else {
      super.doPaint(g);
    }
  }

  @Nonnull
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myHaveLink) {
      size.width += myLinkWidth;
    }
    return size;
  }

  @Nullable
  @Override
  public Object getFragmentTagAt(int x) {
    if (myHaveLink) {
      return myLink.getFragmentTagAt(x - myLinkOffset);
    }
    return super.getFragmentTagAt(x);
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    private int myHeight;

    @Override
    public void customizeCellRenderer(@Nonnull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {}

    @Override
    protected void doPaint(Graphics2D g) {
      super.doPaint(g);
    }

    public void setHeight(int height) {
      myHeight = height;
    }

    @Override
    public int getHeight() {
      return myHeight;
    }
  }

  private static class MyLongTextHyperlink extends XDebuggerTreeNodeHyperlink {
    private String myText;
    private Project myProject;

    public MyLongTextHyperlink() {
      super(XDebuggerBundle.message("node.test.show.full.value"));
    }

    public void setupComponent(String text, Project project) {
      myText = text;
      myProject = project;
    }

    @Override
    public boolean alwaysOnScreen() {
      return true;
    }

    @Override
    public void onClick(MouseEvent event) {
      DebuggerUIUtil.showValuePopup(new ImmediateFullValueEvaluator(myText), event, myProject, null);
      event.consume();
    }
  }
}
