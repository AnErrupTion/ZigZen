// This is a generated file. Not intended for manual editing.
package com.github.zigzen.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ZigIfStatement extends PsiElement {

  @NotNull
  ZigExpr getExpr();

  @NotNull
  ZigIfPrefix getIfPrefix();

  @Nullable
  ZigPayload getPayload();

  @Nullable
  ZigStatement getStatement();

  @Nullable
  PsiElement getKeywordElse();

  @Nullable
  PsiElement getSemicolon();

}
