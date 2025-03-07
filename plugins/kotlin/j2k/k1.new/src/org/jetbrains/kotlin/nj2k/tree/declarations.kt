// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.nj2k.types.JKNoType

internal abstract class JKDeclaration : JKTreeElement(), PsiOwner by PsiOwnerImpl() {
    abstract val name: JKNameIdentifier
}

/**
 * @param hasTrailingComma - Makes sense only in an enum class,
 * which can have a trailing comma after the last enum entry both in Java and in Kotlin
 */
internal open class JKClass(
    name: JKNameIdentifier,
    inheritance: JKInheritanceInfo,
    var classKind: ClassKind,
    typeParameterList: JKTypeParameterList,
    classBody: JKClassBody,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement,
    val hasTrailingComma: Boolean = false
) : JKDeclaration(), JKVisibilityOwner, JKOtherModifiersOwner, JKModalityOwner, JKTypeParameterListOwner, JKAnnotationListOwner {
    override fun accept(visitor: JKVisitor) = visitor.visitClass(this)

    override var name by child(name)
    val inheritance by child(inheritance)
    override var typeParameterList: JKTypeParameterList by child(typeParameterList)
    var classBody: JKClassBody by child(classBody)
    override var annotationList: JKAnnotationList by child(annotationList)

    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)

    enum class ClassKind(val text: String) {
        ANNOTATION("annotation class"),
        CLASS("class"),
        ENUM("enum class"),
        INTERFACE("interface"),
        OBJECT("object"),
        COMPANION("companion object"),
        RECORD("data class")
    }
}

internal class JKRecordClass(
    name: JKNameIdentifier,
    recordComponents: List<JKJavaRecordComponent>,
    inheritance: JKInheritanceInfo,
    typeParameterList: JKTypeParameterList,
    classBody: JKClassBody,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
) : JKClass(
    name,
    inheritance,
    ClassKind.RECORD,
    typeParameterList,
    classBody,
    annotationList,
    otherModifierElements,
    visibilityElement,
    JKModalityModifierElement(FINAL) // A record class is implicitly final
) {
    var recordComponents: List<JKJavaRecordComponent> by children(recordComponents)
}

internal abstract class JKVariable : JKDeclaration(), JKAnnotationListOwner {
    abstract var type: JKTypeElement
    abstract var initializer: JKExpression
}

internal class JKLocalVariable(
    type: JKTypeElement,
    name: JKNameIdentifier,
    initializer: JKExpression,
    mutabilityElement: JKMutabilityModifierElement,
    annotationList: JKAnnotationList = JKAnnotationList()
) : JKVariable(), JKMutabilityOwner {
    override var initializer by child(initializer)
    override var name by child(name)
    override var type by child(type)
    override var annotationList by child(annotationList)
    override var mutabilityElement by child(mutabilityElement)

    override fun accept(visitor: JKVisitor) = visitor.visitLocalVariable(this)
}

internal class JKForLoopVariable(
    type: JKTypeElement,
    name: JKNameIdentifier,
    initializer: JKExpression,
    annotationList: JKAnnotationList = JKAnnotationList()
) : JKVariable() {
    override var initializer by child(initializer)
    override var name by child(name)
    override var type by child(type)
    override var annotationList by child(annotationList)

    override fun accept(visitor: JKVisitor) = visitor.visitForLoopVariable(this)
}

internal open class JKParameter(
    type: JKTypeElement,
    name: JKNameIdentifier,
    var isVarArgs: Boolean = false,
    initializer: JKExpression = JKStubExpression(),
    annotationList: JKAnnotationList = JKAnnotationList()
) : JKVariable(), JKModifiersListOwner {
    override var initializer by child(initializer)
    override var name by child(name)
    override var type by child(type)
    override var annotationList by child(annotationList)
    override fun accept(visitor: JKVisitor) = visitor.visitParameter(this)
}

internal class JKJavaRecordComponent(
    type: JKTypeElement,
    name: JKNameIdentifier,
    isVarArgs: Boolean,
    annotationList: JKAnnotationList
) : JKParameter(type, name, isVarArgs, annotationList = annotationList)

internal class JKEnumConstant(
    name: JKNameIdentifier,
    arguments: JKArgumentList,
    body: JKClassBody,
    type: JKTypeElement,
    annotationList: JKAnnotationList = JKAnnotationList()
) : JKVariable() {
    override var name: JKNameIdentifier by child(name)
    val arguments: JKArgumentList by child(arguments)
    val body: JKClassBody by child(body)
    override var type: JKTypeElement by child(type)
    override var initializer: JKExpression by child(JKStubExpression())
    override var annotationList by child(annotationList)

    override fun accept(visitor: JKVisitor) = visitor.visitEnumConstant(this)
}

