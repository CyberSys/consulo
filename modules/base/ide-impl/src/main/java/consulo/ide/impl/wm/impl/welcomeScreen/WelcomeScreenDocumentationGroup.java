/*
 * Copyright 2013-2022 consulo.io
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
package consulo.ide.impl.wm.impl.welcomeScreen;

import consulo.annotation.component.ActionImpl;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;

/**
 * @author VISTALL
 * @since 26-Jun-22
 */
@ActionImpl(id = IdeActions.GROUP_WELCOME_SCREEN_DOC, childrenRefs = {"HelpTopics", "ShowTips", "Help.KeymapReference", "Help.Youtube", "Help.JoinDiscordChannel", "WelcomeScreen.DevelopPlugins"})
public class WelcomeScreenDocumentationGroup extends DefaultActionGroup {
}
