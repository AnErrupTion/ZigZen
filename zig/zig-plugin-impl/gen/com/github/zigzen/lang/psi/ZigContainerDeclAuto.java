// This is a generated file. Not intended for manual editing.
package com.github.zigzen.lang.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface ZigContainerDeclAuto extends PsiElement {

  @NotNull
  ZigContainerDeclType getContainerDeclType();

  @NotNull
  List<ZigContainerDeclaration> getContainerDeclarationList();

  @NotNull
  List<ZigContainerField> getContainerFieldList();

  @Nullable
  PsiElement getContainerDocComment();

  @NotNull
  PsiElement getLbrace();

  @NotNull
  PsiElement getRbrace();

}
