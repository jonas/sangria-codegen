/*
 * Copyright 2017 Mediative
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mediative.sangria.codegen

import scala.collection.immutable.Seq

/**
 * AST representing the extracted GraphQL types.
 */
sealed trait Tree
object Tree {
  case class Ref(name: String)                      extends Tree
  case class Field[T <: Tree](name: String, tpe: T) extends Tree
  case class Selection(fields: Seq[Field[Tree]], interfaces: Seq[String] = Vector.empty)
      extends Tree {
    def +(that: Selection) =
      Selection((this.fields ++ that.fields).distinct, this.interfaces ++ that.interfaces)
  }
  object Selection {
    final val empty = Selection(Vector.empty)
  }

  /**
   * Operations represent API calls and are the entry points to the API.
   */
  case class Operation(name: Option[String], variables: Seq[Field[Ref]], selection: Selection)
      extends Tree

  /**
   * Marker trait for GraphQL input and output types.
   */
  sealed trait Type extends Tree {
    def name: String
  }
  case class Object(name: String, fields: Seq[Field[Ref]])    extends Type
  case class Interface(name: String, fields: Seq[Field[Ref]]) extends Type
  case class Enum(name: String, vaules: Seq[String])          extends Type

  /**
   * The API based on one or more GraphQL query documents using a given schema.
   *
   * It includes only the operations, interfaces and input/output types
   * referenced in the query documents.
   */
  case class Api(operations: Seq[Operation], interfaces: Seq[Interface], types: Seq[Type])
}
