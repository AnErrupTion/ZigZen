// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.*
import org.jetbrains.plugins.terminal.exp.*
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class TerminalTextHighlighterTest {

  private val projectRule: ProjectRule = ProjectRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val ruleChain = RuleChain(EdtRule(), projectRule, disposableRule)

  @Test
  fun `editor highlighter finds proper initial range`() {
    val outputManager = TestTerminalOutputManager(projectRule.project, disposableRule.disposable)
    outputManager.createBlock("echo foo", TestCommandOutput("foo bar baz",
                                                            listOf(HighlightingInfo(1, 2, green()),
                                                                   HighlightingInfo(5, 6, red()))))
    checkHighlighter(outputManager, listOf(TextRange(0, 1),
                                           TextRange(1, 2),
                                           TextRange(2, 5),
                                           TextRange(5, 6),
                                           TextRange(6, outputManager.document.textLength)))
  }

  private fun checkHighlighter(outputManager: TestTerminalOutputManager, ranges: List<TextRange>) {
    for (documentOffset in 0 until outputManager.document.textLength) {
      val iterator = outputManager.terminalOutputHighlighter.createIterator(documentOffset)
      Assert.assertTrue(!iterator.atEnd())
      val expectedTextRange = ranges.find { it.startOffset <= documentOffset && documentOffset < it.endOffset }
      Assert.assertNotNull(expectedTextRange)
      Assert.assertEquals(expectedTextRange, TextRange(iterator.start, iterator.end))
    }
  }

  companion object {
    fun green(): TextAttributes = ConsoleHighlighter.GREEN.defaultAttributes
    fun red(): TextAttributes = ConsoleHighlighter.RED.defaultAttributes
  }
}