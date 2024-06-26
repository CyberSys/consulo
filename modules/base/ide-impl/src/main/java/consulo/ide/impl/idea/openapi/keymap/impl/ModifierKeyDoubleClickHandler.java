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
package consulo.ide.impl.idea.openapi.keymap.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.ui.IdeEventQueueProxy;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.ex.action.*;
import consulo.ui.ex.action.event.AnActionListener;
import consulo.ui.ex.internal.ActionManagerEx;
import consulo.util.lang.Clock;
import consulo.util.lang.Couple;
import gnu.trove.TIntIntHashMap;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static consulo.ide.impl.idea.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;

/**
 * Support for keyboard shortcuts like Control-double-click or Control-double-click+A
 * <p>
 * Timings that are used in the implementation to detect double click were tuned for SearchEverywhere
 * functionality (invoked on double Shift), so if you need to change them, please make sure
 * SearchEverywhere behaviour remains intact.
 */
@Singleton
@ServiceAPI(value = ComponentScope.APPLICATION, lazy = false)
@ServiceImpl
public class ModifierKeyDoubleClickHandler implements Disposable {
  private static final Logger LOG = Logger.getInstance(ModifierKeyDoubleClickHandler.class);
  private static final TIntIntHashMap KEY_CODE_TO_MODIFIER_MAP = new TIntIntHashMap();

