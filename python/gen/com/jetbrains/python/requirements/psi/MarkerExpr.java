// This is a generated file. Not intended for manual editing.
package com.jetbrains.python.requirements.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface MarkerExpr extends PsiElement {

  @Nullable
  MarkerOp getMarkerOp();

  @Nullable
  MarkerOr getMarkerOr();

  @NotNull
  List<MarkerVar> getMarkerVarList();

}
