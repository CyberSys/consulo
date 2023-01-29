/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.checkout;

import java.io.File;

/**
 * author: lesya
 */
public class SimpleCheckoutStrategy extends CheckoutStrategy {
  public SimpleCheckoutStrategy(File selectedLocation, File cvsPath, boolean isForFile) {
    super(selectedLocation, cvsPath, isForFile);
  }

  @Override
  public boolean useAlternativeCheckoutLocation() {
    return false;
  }

  @Override
  public File getResult() {
    return new File(getSelectedLocation(), getCvsPath().getPath());
  }

  @Override
  public File getCheckoutDirectory() {
    return getSelectedLocation();
  }
}
