// This is a generated file. Not intended for manual editing.
package com.github.zigzen.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ZigWhilePrefix extends PsiElement {

  @NotNull
  List<ZigExpr> getExprList();

  @Nullable
  ZigPtrPayload getPtrPayload();

  @NotNull
  PsiElement getKeywordWhile();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

}
