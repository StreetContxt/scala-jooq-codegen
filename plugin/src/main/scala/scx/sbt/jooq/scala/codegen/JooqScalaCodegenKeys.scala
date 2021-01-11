package scx.sbt.jooq.scala.codegen

import sbt._

trait JooqScalaCodegenKeys {

  val JooqScalaCodegen = config("jooq-scala-codegen").hide

  val jooqScalaCodegen = taskKey[Seq[File]]("Run jOOQ codegen")

  val jooqScalaPackage =
    settingKey[String]("jOOQ codegen configuration")
  val jooqScalaCatalog =
    settingKey[String]("jOOQ codegen configuration")

  val jooqScalaCodegenStrategy =
    settingKey[ScalaCodegenStrategy]("jOOQ codegen strategy")

  val jooqScalaCodegenGeneratedSources =
    taskKey[Seq[File]]("jOOQ codegen generated sources")
  val jooqScalaCodegenGeneratedSourcesFinder =
    taskKey[PathFinder]("PathFinder for jOOQ codegen generated sources")

}

object JooqScalaCodegenKeys extends JooqScalaCodegenKeys
