// This is a generated file. Not intended for manual editing.
package com.github.zigzen.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.github.zigzen.lang.psi.ZigTypes.*;
import com.github.zigzen.lang.psi.*;

public class ZigPrimaryExprImpl extends ZigExprImpl implements ZigPrimaryExpr {

  public ZigPrimaryExprImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull ZigVisitor visitor) {
    visitor.visitPrimaryExpr(this);
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
  public ZigBlockLabel getBlockLabel() {
    return findChildByClass(ZigBlockLabel.class);
  }

  @Override
  @Nullable
  public ZigBreakLabel getBreakLabel() {
    return findChildByClass(ZigBreakLabel.class);
  }

  @Override
  @Nullable
  public ZigExpr getExpr() {
    return findChildByClass(ZigExpr.class);
  }

  @Override
  @Nullable
  public PsiElement getKeywordBreak() {
    return findChildByType(KEYWORD_BREAK);
  }

  @Override
  @Nullable
  public PsiElement getKeywordComptime() {
    return findChildByType(KEYWORD_COMPTIME);
  }

  @Override
  @Nullable
  public PsiElement getKeywordContinue() {
    return findChildByType(KEYWORD_CONTINUE);
  }

  @Override
  @Nullable
  public PsiElement getKeywordNosuspend() {
    return findChildByType(KEYWORD_NOSUSPEND);
  }

  @Override
  @Nullable
  public PsiElement getKeywordResume() {
    return findChildByType(KEYWORD_RESUME);
  }

  @Override
  @Nullable
  public PsiElement getKeywordReturn() {
    return findChildByType(KEYWORD_RETURN);
  }

}
