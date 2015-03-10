/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.metadata.typesystem.json

import com.google.common.collect.ImmutableList
import org.apache.hadoop.metadata.typesystem.TypesDef
import org.apache.hadoop.metadata.typesystem.types.DataTypes.{ArrayType, MapType, TypeCategory}
import org.apache.hadoop.metadata.typesystem.types._
import org.apache.hadoop.metadata.MetadataException
import org.json4s.JsonAST.JString
import org.json4s._
import org.json4s.native.Serialization._


/**
 * Module for serializing to/from Json.
 *
 * @example {{{
 *            val j = TypesSerialization.toJson(typeSystem, "Employee", "Person", "Department", "SecurityClearance")
 *
 *            val typesDef = TypesSerialization.fromJson(jsonStr)
 *            typesDef.enumTypes.foreach( typeSystem.defineEnumType(_))

  typeSystem.defineTypes(ImmutableList.copyOf(typesDef.structTypes.toArray),
    ImmutableList.copyOf(typesDef.traitTypes.toArray),
    ImmutableList.copyOf(typesDef.classTypes.toArray)
  )
 * }}}
 *
 * @todo doesn't traverse includes directives. Includes are parsed into
 *       [[org.apache.hadoop.metadata.tools.thrift.IncludeDef IncludeDef]] structures
 *       but are not traversed.
 * @todo mixing in [[scala.util.parsing.combinator.PackratParsers PackratParsers]] is a placeholder. Need to
 *       change specific grammar rules to `lazy val` and `Parser[Elem]` to `PackratParser[Elem]`. Will do based on
 *       performance analysis.
 * @todo Error reporting
 */
object TypesSerialization {

    def toJsonValue(typ: IDataType[_])(implicit formats: Formats): JValue = {
        typ.getTypeCategory match {
            case TypeCategory.CLASS => {
                Extraction.decompose(convertClassTypeToHierarchicalTypeDefintion(typ.asInstanceOf[ClassType]))
            }
            case TypeCategory.STRUCT => {
                Extraction.decompose(convertStructTypeToStructDef(typ.asInstanceOf[StructType]))
            }
            case TypeCategory.TRAIT => {
                Extraction.decompose(convertTraitTypeToHierarchicalTypeDefintion(typ.asInstanceOf[TraitType]))
            }
            case TypeCategory.ENUM => {
                Extraction.decompose(convertEnumTypeToEnumTypeDef(typ.asInstanceOf[EnumType]))
            }
            case _ => JString(s"${typ.getName}")
        }
    }

    def toJson(ts: TypeSystem, typName: String): String = {
        toJson(ts, List(typName): _*)
    }

    def toJson(ts: TypeSystem, typNames: String*): String = {
        toJson(ts, (typ: IDataType[_]) => typNames.contains(typ.getName))
    }

    import scala.collection.JavaConversions._

    def toJson(ts: TypeSystem, typNames: java.util.List[String]): String = {
        toJson(ts, typNames.toIndexedSeq: _*)
    }

    def toJson(ts: TypeSystem, export: IDataType[_] => Boolean): String = {
        implicit val formats = org.json4s.native.Serialization.formats(NoTypeHints) + new MultiplicitySerializer

        val typsDef = convertToTypesDef(ts, export)

        writePretty(typsDef)
    }

    def fromJson(jsonStr: String): TypesDef = {
        implicit val formats = org.json4s.native.Serialization.formats(NoTypeHints) + new MultiplicitySerializer

        read[TypesDef](jsonStr)
    }

    def toJson(typesDef : TypesDef) : String = {
      implicit val formats = org.json4s.native.Serialization.formats(NoTypeHints) + new MultiplicitySerializer
      writePretty(typesDef)

    }

    def toJson(enumTypeDefinition: EnumTypeDefinition) : String = {
      toJson(new TypesDef(enumTypeDefinition))
    }

    def toJson(structTypeDefinition: StructTypeDefinition) : String = {
      toJson(new TypesDef(structTypeDefinition))
    }

    def toJson(typDef: HierarchicalTypeDefinition[_], isTrait : Boolean) : String = {
      toJson(new TypesDef(typDef, isTrait))
    }

  private def convertAttributeInfoToAttributeDef(aInfo: AttributeInfo) = {
        new AttributeDefinition(aInfo.name, aInfo.dataType().getName, aInfo.multiplicity,
            aInfo.isComposite, aInfo.reverseAttributeName)
    }

    private def convertEnumTypeToEnumTypeDef(et: EnumType) = {
        val eVals: Seq[EnumValue] = et.valueMap.values().toSeq
        new EnumTypeDefinition(et.name, eVals: _*)
    }

    private def convertStructTypeToStructDef(st: StructType): StructTypeDefinition = {

        val aDefs: Iterable[AttributeDefinition] =
            st.fieldMapping.fields.values().map(convertAttributeInfoToAttributeDef(_))
        new StructTypeDefinition(st.name, aDefs.toArray)
    }

