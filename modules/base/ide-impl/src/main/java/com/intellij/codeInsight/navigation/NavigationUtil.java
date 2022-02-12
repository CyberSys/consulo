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

package com.intellij.codeInsight.navigation;

import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import consulo.navigation.NavigationItem;
import consulo.dataContext.DataContext;
import consulo.editor.Editor;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import consulo.editor.markup.HighlighterTargetArea;
import consulo.editor.markup.MarkupModel;
import consulo.editor.markup.TextAttributes;
import consulo.component.extension.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import consulo.virtualFileSystem.fileType.INativeFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import consulo.ui.ex.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import consulo.util.lang.ref.Ref;
import consulo.document.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.navigation.Navigatable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.resolve.PsiElementProcessor;
import com.intellij.ui.ColoredListCellRenderer;
import consulo.ui.ex.JBColor;
import com.intellij.ui.SeparatorWithText;
import consulo.ui.ex.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import consulo.application.util.function.Processor;
import com.intellij.util.containers.ContainerUtil;
import consulo.application.ui.awt.UIUtil;
import consulo.ui.color.RGBColor;
import consulo.ui.image.Image;
import consulo.ui.style.StandardColors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * @author ven
 */
public final class NavigationUtil {

  private NavigationUtil() {
  }

  @Nonnull
  public static JBPopup getPsiElementPopup(@Nonnull PsiElement[] elements, String title) {
    return getPsiElementPopup(elements, new DefaultPsiElementCellRenderer(), title);
  }

