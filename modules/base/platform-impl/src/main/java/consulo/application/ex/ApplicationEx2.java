/*
 * Copyright 2013-2016 consulo.io
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
package consulo.application.ex;

import com.intellij.openapi.application.ex.ApplicationEx;
import consulo.components.impl.stores.IApplicationStore;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 12:05/12.08.13
 */
public interface ApplicationEx2 extends ApplicationEx {
  @Nonnull
  IApplicationStore getStateStore();
}