internal class JKTypeParameter(
    name: JKNameIdentifier,
    upperBounds: List<JKTypeElement>,
    annotationList: JKAnnotationList = JKAnnotationList()
) : JKDeclaration(), JKAnnotationListOwner {
    override var name: JKNameIdentifier by child(name)
    var upperBounds: List<JKTypeElement> by children(upperBounds)
    override var annotationList by child(annotationList)

    override fun accept(visitor: JKVisitor) = visitor.visitTypeParameter(this)
}

internal abstract class JKMethod :
    JKDeclaration(), JKVisibilityOwner, JKModalityOwner, JKOtherModifiersOwner, JKTypeParameterListOwner, JKAnnotationListOwner {

    abstract var parameters: List<JKParameter>
    abstract var returnType: JKTypeElement
    abstract var block: JKBlock

    val leftParen = JKTokenElementImpl("(")
    val rightParen = JKTokenElementImpl(")")
}

internal class JKMethodImpl(
    returnType: JKTypeElement,
    name: JKNameIdentifier,
    parameters: List<JKParameter>,
    block: JKBlock,
    typeParameterList: JKTypeParameterList,
    annotationList: JKAnnotationList,
    throwsList: List<JKTypeElement>,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement
) : JKMethod() {
    override fun accept(visitor: JKVisitor) = visitor.visitMethod(this)

    override var returnType: JKTypeElement by child(returnType)
    override var name: JKNameIdentifier by child(name)
    override var parameters: List<JKParameter> by children(parameters)
    override var block: JKBlock by child(block)
    override var typeParameterList: JKTypeParameterList by child(typeParameterList)
    override var annotationList: JKAnnotationList by child(annotationList)
    var throwsList: List<JKTypeElement> by children(throwsList)

    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)
}

internal abstract class JKConstructor : JKMethod() {
    abstract var delegationCall: JKExpression
}

internal class JKConstructorImpl(
    name: JKNameIdentifier,
    parameters: List<JKParameter>,
    block: JKBlock,
    delegationCall: JKExpression,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement
) : JKConstructor() {
    override var returnType: JKTypeElement by child(JKTypeElement(JKNoType))
    override var name: JKNameIdentifier by child(name)
    override var parameters: List<JKParameter> by children(parameters)
    override var block: JKBlock by child(block)
    override var delegationCall: JKExpression by child(delegationCall)
    override var typeParameterList: JKTypeParameterList by child(JKTypeParameterList())
    override var annotationList: JKAnnotationList by child(annotationList)
    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)

    override fun accept(visitor: JKVisitor) = visitor.visitConstructor(this)
}

internal class JKKtPrimaryConstructor(
    name: JKNameIdentifier,
    parameters: List<JKParameter>,
    delegationCall: JKExpression,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement
) : JKConstructor() {
    override var returnType: JKTypeElement by child(JKTypeElement(JKNoType))
    override var name: JKNameIdentifier by child(name)
    override var parameters: List<JKParameter> by children(parameters)
    override var block: JKBlock by child(JKBodyStub)
    override var delegationCall: JKExpression by child(delegationCall)
    override var typeParameterList: JKTypeParameterList by child(JKTypeParameterList())
    override var annotationList: JKAnnotationList by child(annotationList)
    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)

    override fun accept(visitor: JKVisitor) = visitor.visitKtPrimaryConstructor(this)
}

internal class JKField(
    type: JKTypeElement,
    name: JKNameIdentifier,
    initializer: JKExpression,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement,
    mutabilityElement: JKMutabilityModifierElement
) : JKVariable(), JKVisibilityOwner, JKMutabilityOwner, JKModalityOwner, JKOtherModifiersOwner, JKAnnotationListOwner {
    override var annotationList: JKAnnotationList by child(annotationList)
    override var initializer: JKExpression by child(initializer)
    override var type by child(type)
    override var name: JKNameIdentifier by child(name)
    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)
    override var mutabilityElement by child(mutabilityElement)

    override fun accept(visitor: JKVisitor) = visitor.visitField(this)
}

internal sealed class JKInitDeclaration(block: JKBlock) : JKDeclaration() {
    var block: JKBlock by child(block)
    abstract val isStatic: Boolean
    override val name: JKNameIdentifier by child(JKNameIdentifier("<init>"))
}

internal class JKKtInitDeclaration(block: JKBlock) : JKInitDeclaration(block) {
    override val isStatic: Boolean get() = false
    override fun accept(visitor: JKVisitor) = visitor.visitKtInitDeclaration(this)
}

internal class JKJavaStaticInitDeclaration(block: JKBlock) : JKInitDeclaration(block) {
    override val isStatic: Boolean get() = true
    override fun accept(visitor: JKVisitor) = visitor.visitJavaStaticInitDeclaration(this)
}
