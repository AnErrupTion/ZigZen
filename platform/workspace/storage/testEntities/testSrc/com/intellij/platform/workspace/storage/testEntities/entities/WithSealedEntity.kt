// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

interface WithSealedEntity : WorkspaceEntity {
  val classes: List<MySealedClass>
  val interfaces: List<MySealedInterface>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : WithSealedEntity, WorkspaceEntity.Builder<WithSealedEntity> {
    override var entitySource: EntitySource
    override var classes: MutableList<MySealedClass>
    override var interfaces: MutableList<MySealedInterface>
  }

  companion object : EntityType<WithSealedEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(classes: List<MySealedClass>,
                        interfaces: List<MySealedInterface>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): WithSealedEntity {
      val builder = builder()
      builder.classes = classes.toMutableWorkspaceList()
      builder.interfaces = interfaces.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: WithSealedEntity,
                                      modification: WithSealedEntity.Builder.() -> Unit): WithSealedEntity = modifyEntity(
  WithSealedEntity.Builder::class.java, entity, modification)
//endregion

sealed class MySealedClass

data class MySealedClassOne(val info: String) : MySealedClass()
data class MySealedClassTwo(val info: String) : MySealedClass()

sealed interface MySealedInterface

data class MySealedInterfaceOne(val info: String) : MySealedInterface
data class MySealedInterfaceTwo(val info: String) : MySealedInterface
