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
import scala.meta._
import cats.implicits._

/**
 * Generate code using Scalameta.
 */
case class ScalametaGenerator(moduleName: Term.Name, stats: Seq[Stat] = Vector.empty)
    extends Generator[Defn.Object] {

  override def generate(api: Tree.Api): Result[Defn.Object] = {
    val operations = api.operations.flatMap(generateOperation)
    val fragments  = api.interfaces.map(generateInterface)
    for {
      types <- api.types.map(generateOutputType).toList.sequenceU
    } yield {
      q"""
        object $moduleName {
          ..$operations
          ..$fragments
          ..${types.flatten}
          ..$stats
        }
      """
    }
  }

  def termParam(paramName: String, typeName: String) =
    Term.Param(Vector.empty, Term.Name(paramName), Some(Type.Name(typeName)), None)

  def generateOperation(operation: Tree.Operation): Seq[Stat] = {
    def generateSelectionParams(prefix: String)(selection: Tree.Selection): Seq[Term.Param] =
      selection.fields.map {
        case Tree.Field(name, Tree.Ref(tpe)) =>
          termParam(name, tpe)

        case Tree.Field(name, _) =>
          termParam(name, prefix + name.capitalize)
      }

    def generateSelectionStats(prefix: String)(selection: Tree.Selection): Seq[Stat] =
      selection.fields.flatMap {
        case Tree.Field(name, Tree.Ref(tpe)) =>
          Vector.empty

        case Tree.Field(name, selection: Tree.Selection) =>
          val stats  = generateSelectionStats(prefix + name.capitalize + ".")(selection)
          val params = generateSelectionParams(prefix + name.capitalize + ".")(selection)

          val tpeName   = Type.Name(name.capitalize)
          val termName  = Term.Name(name.capitalize)
          val ctorNames = selection.interfaces.map(Ctor.Name.apply)
          val emptySelf = Term.Param(Vector.empty, Name.Anonymous(), None, None)
          val template  = Template(Nil, ctorNames, emptySelf, None)

          Vector(q"case class $tpeName(..${params}) extends $template") ++
            Option(stats)
              .filter(_.nonEmpty)
              .map { stats =>
                q"object $termName { ..$stats }"
              }
              .toVector
      }

    val variables = operation.variables.map { varDef =>
      termParam(varDef.name, varDef.tpe.name)
    }

    val name   = operation.name.getOrElse(sys.error("found unnamed operation"))
    val stats  = generateSelectionStats(name + ".")(operation.selection)
    val params = generateSelectionParams(name + ".")(operation.selection)

    val tpeName          = Type.Name(name)
    val termName         = Term.Name(name)
    val variableTypeName = Type.Name(name + "Variables")

    Vector[Stat](
      q"case class $tpeName(..${params})",
      q"""
        object $termName {
          case class $variableTypeName(..$variables)
          ..${stats}
        }
      """
    )
  }

  def generateInterface(interface: Tree.Interface): Stat = {
    val defs = interface.fields.map { field =>
      val fieldName = Term.Name(field.name)
      val tpeName   = Type.Name(field.tpe.name)
      q"def $fieldName: $tpeName"
    }
    val traitName = Type.Name(interface.name)
    q"trait $traitName { ..$defs }"
  }

  def generateOutputType(tree: Tree.OutputType): Result[Seq[Stat]] = tree match {
    case interface: Tree.Interface =>
      Right(Vector(generateInterface(interface)))

    case Tree.Object(name, fields) =>
      val params    = fields.map(field => termParam(field.name, field.tpe.name))
      val className = Type.Name(name)
      // FIXME: val interfaces = obj.interfaces.
      Right(Vector(q"case class $className(..$params)": Stat))

    case Tree.Enum(name, values) =>
      val enumValues = values.map { value =>
        val ctorName  = Ctor.Ref.Name(name)
        val parent    = ctor"$ctorName"
        val template  = template"$parent"
        val valueName = Term.Name(value)
        q"case object $valueName extends $template"
      }

      val enumName   = Type.Name(name)
      val objectName = Term.Name(name)
      Right(
        Vector[Stat](
          q"sealed trait $enumName",
          q"object $objectName { ..$enumValues }"
        ))

    case unknown =>
      Left(Failure("Unknown output type " + unknown))
  }

}

object ScalametaGenerator {
  def apply(moduleName: String): ScalametaGenerator =
    ScalametaGenerator(Term.Name(moduleName))
}