  static {
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_ALT, InputEvent.ALT_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_META, InputEvent.META_MASK);
    KEY_CODE_TO_MODIFIER_MAP.put(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK);
  }

  private final ActionManager myActionManager;
  private final ConcurrentMap<String, MyDispatcher> myDispatchers = new ConcurrentHashMap<>();
  private boolean myIsRunningAction;

  @Inject
  ModifierKeyDoubleClickHandler(ActionManager actionManager) {
    myActionManager = actionManager;

    int modifierKeyCode = getMultiCaretActionModifier();
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE, modifierKeyCode, KeyEvent.VK_UP);
    registerAction(IdeActions.ACTION_EDITOR_CLONE_CARET_BELOW, modifierKeyCode, KeyEvent.VK_DOWN);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_LEFT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_RIGHT);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_HOME);
    registerAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END_WITH_SELECTION, modifierKeyCode, KeyEvent.VK_END);
  }

  @Override
  public void dispose() {
    for (MyDispatcher dispatcher : myDispatchers.values()) {
      Disposer.dispose(dispatcher);
    }
    myDispatchers.clear();
  }

  public static ModifierKeyDoubleClickHandler getInstance() {
    return ApplicationManager.getApplication().getInstance(ModifierKeyDoubleClickHandler.class);
  }

  public static int getMultiCaretActionModifier() {
    return Platform.current().os().isMac() ? KeyEvent.VK_ALT : KeyEvent.VK_CONTROL;
  }

  /**
   * @param actionId                Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode         keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode           keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   * @param skipIfActionHasShortcut do not invoke action if a shortcut is already bound to it in keymap
   */
  public void registerAction(@Nonnull String actionId, int modifierKeyCode, int actionKeyCode, boolean skipIfActionHasShortcut) {
    final MyDispatcher dispatcher = new MyDispatcher(actionId, modifierKeyCode, actionKeyCode, skipIfActionHasShortcut);
    MyDispatcher oldDispatcher = myDispatchers.put(actionId, dispatcher);
    IdeEventQueueProxy.getInstance().addDispatcher(dispatcher, dispatcher);
    myActionManager.addAnActionListener(dispatcher, dispatcher);
    if (oldDispatcher != null) {
      Disposer.dispose(oldDispatcher);
    }
  }

  /**
   * @param actionId        Id of action to be triggered on modifier+modifier[+actionKey]
   * @param modifierKeyCode keyCode for modifier, e.g. KeyEvent.VK_SHIFT
   * @param actionKeyCode   keyCode for actionKey, or -1 if action should be triggered on bare modifier double click
   */
  public void registerAction(@Nonnull String actionId, int modifierKeyCode, int actionKeyCode) {
    registerAction(actionId, modifierKeyCode, actionKeyCode, true);
  }

  public void unregisterAction(@Nonnull String actionId) {
    MyDispatcher oldDispatcher = myDispatchers.remove(actionId);
    if (oldDispatcher != null) {
      Disposer.dispose(oldDispatcher);
    }
  }

  public boolean isRunningAction() {
    return myIsRunningAction;
  }

  private class MyDispatcher implements AnActionListener, Predicate<AWTEvent>, Disposable {
    private final String myActionId;
    private final int myModifierKeyCode;
    private final int myActionKeyCode;
    private final boolean mySkipIfActionHasShortcut;

    private final Couple<AtomicBoolean> ourPressed = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final Couple<AtomicBoolean> ourReleased = Couple.of(new AtomicBoolean(false), new AtomicBoolean(false));
    private final AtomicBoolean ourOtherKeyWasPressed = new AtomicBoolean(false);
    private final AtomicLong ourLastTimePressed = new AtomicLong(0);

    public MyDispatcher(@Nonnull String actionId, int modifierKeyCode, int actionKeyCode, boolean skipIfActionHasShortcut) {
      myActionId = actionId;
      myModifierKeyCode = modifierKeyCode;
      myActionKeyCode = actionKeyCode;
      mySkipIfActionHasShortcut = skipIfActionHasShortcut;
    }

    @Override
    public boolean test(@Nonnull AWTEvent event) {
      if (event instanceof KeyEvent keyEvent) {
        final int keyCode = keyEvent.getKeyCode();
        LOG.debug("", this, event);
        if (keyCode == myModifierKeyCode) {
          if (hasOtherModifiers(keyEvent)) {
            resetState();
            return false;
          }
          if (myActionKeyCode == -1 && ourOtherKeyWasPressed.get() && Clock.getTime() - ourLastTimePressed.get() < 500) {
            resetState();
            return false;
          }
          ourOtherKeyWasPressed.set(false);
          if (ourPressed.first.get() && Clock.getTime() - ourLastTimePressed.get() > 500) {
            resetState();
          }
          handleModifier((KeyEvent)event);
          return false;
        }
        else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get() && myActionKeyCode != -1) {
          if (keyCode == myActionKeyCode && !hasOtherModifiers(keyEvent)) {
            if (event.getID() == KeyEvent.KEY_PRESSED) {
              return run(keyEvent);
            }
            return true;
          }
          return false;
        }
        else {
          ourLastTimePressed.set(Clock.getTime());
          ourOtherKeyWasPressed.set(true);
          if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_TAB) {
            ourLastTimePressed.set(0);
          }
        }
        resetState();
      }
      return false;
    }

    private boolean hasOtherModifiers(KeyEvent keyEvent) {
      final int modifiers = keyEvent.getModifiers();
      return !KEY_CODE_TO_MODIFIER_MAP.forEachEntry(
        (keyCode, modifierMask) -> keyCode == myModifierKeyCode || (modifiers & modifierMask) == 0
      );
    }

    private void handleModifier(KeyEvent event) {
      if (ourPressed.first.get() && Clock.getTime() - ourLastTimePressed.get() > 300) {
        resetState();
        return;
      }

      if (event.getID() == KeyEvent.KEY_PRESSED) {
        if (!ourPressed.first.get()) {
          resetState();
          ourPressed.first.set(true);
          ourLastTimePressed.set(Clock.getTime());
          return;
        }
        else {
          if (ourPressed.first.get() && ourReleased.first.get()) {
            ourPressed.second.set(true);
            ourLastTimePressed.set(Clock.getTime());
            return;
          }
        }
      }
      else if (event.getID() == KeyEvent.KEY_RELEASED) {
        if (ourPressed.first.get() && !ourReleased.first.get()) {
          ourReleased.first.set(true);
          ourLastTimePressed.set(Clock.getTime());
          return;
        }
        else if (ourPressed.first.get() && ourReleased.first.get() && ourPressed.second.get()) {
          resetState();
          if (myActionKeyCode == -1 && !shouldSkipIfActionHasShortcut()) {
            run(event);
          }
          return;
        }
      }
      resetState();
    }

    private void resetState() {
      ourPressed.first.set(false);
      ourPressed.second.set(false);
      ourReleased.first.set(false);
      ourReleased.second.set(false);
    }

    private boolean run(KeyEvent event) {
      myIsRunningAction = true;
      try {
        AnAction action = myActionManager.getAction(myActionId);
        if (action == null) return false;
        DataContext context = DataManager.getInstance().getDataContext(IdeFocusManager.findInstance().getFocusOwner());
        AnActionEvent anActionEvent = AnActionEvent.createFromAnAction(action, event, ActionPlaces.MAIN_MENU, context);
        action.update(anActionEvent);
        if (!anActionEvent.getPresentation().isEnabled()) return false;

        ((ActionManagerEx)myActionManager).fireBeforeActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        action.actionPerformed(anActionEvent);
        ((ActionManagerEx)myActionManager).fireAfterActionPerformed(action, anActionEvent.getDataContext(), anActionEvent);
        return true;
      }
      finally {
        myIsRunningAction = false;
      }
    }

    private boolean shouldSkipIfActionHasShortcut() {
      return mySkipIfActionHasShortcut && getActiveKeymapShortcuts(myActionId).getShortcuts().length > 0;
    }

    @Override
    public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
      if (!myIsRunningAction) resetState();
    }

    @Override
    public void dispose() {
    }

    @Override
    public String toString() {
      return "modifier double-click dispatcher [modifierKeyCode=" + myModifierKeyCode + ",actionKeyCode=" + myActionKeyCode + ",actionId=" + myActionId + "]";
    }
  }
}
