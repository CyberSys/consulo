/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.language.editor.documentation;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.component.ProcessCanceledException;
import consulo.http.HttpRequests;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.util.io.CharsetToolkit;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.net.URL;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractExternalFilter {
  private static final Logger LOG = Logger.getInstance(AbstractExternalFilter.class);

  private static final Pattern ourClassDataStartPattern = Pattern.compile("START OF CLASS DATA", Pattern.CASE_INSENSITIVE);
  private static final Pattern ourClassDataEndPattern = Pattern.compile("SUMMARY ========", Pattern.CASE_INSENSITIVE);
  private static final Pattern ourNonClassDataEndPattern = Pattern.compile("<A NAME=", Pattern.CASE_INSENSITIVE);

  @NonNls
  protected static final Pattern ourAnchorSuffix = Pattern.compile("#(.*)$");
  protected static
  @NonNls
  final Pattern ourHtmlFileSuffix = Pattern.compile("/([^/]*[.][hH][tT][mM][lL]?)$");
  private static
  @NonNls
  final Pattern ourAnnihilator = Pattern.compile("/[^/^.]*/[.][.]/");
  private static
  @NonNls
  final String JAR_PROTOCOL = "jar:";
  @NonNls
  private static final String HR = "<HR>";
  @NonNls
  private static final String P = "<P>";
  @NonNls
  private static final String DL = "<DL>";
  @NonNls
  protected static final String H2 = "</H2>";
  @NonNls
  protected static final String HTML_CLOSE = "</HTML>";
  @NonNls
  protected static final String HTML = "<HTML>";
  @NonNls
  private static final String BR = "<BR>";
  @NonNls
  private static final String DT = "<DT>";
  private static final Pattern CHARSET_META_PATTERN =
    Pattern.compile("<meta[^>]+\\s*charset=\"?([\\w\\-]*)\\s*\">", Pattern.CASE_INSENSITIVE);
  private static final String FIELD_SUMMARY = "<!-- =========== FIELD SUMMARY =========== -->";
  private static final String CLASS_SUMMARY = "<div class=\"summary\">";

  protected static abstract class RefConvertor {
    @Nonnull
    private final Pattern mySelector;

    public RefConvertor(@Nonnull Pattern selector) {
      mySelector = selector;
    }

    protected abstract String convertReference(String root, String href);

    public CharSequence refFilter(final String root, @Nonnull CharSequence read) {
      CharSequence toMatch = StringUtil.toUpperCase(read);
      StringBuilder ready = new StringBuilder();
      int prev = 0;
      Matcher matcher = mySelector.matcher(toMatch);

      while (matcher.find()) {
        CharSequence before = read.subSequence(prev, matcher.start(1) - 1);     // Before reference
        final CharSequence href = read.subSequence(matcher.start(1), matcher.end(1)); // The URL
        prev = matcher.end(1) + 1;
        ready.append(before);
        ready.append("\"");
        ready.append(ApplicationManager.getApplication().runReadAction((Supplier<String>)() -> {
          return convertReference(root, href.toString());
        }
        ));
        ready.append("\"");
      }

      ready.append(read, prev, read.length());

      return ready;
    }
  }

  protected static String doAnnihilate(String path) {
    int len = path.length();

    do {
      path = ourAnnihilator.matcher(path).replaceAll("/");
    }
    while (len > (len = path.length()));

    return path;
  }

  public CharSequence correctRefs(String root, CharSequence read) {
    CharSequence result = read;
    for (RefConvertor myReferenceConvertor : getRefConverters()) {
      result = myReferenceConvertor.refFilter(root, result);
    }
    return result;
  }

  protected abstract RefConvertor[] getRefConverters();

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String getExternalDocInfo(final String url) throws Exception {
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode() && app.isDispatchThread() || app.isWriteAccessAllowed()) {
      LOG.error("May block indefinitely: shouldn't be called from EDT or under write lock");
      return null;
    }

    if (url == null || !MyJavadocFetcher.ourFree) {
      return null;
    }

    MyJavadocFetcher fetcher = new MyJavadocFetcher(url, new MyDocBuilder() {
      @Override
      public void buildFromStream(String url, Reader input, StringBuilder result) throws IOException {
        doBuildFromStream(url, input, result);
      }
    });
    try {
      app.executeOnPooledThread(fetcher).get();
    }
    catch (Exception e) {
      return null;
    }

    Exception exception = fetcher.myException;
    if (exception != null) {
      fetcher.myException = null;
      throw exception;
    }

    return correctDocText(url, fetcher.data);
  }

  @Nonnull
  protected String correctDocText(@Nonnull String url, @Nonnull CharSequence data) {
    CharSequence docText = correctRefs(ourAnchorSuffix.matcher(url).replaceAll(""), data);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Filtered JavaDoc: " + docText + "\n");
    }
    return PlatformDocumentationUtil.fixupText(docText);
  }

  @Nullable
  public String getExternalDocInfoForElement(final String docURL, final PsiElement element) throws Exception {
    return getExternalDocInfo(docURL);
  }

  protected void doBuildFromStream(String url, Reader input, StringBuilder data) throws IOException {
    doBuildFromStream(url, input, data, true, true);
  }

  protected void doBuildFromStream(final String url,
                                   Reader input,
                                   final StringBuilder data,
                                   boolean searchForEncoding,
                                   boolean matchStart) throws IOException {
    ParseSettings settings = getParseSettings(url);
    @NonNls Pattern startSection = settings.startPattern;
    @NonNls Pattern endSection = settings.endPattern;
    boolean useDt = settings.useDt;
    @NonNls String greatestEndSection = "<!-- ========= END OF CLASS DATA ========= -->";

    data.append(HTML);
    URL baseUrl = VirtualFileUtil.convertToURL(url);
    if (baseUrl != null) {
      data.append("<base href=\"").append(baseUrl).append("\">");
    }
    data.append("<style type=\"text/css\">" +
                  "  ul.inheritance {\n" +
                  "      margin:0;\n" +
                  "      padding:0;\n" +
                  "  }\n" +
                  "  ul.inheritance li {\n" +
                  "       display:inline;\n" +
                  "       list-style:none;\n" +
                  "  }\n" +
                  "  ul.inheritance li ul.inheritance {\n" +
                  "    margin-left:15px;\n" +
                  "    padding-left:15px;\n" +
                  "    padding-top:1px;\n" +
                  "  }\n" +
                  "</style>");

    String read;
    String contentEncoding = null;
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    BufferedReader buf = new BufferedReader(input);
    do {
      read = buf.readLine();
      if (read != null && searchForEncoding && read.contains("charset")) {
        String foundEncoding = parseContentEncoding(read);
        if (foundEncoding != null) {
          contentEncoding = foundEncoding;
        }
      }
    }
    while (read != null && matchStart && !startSection.matcher(StringUtil.toUpperCase(read)).find());

    if (input instanceof MyReader myReader && contentEncoding != null && !contentEncoding.equalsIgnoreCase(CharsetToolkit.UTF8) &&
      !contentEncoding.equals(myReader.getEncoding())) {
      //restart page parsing with correct encoding
      try {
        data.setLength(0);
        doBuildFromStream(url, new MyReader(myReader.myInputStream, contentEncoding), data, false, true);
      }
      catch (ProcessCanceledException e) {
        return;
      }
      return;
    }

    if (read == null) {
      data.setLength(0);
      if (matchStart && !settings.forcePatternSearch && input instanceof MyReader myReader) {
        try {
          final MyReader reader = contentEncoding != null ? new MyReader(myReader.myInputStream, contentEncoding)
            : new MyReader(myReader.myInputStream, myReader.getEncoding());
          doBuildFromStream(url, reader, data, false, false);
        }
        catch (ProcessCanceledException ignored) {
        }
      }
      return;
    }

    if (useDt) {
      boolean skip = false;

      do {
        if (StringUtil.toUpperCase(read).contains(H2) && !read.toUpperCase(Locale.ENGLISH).contains("H2")) { // read=class name in <H2>
          data.append(H2);
          skip = true;
        }
        else if (endSection.matcher(read).find() || StringUtil.indexOfIgnoreCase(read, greatestEndSection, 0) != -1) {
          data.append(HTML_CLOSE);
          return;
        }
        else if (!skip) {
          appendLine(data, read);
        }
      }
      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).trim().equals(DL) &&
        !StringUtil.containsIgnoreCase(read, "<div class=\"description\""));

      data.append(DL);

      StringBuilder classDetails = new StringBuilder();
      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).equals(HR) && !StringUtil.toUpperCase(read).equals(P)) {
        if (reachTheEnd(data, read, classDetails)) return;
        appendLine(classDetails, read);
      }

      while (((read = buf.readLine()) != null) && !StringUtil.toUpperCase(read).equals(P) && !StringUtil.toUpperCase(read).equals(HR)) {
        if (reachTheEnd(data, read, classDetails)) return;
        appendLine(data, read.replaceAll(DT, DT + BR));
      }

      data.append(classDetails);
      data.append(P);
    }
    else {
      appendLine(data, read);
    }

    while (((read = buf.readLine()) != null) &&
      !endSection.matcher(read).find() &&
      StringUtil.indexOfIgnoreCase(read, greatestEndSection, 0) == -1) {
      if (!StringUtil.toUpperCase(read).contains(HR)
        && !StringUtil.containsIgnoreCase(read, "<ul class=\"blockList\">")
        && !StringUtil.containsIgnoreCase(read, "<li class=\"blockList\">")) {
        appendLine(data, read);
      }
    }

    data.append(HTML_CLOSE);
  }

  @Nonnull
  protected ParseSettings getParseSettings(@Nonnull String url) {
    Pattern startSection = ourClassDataStartPattern;
    Pattern endSection = ourClassDataEndPattern;
    boolean anchorPresent = false;

    Matcher anchorMatcher = ourAnchorSuffix.matcher(url);
    if (anchorMatcher.find()) {
      anchorPresent = true;
      startSection = Pattern.compile(Pattern.quote("<a name=\"" + anchorMatcher.group(1) + "\""), Pattern.CASE_INSENSITIVE);
      endSection = ourNonClassDataEndPattern;
    }
    return new ParseSettings(startSection, endSection, !anchorPresent, anchorPresent);
  }

  private static boolean reachTheEnd(StringBuilder data, String read, StringBuilder classDetails) {
    if (StringUtil.indexOfIgnoreCase(read, FIELD_SUMMARY, 0) != -1 ||
      StringUtil.indexOfIgnoreCase(read, CLASS_SUMMARY, 0) != -1) {
      data.append(classDetails);
      data.append(HTML_CLOSE);
      return true;
    }
    return false;
  }

  @Nullable
  static String parseContentEncoding(@Nonnull String htmlLine) {
    if (!htmlLine.contains("charset")) {
      return null;
    }

    Matcher matcher = CHARSET_META_PATTERN.matcher(htmlLine);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static void appendLine(StringBuilder buffer, final String read) {
    buffer.append(read);
    buffer.append("\n");
  }

  private interface MyDocBuilder {
    void buildFromStream(String url, Reader input, StringBuilder result) throws IOException;
  }

  private static class MyJavadocFetcher implements Runnable {
    private static boolean ourFree = true;
    private final StringBuilder data = new StringBuilder();
    private final String url;
    private final MyDocBuilder myBuilder;
    private Exception myException;

    public MyJavadocFetcher(String url, MyDocBuilder builder) {
      this.url = url;
      myBuilder = builder;
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFree = false;
    }

    @Override
    public void run() {
      try {
        if (url == null) {
          return;
        }

        if (url.startsWith(JAR_PROTOCOL)) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(PlatformDocumentationUtil.getDocURL(url));
          if (file != null) {
            myBuilder.buildFromStream(url, new InputStreamReader(file.getInputStream()), data);
          }
        }
        else {
          URL parsedUrl = VirtualFileUtil.getURL(url);
          if (parsedUrl != null) {
            HttpRequests.request(parsedUrl.toString()).gzip(false).connect(new HttpRequests.RequestProcessor<Void>() {
              @Override
              public Void process(@Nonnull HttpRequests.Request request) throws IOException {
                byte[] bytes = request.readBytes(null);
                String contentEncoding = null;
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                  for (String htmlLine = reader.readLine(); htmlLine != null; htmlLine = reader.readLine()) {
                    contentEncoding = parseContentEncoding(htmlLine);
                    if (contentEncoding != null) {
                      break;
                    }
                  }
                }
                finally {
                  stream.reset();
                }

                if (contentEncoding == null) {
                  contentEncoding = request.getConnection().getContentEncoding();
                }

                //noinspection IOResourceOpenedButNotSafelyClosed
                myBuilder.buildFromStream(url,
                                          contentEncoding != null ? new MyReader(stream, contentEncoding) : new MyReader(stream),
                                          data);
                return null;
              }
            });
          }
        }
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (IOException e) {
        myException = e;
      }
      finally {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourFree = true;
      }
    }
  }

  private static class MyReader extends InputStreamReader {
    private ByteArrayInputStream myInputStream;

    public MyReader(ByteArrayInputStream in) {
      super(in);

      in.reset();
      myInputStream = in;
    }

    public MyReader(ByteArrayInputStream in, String charsetName) throws UnsupportedEncodingException {
      super(in, charsetName);

      in.reset();
      myInputStream = in;
    }
  }

  /**
   * Settings used for parsing of external documentation
   */
  protected static class ParseSettings {
    @Nonnull
    /**
     * Pattern defining the start of target fragment
     */
    private final Pattern startPattern;
    @Nonnull
    /**
     * Pattern defining the end of target fragment
     */
    private final Pattern endPattern;
    /**
     * If <code>false</code>, and line matching start pattern is not found, whole document will be processed
     */
    private final boolean forcePatternSearch;
    /**
     * Replace table data by &lt;dt&gt;
     */
    private final boolean useDt;

    public ParseSettings(@Nonnull Pattern startPattern, @Nonnull Pattern endPattern, boolean useDt, boolean forcePatternSearch) {
      this.startPattern = startPattern;
      this.endPattern = endPattern;
      this.useDt = useDt;
      this.forcePatternSearch = forcePatternSearch;
    }
  }
}