    private def convertTraitTypeToHierarchicalTypeDefintion(tt: TraitType): HierarchicalTypeDefinition[TraitType] = {

        val aDefs: Iterable[AttributeDefinition] =
            tt.immediateAttrs.map(convertAttributeInfoToAttributeDef(_))
        new HierarchicalTypeDefinition[TraitType](classOf[TraitType], tt.name, tt.superTypes, aDefs.toArray)
    }

    private def convertClassTypeToHierarchicalTypeDefintion(tt: ClassType): HierarchicalTypeDefinition[ClassType] = {

        val aDefs: Iterable[AttributeDefinition] =
            tt.immediateAttrs.map(convertAttributeInfoToAttributeDef(_))
        new HierarchicalTypeDefinition[ClassType](classOf[ClassType], tt.name, tt.superTypes, aDefs.toArray)
    }

    def convertToTypesDef(ts: TypeSystem, export: IDataType[_] => Boolean): TypesDef = {

        var enumTypes: Seq[EnumTypeDefinition] = Nil
        var structTypes: Seq[StructTypeDefinition] = Nil
        var traitTypes: Seq[HierarchicalTypeDefinition[TraitType]] = Nil
        var classTypes: Seq[HierarchicalTypeDefinition[ClassType]] = Nil

        def toTyp(nm: String) = ts.getDataType(classOf[IDataType[_]], nm)

        val typs: Iterable[IDataType[_]] = ts.getTypeNames.map(toTyp(_)).filter { (typ: IDataType[_]) =>
            !(typ.getTypeCategory eq TypeCategory.PRIMITIVE) && export(typ)
        }

        typs.foreach {
            case typ: ArrayType => ()
            case typ: MapType => ()
            case typ: EnumType => enumTypes = enumTypes :+ convertEnumTypeToEnumTypeDef(typ)
            case typ: StructType => structTypes = structTypes :+ convertStructTypeToStructDef(typ)
            case typ: TraitType => traitTypes = traitTypes :+ convertTraitTypeToHierarchicalTypeDefintion(typ)
            case typ: ClassType => classTypes = classTypes :+ convertClassTypeToHierarchicalTypeDefintion(typ)
        }

        TypesDef(enumTypes, structTypes, traitTypes, classTypes)
    }

}

class MultiplicitySerializer extends CustomSerializer[Multiplicity](format => ( {
    case JString(m) => m match {
        case "optional" => Multiplicity.OPTIONAL
        case "required" => Multiplicity.REQUIRED
        case "collection" => Multiplicity.COLLECTION
        case "set" => Multiplicity.SET
    }
}, {
    case m: Multiplicity => JString(m match {
        case Multiplicity.OPTIONAL => "optional"
        case Multiplicity.REQUIRED => "required"
        case Multiplicity.COLLECTION => "collection"
        case Multiplicity.SET => "set"
    }

    )
}
    ))

trait TypeHelpers {
    def requiredAttr(name: String, dataType: IDataType[_]) =
        new AttributeDefinition(name, dataType.getName, Multiplicity.REQUIRED, false, null)

    def optionalAttr(name: String, dataTypeName: String) =
        new AttributeDefinition(name, dataTypeName, Multiplicity.OPTIONAL, false, null)


    def optionalAttr(name: String, dataType: IDataType[_]) =
        new AttributeDefinition(name, dataType.getName, Multiplicity.OPTIONAL, false, null)

    def structDef(name: String, attrs: AttributeDefinition*) = {
        new StructTypeDefinition(name, attrs.toArray)
    }

    def defineTraits(ts: TypeSystem, tDefs: HierarchicalTypeDefinition[TraitType]*) = {
        ts.defineTraitTypes(tDefs: _*)
    }

    def createTraitTypeDef(name: String, superTypes: Seq[String], attrDefs: AttributeDefinition*):
    HierarchicalTypeDefinition[TraitType] = {
        val sts = ImmutableList.copyOf(superTypes.toArray)
        return new HierarchicalTypeDefinition[TraitType](classOf[TraitType], name,
            sts, attrDefs.toArray)
    }

    def createClassTypeDef(name: String, superTypes: Seq[String], attrDefs: AttributeDefinition*):
    HierarchicalTypeDefinition[ClassType] = {
        val sts = ImmutableList.copyOf(superTypes.toArray)
        return new HierarchicalTypeDefinition[ClassType](classOf[ClassType], name,
            sts, attrDefs.toArray)
    }

    @throws(classOf[MetadataException])
    def defineClassType(ts: TypeSystem, classDef: HierarchicalTypeDefinition[ClassType]): ClassType = {
        ts.defineTypes(ImmutableList.of[StructTypeDefinition],
            ImmutableList.of[HierarchicalTypeDefinition[TraitType]],
            ImmutableList.of[HierarchicalTypeDefinition[ClassType]](classDef))
        return ts.getDataType(classOf[ClassType], classDef.typeName)
    }
}