  @Nonnull
  public static JBPopup getPsiElementPopup(@Nonnull PsiElement[] elements, @Nonnull final PsiElementListCellRenderer<PsiElement> renderer, final String title) {
    return getPsiElementPopup(elements, renderer, title, new PsiElementProcessor<PsiElement>() {
      @Override
      public boolean execute(@Nonnull final PsiElement element) {
        Navigatable descriptor = EditSourceUtil.getDescriptor(element);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(true);
        }
        return true;
      }
    });
  }

  @Nonnull
  public static <T extends PsiElement> JBPopup getPsiElementPopup(@Nonnull T[] elements,
                                                                  @Nonnull final PsiElementListCellRenderer<T> renderer,
                                                                  final String title,
                                                                  @Nonnull final PsiElementProcessor<T> processor) {
    return getPsiElementPopup(elements, renderer, title, processor, null);
  }

  @Nonnull
  public static <T extends PsiElement> JBPopup getPsiElementPopup(@Nonnull T[] elements,
                                                                  @Nonnull final PsiElementListCellRenderer<T> renderer,
                                                                  @Nullable final String title,
                                                                  @Nonnull final PsiElementProcessor<T> processor,
                                                                  @Nullable final T selection) {
    final JList list = new JBList(elements);
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    list.setCellRenderer(renderer);

    list.setFont(EditorUtil.getEditorFont());

    if (selection != null) {
      list.setSelectedValue(selection, true);
    }

    final Runnable runnable = () -> {
      int[] ids = list.getSelectedIndices();
      if (ids == null || ids.length == 0) return;
      for (Object element : list.getSelectedValues()) {
        if (element != null) {
          processor.execute((T)element);
        }
      }
    };

    PopupChooserBuilder builder = new PopupChooserBuilder(list);
    if (title != null) {
      builder.setTitle(title);
    }
    renderer.installSpeedSearch(builder, true);

    JBPopup popup = builder.setItemChoosenCallback(runnable).createPopup();

    builder.getScrollPane().setBorder(null);
    builder.getScrollPane().setViewportBorder(null);

    hidePopupIfDumbModeStarts(popup, elements[0].getProject());

    return popup;
  }

  public static void hidePopupIfDumbModeStarts(@Nonnull JBPopup popup, @Nonnull Project project) {
    if (!DumbService.isDumb(project)) {
      project.getMessageBus().connect(popup).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void enteredDumbMode() {
          popup.cancel();
        }
      });
    }
  }

  public static boolean activateFileWithPsiElement(@Nonnull PsiElement elt) {
    return activateFileWithPsiElement(elt, true);
  }

  public static boolean activateFileWithPsiElement(@Nonnull PsiElement elt, boolean searchForOpen) {
    return openFileWithPsiElement(elt, searchForOpen, true);
  }

  public static boolean openFileWithPsiElement(PsiElement element, boolean searchForOpen, boolean requestFocus) {
    boolean openAsNative = false;
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (virtualFile != null) {
        openAsNative = virtualFile.getFileType() instanceof INativeFileType || virtualFile.getFileType() == UnknownFileType.INSTANCE;
      }
    }

    if (searchForOpen) {
      element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    }
    else {
      element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true);
    }

    if (openAsNative || !activatePsiElementIfOpen(element, searchForOpen, requestFocus)) {
      final NavigationItem navigationItem = (NavigationItem)element;
      if (!navigationItem.canNavigate()) return false;
      navigationItem.navigate(requestFocus);
      return true;
    }

    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null);
    return false;
  }

  private static boolean activatePsiElementIfOpen(@Nonnull PsiElement elt, boolean searchForOpen, boolean requestFocus) {
    if (!elt.isValid()) return false;
    elt = elt.getNavigationElement();
    final PsiFile file = elt.getContainingFile();
    if (file == null || !file.isValid()) return false;

    VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) return false;

    if (!EditorHistoryManager.getInstance(elt.getProject()).hasBeenOpen(vFile)) return false;

    final FileEditorManager fem = FileEditorManager.getInstance(elt.getProject());
    if (!fem.isFileOpen(vFile)) {
      fem.openFile(vFile, requestFocus, searchForOpen);
    }

    final TextRange range = elt.getTextRange();
    if (range == null) return false;

    final FileEditor[] editors = fem.getEditors(vFile);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        final Editor text = ((TextEditor)editor).getEditor();
        final int offset = text.getCaretModel().getOffset();

        if (range.containsOffset(offset)) {
          // select the file
          fem.openFile(vFile, requestFocus, searchForOpen);
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Patches attributes to be visible under debugger active line
   */
  @SuppressWarnings("UseJBColor")
  public static TextAttributes patchAttributesColor(TextAttributes attributes, @Nonnull TextRange range, @Nonnull Editor editor) {
    if (attributes.getForegroundColor() == null && attributes.getEffectColor() == null) return attributes;
    MarkupModel model = DocumentMarkupModel.forDocument(editor.getDocument(), editor.getProject(), false);
    if (model != null) {
      if (!((MarkupModelEx)model).processRangeHighlightersOverlappingWith(range.getStartOffset(), range.getEndOffset(), highlighter -> {
        if (highlighter.isValid() && highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) {
          TextAttributes textAttributes = highlighter.getTextAttributes();
          if (textAttributes != null) {
            RGBColor color = textAttributes.getBackgroundColor() == null ? null : textAttributes.getBackgroundColor().toRGB();
            return !(color != null && color.getBlue() > 128 && color.getRed() < 128 && color.getGreen() < 128);
          }
        }
        return true;
      })) {
        TextAttributes clone = attributes.clone();
        clone.setForegroundColor(StandardColors.ORANGE);
        clone.setEffectColor(StandardColors.ORANGE);
        return clone;
      }
    }
    return attributes;
  }

  @Nonnull
  public static JBPopup getRelatedItemsPopup(final List<? extends GotoRelatedItem> items, String title) {
    return getRelatedItemsPopup(items, title, false);
  }

  /**
   * Returns navigation popup that shows list of related items from {@code items} list
   *
   * @param items
   * @param title
   * @param showContainingModules Whether the popup should show additional information that aligned at the right side of the dialog.<br>
   *                              It's usually a module name or library name of corresponding navigation item.<br>
   *                              {@code false} by default
   * @return
   */
  @Nonnull
  public static JBPopup getRelatedItemsPopup(final List<? extends GotoRelatedItem> items, String title, boolean showContainingModules) {
    Object[] elements = new Object[items.size()];
    //todo[nik] move presentation logic to GotoRelatedItem class
    final Map<PsiElement, GotoRelatedItem> itemsMap = new HashMap<>();
    for (int i = 0; i < items.size(); i++) {
      GotoRelatedItem item = items.get(i);
      elements[i] = item.getElement() != null ? item.getElement() : item;
      itemsMap.put(item.getElement(), item);
    }

    return getPsiElementPopup(elements, itemsMap, title, showContainingModules, element -> {
      if (element instanceof PsiElement) {
        //noinspection SuspiciousMethodCalls
        itemsMap.get(element).navigate();
      }
      else {
        ((GotoRelatedItem)element).navigate();
      }
      return true;
    });
  }

  private static JBPopup getPsiElementPopup(final Object[] elements,
                                            final Map<PsiElement, GotoRelatedItem> itemsMap,
                                            final String title,
                                            final boolean showContainingModules,
                                            final Processor<Object> processor) {

    final Ref<Boolean> hasMnemonic = Ref.create(false);
    final DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer() {
      {
        setFocusBorderEnabled(false);
      }

      @Override
      public String getElementText(PsiElement element) {
        String customName = itemsMap.get(element).getCustomName();
        return (customName != null ? customName : super.getElementText(element));
      }

      @Override
      protected Image getIcon(PsiElement element) {
        Image customIcon = itemsMap.get(element).getCustomIcon();
        return customIcon != null ? customIcon : super.getIcon(element);
      }

      @Override
      public String getContainerText(PsiElement element, String name) {
        String customContainerName = itemsMap.get(element).getCustomContainerName();

        if (customContainerName != null) {
          return customContainerName;
        }
        PsiFile file = element.getContainingFile();
        return file != null && !getElementText(element).equals(file.getName()) ? "(" + file.getName() + ")" : null;
      }

      @Override
      protected DefaultListCellRenderer getRightCellRenderer(Object value) {
        return showContainingModules ? super.getRightCellRenderer(value) : null;
      }

      @Override
      protected boolean customizeNonPsiElementLeftRenderer(ColoredListCellRenderer renderer, JList list, Object value, int index, boolean selected, boolean hasFocus) {
        final GotoRelatedItem item = (GotoRelatedItem)value;
        Color color = list.getForeground();
        final SimpleTextAttributes nameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color);
        final String name = item.getCustomName();
        if (name == null) return false;
        renderer.append(name, nameAttributes);
        renderer.setIcon(item.getCustomIcon());
        return true;
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final JPanel component = (JPanel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!hasMnemonic.get()) return component;

        final JPanel panelWithMnemonic = new JPanel(new BorderLayout());
        final int mnemonic = getMnemonic(value, itemsMap);
        final JLabel label = new JLabel("");
        if (mnemonic != -1) {
          label.setText(mnemonic + ".");
          label.setDisplayedMnemonicIndex(0);
        }
        label.setPreferredSize(new JLabel("8.").getPreferredSize());

        final JComponent leftRenderer = (JComponent)component.getComponents()[0];
        component.remove(leftRenderer);
        panelWithMnemonic.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 0));
        panelWithMnemonic.setBackground(leftRenderer.getBackground());
        label.setBackground(leftRenderer.getBackground());
        panelWithMnemonic.add(label, BorderLayout.WEST);
        panelWithMnemonic.add(leftRenderer, BorderLayout.CENTER);
        component.add(panelWithMnemonic);
        return component;
      }
    };
    final ListPopupImpl popup = new ListPopupImpl(new BaseListPopupStep<Object>(title, Arrays.asList(elements)) {
      @Override
      public boolean isSpeedSearchEnabled() {
        return true;
      }

      @Override
      public String getIndexedString(Object value) {
        if (value instanceof GotoRelatedItem) {
          //noinspection ConstantConditions
          return ((GotoRelatedItem)value).getCustomName();
        }
        PsiElement element = (PsiElement)value;
        if (!element.isValid()) return "INVALID";
        return renderer.getElementText(element) + " " + renderer.getContainerText(element, null);
      }

      @Override
      public PopupStep onChosen(Object selectedValue, boolean finalChoice) {
        processor.process(selectedValue);
        return super.onChosen(selectedValue, finalChoice);
      }
    }) {
    };
    popup.getList().setCellRenderer(new PopupListElementRenderer(popup) {
      Map<Object, String> separators = new HashMap<>();

      {
        final ListModel model = popup.getList().getModel();
        String current = null;
        boolean hasTitle = false;
        for (int i = 0; i < model.getSize(); i++) {
          final Object element = model.getElementAt(i);
          final GotoRelatedItem item = itemsMap.get(element);
          if (item != null && !StringUtil.equals(current, item.getGroup())) {
            current = item.getGroup();
            separators.put(element, current);
            if (!hasTitle && !StringUtil.isEmpty(current)) {
              hasTitle = true;
            }
          }
        }

        if (!hasTitle) {
          separators.remove(model.getElementAt(0));
        }
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component component = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final String separator = separators.get(value);

        if (separator != null) {
          JPanel panel = new JPanel(new BorderLayout());
          panel.add(component, BorderLayout.CENTER);
          final SeparatorWithText sep = new SeparatorWithText() {
            @Override
            protected void paintComponent(Graphics g) {
              g.setColor(new JBColor(Color.WHITE, UIUtil.getSeparatorColor()));
              g.fillRect(0, 0, getWidth(), getHeight());
              super.paintComponent(g);
            }
          };
          sep.setCaption(separator);
          panel.add(sep, BorderLayout.NORTH);
          return panel;
        }
        return component;
      }
    });

    popup.setMinimumSize(new Dimension(200, -1));

    for (Object item : elements) {
      final int mnemonic = getMnemonic(item, itemsMap);
      if (mnemonic != -1) {
        final Action action = createNumberAction(mnemonic, popup, itemsMap, processor);
        popup.registerAction(mnemonic + "Action", KeyStroke.getKeyStroke(String.valueOf(mnemonic)), action);
        popup.registerAction(mnemonic + "Action", KeyStroke.getKeyStroke("NUMPAD" + String.valueOf(mnemonic)), action);
        hasMnemonic.set(true);
      }
    }
    return popup;
  }

  private static Action createNumberAction(final int mnemonic, final ListPopupImpl listPopup, final Map<PsiElement, GotoRelatedItem> itemsMap, final Processor<Object> processor) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (final Object item : listPopup.getListStep().getValues()) {
          if (getMnemonic(item, itemsMap) == mnemonic) {
            listPopup.setFinalRunnable(() -> processor.process(item));
            listPopup.closeOk(null);
          }
        }
      }
    };
  }

  private static int getMnemonic(Object item, Map<PsiElement, GotoRelatedItem> itemsMap) {
    return (item instanceof GotoRelatedItem ? (GotoRelatedItem)item : itemsMap.get((PsiElement)item)).getMnemonic();
  }

  @Nonnull
  public static List<GotoRelatedItem> collectRelatedItems(@Nonnull PsiElement contextElement, @Nullable DataContext dataContext) {
    Set<GotoRelatedItem> items = ContainerUtil.newLinkedHashSet();
    for (GotoRelatedProvider provider : Extensions.getExtensions(GotoRelatedProvider.EP_NAME)) {
      items.addAll(provider.getItems(contextElement));
      if (dataContext != null) {
        items.addAll(provider.getItems(dataContext));
      }
    }
    GotoRelatedItem[] result = items.toArray(new GotoRelatedItem[items.size()]);
    Arrays.sort(result, (i1, i2) -> {
      String o1 = i1.getGroup();
      String o2 = i2.getGroup();
      return StringUtil.isEmpty(o1) ? 1 : StringUtil.isEmpty(o2) ? -1 : o1.compareTo(o2);
    });
    return Arrays.asList(result);
  }
}
