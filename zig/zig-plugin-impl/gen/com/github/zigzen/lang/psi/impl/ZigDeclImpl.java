// This is a generated file. Not intended for manual editing.
package com.github.zigzen.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.github.zigzen.lang.psi.ZigTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.github.zigzen.lang.psi.*;

public class ZigDeclImpl extends ASTWrapperPsiElement implements ZigDecl {

  public ZigDeclImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitDecl(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ZigVisitor) accept((ZigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public ZigBlock getBlock() {
    return findChildByClass(ZigBlock.class);
  }

  @Override
  @Nullable
  public ZigExpr getExpr() {
    return findChildByClass(ZigExpr.class);
  }

  @Override
  @Nullable
  public ZigFnProto getFnProto() {
    return findChildByClass(ZigFnProto.class);
  }

  @Override
  @Nullable
  public ZigVarDecl getVarDecl() {
    return findChildByClass(ZigVarDecl.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordExport() {
    return findChildByType(KEYWORD_EXPORT);
  }

  @Override
  @Nullable
  public PsiElement getKeywordExtern() {
    return findChildByType(KEYWORD_EXTERN);
  }

  @Override
  @Nullable
  public PsiElement getKeywordInline() {
    return findChildByType(KEYWORD_INLINE);
  }

  @Override
  @Nullable
  public PsiElement getKeywordNoiline() {
    return findChildByType(KEYWORD_NOILINE);
  }

  @Override
  @Nullable
  public PsiElement getKeywordThreadlocal() {
    return findChildByType(KEYWORD_THREADLOCAL);
  }

  @Override
  @Nullable
  public PsiElement getKeywordUsingnamespace() {
    return findChildByType(KEYWORD_USINGNAMESPACE);
  }

  @Override
  @Nullable
  public PsiElement getSemicolon() {
    return findChildByType(SEMICOLON);
  }

  @Override
  @Nullable
  public PsiElement getStringLiteralSingle() {
    return findChildByType(STRING_LITERAL_SINGLE);
  }

}
