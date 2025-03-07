// Copyright 2024 ZigIDE and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.github.zigzen.lang.parser

import com.github.zigzen.lang.ZigLanguage
import com.github.zigzen.lang.lexer.ZigLexerAdapter
import com.github.zigzen.extapi.psi.ZigPsiFile
import com.github.zigzen.lang.psi.ZigTokenSets
import com.github.zigzen.lang.psi.ZigTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType

class ZigParserDefinition : ParserDefinition {
  @Suppress("CompanionObjectInExtension")
  companion object {
      val FILE = IFileElementType(ZigLanguage.INSTANCE)
  }

  override fun createLexer(project: Project?) = ZigLexerAdapter()
  override fun createParser(project: Project?) = ZigParser()
  override fun getFileNodeType() = FILE
  override fun getCommentTokens() = ZigTokenSets.COMMENTS
  override fun getStringLiteralElements() = ZigTokenSets.STRINGS
  override fun createElement(node: ASTNode?): PsiElement = ZigTypes.Factory.createElement(node)
  override fun createFile(viewProvider: FileViewProvider) = ZigPsiFile(viewProvider)
}