package scx.jooq.scala

import java.io.{BufferedWriter, File, FileWriter}
import java.lang.reflect.ParameterizedType

import org.jooq.{Catalog, Schema, Table, TableField}
import scx.jooq.scala.Generator.ClassMappings
import sun.reflect.generics.reflectiveObjects.GenericArrayTypeImpl

import scala.jdk.CollectionConverters._
import scala.meta._
import java.nio.file._

import org.scalafmt.interfaces.Scalafmt

import scala.util.Try

object Generator {
  def main(args: Array[String]): Unit = {
    println(s"args: ${args.toList}")

    val catalog = Class
      .forName(args(2))
      .getDeclaredFields
      .filter(f => f.getType.getCanonicalName == args(2))
      .head
      .get(null)
      .asInstanceOf[Catalog]

    apply(args(0))(new File(args(1))).generateCatalog(catalog)
  }

  private val defaultConversions: Map[String, ClassMappings] = Map(
    "java.lang.Byte" -> ClassMappings(
      "Array",
      "Byte",
      "Byte2byte",
      "byte2Byte"
    ),
    "java.lang.Byte" -> ClassMappings(
      "java.lang.Byte",
      "Byte",
      "Byte2byte",
      "byte2Byte"
    ),
    "java.lang.Short" -> ClassMappings(
      "java.lang.Short",
      "Short",
      "Short2short",
      "short2Short"
    ),
    "java.lang.Character" -> ClassMappings(
      "java.lang.Character",
      "Char",
      "Character2char",
      "char2Character"
    ),
    "java.lang.Integer" -> ClassMappings(
      "java.lang.Integer",
      "Int",
      "Integer2int",
      "int2Integer"
    ),
    "java.lang.Long" -> ClassMappings(
      "java.lang.Long",
      "Long",
      "Long2long",
      "long2Long"
    ),
    "java.lang.Float" -> ClassMappings(
      "java.lang.Float",
      "Float",
      "Float2float",
      "float2Float"
    ),
    "java.lang.Double" -> ClassMappings(
      "java.lang.Double",
      "Double",
      "Double2double",
      "double2Double"
    ),
    "java.lang.Boolean" -> ClassMappings(
      "java.lang.Boolean",
      "Boolean",
      "Boolean2boolean",
      "boolean2Boolean"
    ),
    "Array" -> ClassMappings(
      "Array",
      "Array",
      "Array",
      "Array",
      anyVal = false,
      functor = true
    )
  )

  def apply(
      topPackage: String,
      conversions: Map[String, ClassMappings]
  )(base: File): Generator =
    new Generator(conversions, topPackage)(base)

  def apply(topPackage: String)(base: File): Generator =
    new Generator(defaultConversions, topPackage)(base)

  case class ClassMappings(
      javaClass: String,
      scalaClass: String,
      toScala: String,
      toJava: String,
      anyVal: Boolean = true,
      functor: Boolean = false
  )
}

