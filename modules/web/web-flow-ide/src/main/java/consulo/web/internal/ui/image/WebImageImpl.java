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
package consulo.web.internal.ui.image;

import ar.com.hjg.pngj.PngReader;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.SVGLoader;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author VISTALL
 * @since 13-Jun-16
 */
public class WebImageImpl implements Image, WebImageWithURL {
  private int myHeight, myWidth;

  private URL myImageUrl;

  public WebImageImpl(@Nonnull URL url) {
    myImageUrl = url;

    URL scaledImageUrl = url;
    String urlText = url.toString();
    if (urlText.endsWith(".png")) {
      urlText = urlText.replace(".png", "@2x.png");
      try {
        scaledImageUrl = new URL(urlText);
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    try (InputStream ignored = scaledImageUrl.openStream()) {
      // if scaled image resolved - map it for better quality
      myImageUrl = scaledImageUrl;
    }
    catch (Throwable ignored) {
    }

    try {
      if (urlText.endsWith(".svg")) {
        SVGDocument diagram = new SVGLoader().load(url);
        FloatSize viewRect = diagram.size();
        myWidth = (int)viewRect.getWidth();
        myHeight = (int)viewRect.getHeight();
      }
      else {
        PngReader reader = null;
        try (InputStream stream = url.openStream()) {
          reader = new PngReader(stream);
          myWidth = reader.imgInfo.cols;
          myHeight = reader.imgInfo.rows;
        }
        finally {
          if (reader != null) {
            reader.close();
          }
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getHeight() {
    return myHeight;
  }

  @Override
  public int getWidth() {
    return myWidth;
  }

  @Nonnull
  @Override
  public String getImageURL() {
    return myImageUrl.toExternalForm();
  }
}
