package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.psi.LanguageInjector;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import groovy.lang.Reference;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * For tests checking platform behavior not related to Java language (but they may still use Java for code samples)
 */
@NeedsIndex.SmartMode(reason = "AutoPopup shouldn't work in dumb mode")
public class GeneralAutoPopupTest extends JavaCompletionAutoPopupTestCase {
  public void testNoAutopopupInTheMiddleOfWordWhenTheOnlyVariantIsAlreadyInTheEditor() {
    myFixture.configureByText("a.java", "class Foo { private boolean ignoredProperty; public boolean isIgnoredP<caret>operty() {}}");
    type("r");
    assertNull(getLookup());
  }

  public void testNoLookupAfterTypingALetterAndThenQuicklyOvertypingAQuote() {
    myFixture.configureByText("a.html", "<a href=\"<caret>\">");
    myFixture.type("a");
    type("\"");
    assertNull(getLookup());
  }

  public void testNoLookupAfterTypingAndQuicklyMovingCaretToAnotherPlace() throws Throwable {
    myFixture.configureByText("a.java", "class Foo { <caret> }");
    UsefulTestCase.edt((ThrowableRunnable<Throwable>)() -> {
      myFixture.type("F");
      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() + 1);
    });

    myTester.joinAutopopup();
    myTester.joinCompletion();

    assertNull(getLookup());
  }

  public void testInjectorsAreNotRunInEdt() {
    final Reference<Boolean> injectorCalled = new Reference<>(false);
    LanguageInjector injector = (host, injectionPlacesRegistrar) -> {
      injectorCalled.set(true);
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    };
    ExtensionTestUtil.maskExtensions(LanguageInjector.EXTENSION_POINT_NAME,
                                     Arrays.asList(injector),
                                     myFixture.getTestRootDisposable());

    myFixture.configureByText("a.java", "class Foo { String s = <caret>; }");
    assertFalse(injectorCalled.get());
    type("\"");

    injectorCalled.set(false);
    type("abc");

    assertTrue(injectorCalled.get());
    assertNull(getLookup());
  }

  public void testDonTCloseLookupWhenStartingANewLineAfterDot() throws Throwable {
    myFixture.configureByText("a.java", "class Foo {{ \"abc\"<caret> }}");
    type(".");
    assertNotNull(getLookup());
    UsefulTestCase.edt((ThrowableRunnable<Throwable>)() -> {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE);
      assertNotNull(getLookup());
      assertEquals(getLookup().getLookupStart(), myFixture.getEditor().getCaretModel().getOffset());
      assertTrue(myFixture.getEditor().getDocument().getText().contains("\n"));
    });
  }

  public void testCloseLookupWhenStartingANewLineAfterHavingTypedAnIdentifierManually() throws Throwable {
    myFixture.configureByText("a.java", "class Foo { int a, ab; { new Foo()<caret> }}");
    type(".a");
    assertNotNull(getLookup());
    UsefulTestCase.edt((ThrowableRunnable<Throwable>)() -> {
      myFixture.performEditorAction(IdeActions.ACTION_EDITOR_START_NEW_LINE);
      assertNull(getLookup());
      assertTrue(myFixture.getEditor().getDocument().getText().contains("\n"));
    });
  }

  public void testDoNotRestartAutopopupCompletionWhenAppendingPrefixWhichAlreadyYieldedNoResultsToSaveCpu() {
    JavaAutoPopupTest.registerCompletionContributor(CountingContributor.class, myFixture.getTestRootDisposable(), LoadingOrder.FIRST);
    CountingContributor.getOurCount().set(0);

    myFixture.configureByText("a.txt", "");
    type("a");
    assertNull(getLookup());
    assertEquals(1, CountingContributor.getOurCount().get());

    type("bcd");
    assertNull(getLookup());
    assertEquals(1, CountingContributor.getOurCount().get());

    type(" a");
    assertEquals(2, CountingContributor.getOurCount().get());
  }

  public void testSkipAutopopupIfConfidenceNeedsNonReadyIndex() {
    myFixture.configureByText("a.java", "class C { int abc; { getClass().getDeclaredField(\"<caret>x\"); }}");
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      type("a");
      assertNull(getLookup());
    });
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "abc");
  }

  public static class CountingContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
      ourCount.incrementAndGet();
    }

    public static AtomicInteger getOurCount() {
      return ourCount;
    }

    private static final AtomicInteger ourCount = new AtomicInteger();
  }
}