class Generator(conversions: Map[String, ClassMappings], topPackage: String)(
    base: File
) {
  private val scalafmt = Scalafmt.create(this.getClass.getClassLoader)
  private val scalafmtConfig = Paths.get(".scalafmt.conf")

  def makeDirectoriesForPackage(topParentFile: File, term: Term): File = {
    term match {
      case Term.Select(parent, Term.Name(pkg)) =>
        val parentFile = makeDirectoriesForPackage(topParentFile, parent)
        val pkgFile = new File(parentFile, pkg)
        pkgFile.mkdir()
        pkgFile
      case Term.Name(pkg) =>
        val pkgFile = new File(topParentFile, pkg)
        pkgFile.mkdir()
        pkgFile
    }
  }

  def makeDirecory(d: File): File = {
    if (!d.exists()) {
      makeDirecory(d.getParentFile)
      d.mkdir()
    }
    d
  }

  def generateCatalog(catalog: Catalog): Seq[File] = {
    makeDirecory(base)

    val topPackageDir =
      makeDirectoriesForPackage(base, topPackage.parse[Term].get)

    val catalogDir = if (catalog.getName.nonEmpty) {
      new File(topPackageDir, catalog.getName)
    } else {
      topPackageDir
    }

    catalogDir.mkdir()

    catalog.getSchemas.asScala.toList.flatMap(generateSchema(catalogDir))
  }

  def generateSchema(catalogDir: File)(schema: Schema): Seq[File] = {
    val schemaDir = new File(catalogDir, schema.getName)
    schemaDir.mkdir()
    schema.getTables.asScala.toList.map(generateTableFile(schemaDir))
  }

  def generateTableFile(schemaDir: File)(table: Table[_]): File = {

    val tableFile = new File(schemaDir, table.getClass.getSimpleName + ".scala")
    val path = tableFile.toPath
    val rawSource = generateTable(table).toString()
    val source =
      Try(scalafmt.format(scalafmtConfig, path, rawSource))
        .getOrElse(rawSource)

    val bw = new BufferedWriter(new FileWriter(tableFile))
    bw.write(source)
    bw.close()
    tableFile
  }

  def generateTable(
      tableInstance: Table[_]
  ): Source = {
    val tbl: Class[_] = tableInstance.getClass
    val rec: Class[_] = tableInstance.getRecordType
    val pkg =
      Term.Select(
        if (tableInstance.getSchema.getCatalog.getName.nonEmpty) {
          Term.Select(
            topPackage.parse[Term].get,
            Term.Name(tableInstance.getSchema.getCatalog.getName)
          )
        } else {
          topPackage.parse[Term].get
        },
        Term.Name(tableInstance.getSchema.getName)
      )
    val tableFields: scala.List[TableField[_, _]] =
      getTableFields(tbl, tableInstance)
    val caseClassFields: List[Term.Param] =
      buildCaseClassParameters(tableFields)
    val modifiedFlags: List[Defn.Var] =
      buildModifiedFlags(tableFields)
    val modifiedFlagAccessors: List[Defn.Def] =
      buildModifiedFlagAccessors(tableFields)
    val copy: Defn.Def = buildCopyMethod(tbl, tableFields)
    val toRecord: Defn.Def = buildToRecord(rec, tableFields)
    val fromRecord: Defn.Def =
      buildFromRecord(tbl, rec, tableFields)

    Source(
      List(
        Pkg(
          ref = pkg,
          stats = List(
            Defn.Object(
              Nil,
              Term.Name(tbl.getSimpleName),
              templ = Template(
                early = Nil,
                inits = List(
                  Init(tbl.getCanonicalName.parse[Type].get, Name(""), Nil)
                ),
                self = Self(
                  name = Name.Anonymous(),
                  decltpe = None
                ),
                stats = Nil
              )
            ),
            Defn.Class(
              mods = Nil,
              name = Type.Name(tbl.getSimpleName),
              tparams = Nil,
              ctor = Ctor.Primary(
                mods = Nil,
                name = Name.Anonymous(),
                paramss = List(caseClassFields)
              ),
              templ = Template(
                early = Nil,
                inits = List(
                  Init(tbl.getCanonicalName.parse[Type].get, Name(""), Nil)
                ),
                self = Self(
                  name = Name.Anonymous(),
                  decltpe = None
                ),
                stats =
                  fromRecord :: toRecord :: copy :: modifiedFlags ::: modifiedFlagAccessors
              )
            )
          )
        )
      )
    )
  }

  private def getTypeFromField(tableField: TableField[_, _]) = {
    reflectTypeToMetaType(
      tableField.getTable.getClass
        .getDeclaredField(
          if (tableField.getName != tableField.getTable.getName) {
            tableField.getName.toUpperCase
          } else {
            tableField.getName.toUpperCase + "_"
          }
        )
        .getGenericType
        .asInstanceOf[ParameterizedType]
        .getActualTypeArguments
        .apply(1)
    )
  }

  def reflectTypeToMetaType: java.lang.reflect.Type => Type = {
    case arr: Class[_] if arr.isArray =>
      Type.Apply(
        Type.Name("Array"),
        List(reflectTypeToMetaType(arr.getComponentType))
      )
    case cls: Class[_] =>
      cls.getCanonicalName.parse[Type].get
    case pt: ParameterizedType =>
      Type.Apply(
        pt.getRawType.getTypeName.parse[Type].get,
        pt.getActualTypeArguments.map(t => reflectTypeToMetaType(t)).toList
      )
    case arr: GenericArrayTypeImpl =>
      Type.Apply(
        Type.Name("Array"),
        List(reflectTypeToMetaType(arr.getGenericComponentType))
      )

    case x => throw new RuntimeException(s"class was ${x.getClass}")
  }

  def convertTypeLoop: Type => Type = {
    case ap @ Type.Apply(jt, tp) =>
      conversions
        .get(jt.toString())
        .filter(_.functor)
        .map(cm =>
          Type.Apply(cm.scalaClass.parse[Type].get, tp.map(convertTypeLoop))
        )
        .getOrElse(ap)

    case jt =>
      conversions
        .get(jt.toString())
        .map(_.scalaClass.parse[Type].get)
        .getOrElse(jt)
  }

  private def convertType(tableField: TableField[_, _]): Type = {
    val stpe = {
      Some(
        getTypeFromField(tableField)
      ).map(convertTypeLoop)
        .map { st =>
          if (tableField.getDataType.nullable()) {
            Type.Apply(Type.Name("Option"), List(st))
          } else {
            st
          }
        }
        .get
    }

    stpe
  }

  def convertToScalaLoop: Type => Term = {
    case Type.Apply(jt, List(tp))
        if conversions.get(jt.toString()).exists(_.functor) =>
      Term.Block(
        List(
          Term.Apply(
            Term.Select(Term.Placeholder(), Term.Name("map")),
            List(convertToScalaLoop(tp))
          )
        )
      )

    case jt =>
      conversions
        .get(jt.toString())
        .map(cm => cm.toScala.parse[Term].get)
        .getOrElse(Term.Name("identity"))
  }

  private def convertToScalaValue(
      tableField: TableField[_, _],
      javaVal: Term
  ): Term = {

    val javaType: Type = getTypeFromField(tableField)
    val scalaType: Type = convertType(tableField)
    val conversion = convertToScalaLoop(javaType)

    val convertValue = Term.Apply(
      Term.Select(
        Term.Apply(Term.Name("Option"), List(javaVal)),
        Term.Name("map")
      ),
      List(conversion)
    )

    val anyVal =
      conversions.get(javaType.toString()).exists(_.anyVal)
    if (javaType.toString() == scalaType.toString()) {
      javaVal
    } else if (tableField.getDataType.nullable) {
      convertValue
    } else if (anyVal) {
      Term.Select(convertValue, Term.Name("get"))
    } else {
      Term.Select(convertValue, Term.Name("orNull"))
    }
  }

  private def convertToJavaValue(
      tpe: TableField[_, _],
      scalaVal: Term
  ): Term = {
    val conversionType =
      if (tpe.getType.isArray) tpe.getType.getComponentType else tpe.getType

    conversions
      .get(conversionType.getCanonicalName)
      .map(_.toJava)
      .map { conv =>
        if (tpe.getType.isArray && tpe.getDataType.nullable()) {
          Term.Select(
            Term.Apply(
              Term.Select(scalaVal, Term.Name("map")),
              List(
                Term.Apply(
                  Term.Select(Term.Placeholder(), Term.Name("map")),
                  List(Term.Name(conv))
                )
              )
            ),
            Term.Name("orNull")
          )
        } else if (tpe.getDataType.nullable()) {
          Term.Select(
            Term.Apply(
              Term.Select(scalaVal, Term.Name("map")),
              List(Term.Name(conv))
            ),
            Term.Name("orNull")
          )
        } else if (tpe.getType.isArray) {
          Term.Apply(
            Term.Select(scalaVal, Term.Name("map")),
            List(Term.Name(conv))
          )
        } else {
          Term.Apply(Term.Name(conv), List(scalaVal))
        }
      }
      .getOrElse {
        if (tpe.getDataType.nullable()) {
          Term.Select(scalaVal, Term.Name("orNull"))
        } else {
          scalaVal
        }
      }
  }

  private def buildFromRecord(
      tbl: Class[_],
      rec: Class[_],
      tableFields: List[TableField[_, _]]
  ): Defn.Def =
    Defn.Def( //  body : scala.meta.Term
      mods = Nil,
      name = Term.Name("fromRecord"),
      tparams = Nil,
      paramss = List(
        List(
          Term.Param(
            Nil,
            Term.Name("rec"),
            Some(rec.getCanonicalName.parse[Type].get),
            None
          )
        )
      ),
      decltpe = Some(Type.Name(tbl.getSimpleName)),
      body = Term.Block(
        //mods : scala.List[scala.meta.Mod], pats : scala.List[scala.meta.Pat], decltpe : scala.Option[scala.meta.Type], rhs : scala.meta.Term
        Defn.Val(
          mods = Nil,
          pats = List(Pat.Var(Term.Name("result"))),
          decltpe = None,
          rhs = Term.New(
            Init(
              tbl.getSimpleName.parse[Type].get,
              Name(""),
              List(tableFields.map { tf =>
                Term.Assign(
                  Term.Name(snakeToCamel(tf.getName)),
                  convertToScalaValue(
                    tf,
                    Term.Select(
                      Term.Name("rec"),
                      Term
                        .Name(
                          s"get${snakeToCamel(tf.getName, initialUpper = true)}"
                        )
                    )
                  )
                )
              })
            )
          )
        ) :: tableFields.map { tf =>
          Term
            .Assign(
              Term.Select(
                Term.Name("result"),
                Term.Name(snakeToCamel(tf.getName) + "ModifiedFlag")
              ),
              Term.Apply(
                Term.Select(Term.Name("rec"), Term.Name("changed")),
                List(Lit.String(tf.getName))
              )
            )
        } ::: List(Term.Name("result"))
      )
    )

  private def buildToRecord(
      rec: Class[_],
      tableFields: List[TableField[_, _]]
  ): Defn.Def =
    Defn.Def( //  body : scala.meta.Term
      mods = Nil,
      name = Term.Name("toRecord"),
      tparams = Nil,
      paramss = Nil,
      decltpe = Some(rec.getCanonicalName.parse[Type].get),
      body = Term.Block(
        Defn.Val(
          mods = Nil,
          pats = List(Pat.Var(Term.Name("rec"))),
          decltpe = None,
          rhs = Term.New(
            Init(
              rec.getCanonicalName.parse[Type].get,
              Name(""),
              Nil
            )
          )
        ) ::
          tableFields.map { tf =>
            Term.Apply(
              Term.Select(
                Term.Name("rec"),
                Term.Name(
                  s"set${snakeToCamel(tf.getName, initialUpper = true)}"
                )
              ),
              List(
                convertToJavaValue(
                  tf,
                  Term.Select(
                    Term.This(Name("")),
                    Term.Name(snakeToCamel(tf.getName))
                  )
                )
              )
            )
          } ::: tableFields.map { tf =>
          Term.Apply(
            Term.Select(
              Term.Name("rec"),
              Term.Name(
                s"changed"
              )
            ),
            List(
              Lit.String(tf.getName),
              Term.Select(
                Term.This(Name("")),
                Term.Name(snakeToCamel(tf.getName) + "Modified")
              )
            )
          )
        } ::: List(Term.Name("rec"))
      )
    )

  private def buildCopyMethod(
      tbl: Class[_],
      tableFields: List[TableField[_, _]]
  ): Defn.Def = {

    val params = tableFields.map { tf =>
      val scalaClass = convertType(tf)
      Term.Param(
        mods = Nil,
        name = Term.Name(snakeToCamel(tf.getName)),
        decltpe = Some(scalaClass),
        default = Some(Term.Name(snakeToCamel(tf.getName)))
      )
    }

    Defn.Def(
      mods = Nil,
      name = Term.Name("copy"),
      tparams = Nil,
      paramss = List(params),
      decltpe = Some(Type.Name(tbl.getSimpleName)),
      body = Term.Block(
        Defn.Val(
          Nil,
          List(Pat.Var(Term.Name("result"))),
          None,
          Term.New(
            Init(
              Type.Name(tbl.getSimpleName),
              Name(""),
              List(tableFields.map(tf => Term.Name(snakeToCamel(tf.getName))))
            )
          )
        ) ::
          tableFields.map { tf =>
            Term
              .Assign(
                Term.Select(
                  Term.Name("result"),
                  Term.Name(snakeToCamel(tf.getName) + "ModifiedFlag")
                ),
                Term.ApplyInfix(
                  Term.Select(
                    Term.This(Name("")),
                    Term.Name(snakeToCamel(tf.getName) + "ModifiedFlag")
                  ),
                  Term.Name("||"),
                  Nil,
                  List(
                    Term.ApplyInfix(
                      Term.Select(
                        Term.This(Name("")),
                        Term.Name(snakeToCamel(tf.getName))
                      ),
                      Term.Name("!="),
                      Nil,
                      List(Term.Name(snakeToCamel(tf.getName)))
                    )
                  )
                )
              )
          } ++ List(Term.Name("result"))
      )
    )

  }

  private def buildModifiedFlagAccessors(
      tableFields: List[TableField[_, _]]
  ): List[Defn.Def] = {
    tableFields.map { tf =>
      Defn.Def(
        Nil,
        Term.Name(snakeToCamel(tf.getName) + "Modified"),
        Nil,
        Nil,
        Some(Type.Name("Boolean")),
        Term.Name(snakeToCamel(tf.getName) + "ModifiedFlag")
      )
    }
  }

  private def buildModifiedFlags(
      tableFields: List[TableField[_, _]]
  ): List[Defn.Var] = {
    tableFields.map { tf =>
      Defn.Var(
        mods = Nil,
        List(Pat.Var(Term.Name(snakeToCamel(tf.getName) + "ModifiedFlag"))),
        Some(Type.Name("Boolean")),
        Some(Lit.Boolean(false))
      )
    }
  }

  private def buildCaseClassParameters(
      tableFields: List[TableField[_, _]]
  ): List[Term.Param] = {
    tableFields.map { tf =>
      Term
        .Param(
          List(),
          Term.Name(snakeToCamel(tf.getName)),
          Some(convertType(tf)),
          None
        )
    }
  }

  private def getTableFields(
      tbl: Class[_],
      instance: Table[_]
  ): List[TableField[_, _]] = {
    val allFields = tbl.getDeclaredFields.toList
    val tableFields: List[TableField[_, _]] = allFields
      .filter(field =>
        classOf[TableField[_, _]].isAssignableFrom(field.getType)
      )
      .map(f => f.get(instance).asInstanceOf[TableField[_, _]])
    tableFields
  }

  private def snakeToCamel(
      snake: String,
      initialUpper: Boolean = false
  ): String = {
    def loop(x: List[Char]): List[Char] =
      (x: @unchecked) match {
        case '_' :: '_' :: rest => loop('_' :: rest)
        case '_' :: c :: rest   => Character.toUpperCase(c) :: loop(rest)
        case '_' :: Nil         => Nil
        case c :: rest          => c :: loop(rest)
        case Nil                => Nil
      }
    if (snake == null)
      ""
    else if (initialUpper)
      loop('_' :: snake.toList).mkString
    else
      loop(snake.toList).mkString
  }
}
