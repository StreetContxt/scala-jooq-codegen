package scx.sbt.jooq.scala.codegen
import com.typesafe.scalalogging.LazyLogging
import sbt.Keys._
import sbt._
import sbtslf4jsimple.Slf4jSimpleKeys._
import sbtslf4jsimple.Slf4jSimplePlugin

object JooqScalaCodegenPlugin extends AutoPlugin with LazyLogging {

  override def requires: Plugins = Slf4jSimplePlugin

  object autoImport extends JooqScalaCodegenKeys {

    type ScalaCodegenStrategy = scx.sbt.jooq.scala.codegen.ScalaCodegenStrategy
    val ScalaCodegenStrategy = scx.sbt.jooq.scala.codegen.ScalaCodegenStrategy

    def addJooqCodegenSettingsTo(config: Configuration): Seq[Setting[_]] =
      jooqCodegenScopedSettings(config)

  }

  import autoImport._

  override def projectConfigurations: Seq[Configuration] = Seq(JooqScalaCodegen)

  override def projectSettings: Seq[Setting[_]] =
    jooqCodegenDefaultSettings ++ jooqCodegenScopedSettings(Compile)

  def jooqCodegenDefaultSettings: Seq[Setting[_]] =
    Seq(
      libraryDependencies ++= Classpaths
        .autoLibraryDependency(
          (JooqScalaCodegen / autoScalaLibrary).value && (JooqScalaCodegen / scalaHome).value.isEmpty
            && (JooqScalaCodegen / managedScalaInstance).value,
          plugin = false,
          (JooqScalaCodegen / scalaOrganization).value,
          (JooqScalaCodegen / scalaVersion).value
        )
        .map(_ % JooqScalaCodegen)
    ) ++
      inConfig(JooqScalaCodegen)(
        Defaults.configSettings ++
          inTask(run)(
            Seq(
              fork := false,
              mainClass := Some("scx.jooq.scala.Generator")
            )
          )
      ) ++
      Slf4jSimplePlugin.slf4jSimpleScopedSettings(JooqScalaCodegen) ++
      inConfig(JooqScalaCodegen)(
        Seq(
          slf4jSimpleLogFile := "System.out",
          slf4jSimpleCacheOutputStream := true,
          slf4jSimpleShowThreadName := false,
          slf4jSimpleShowLogName := false,
          slf4jSimpleLevelInBrackets := true
        )
      )

  def jooqCodegenScopedSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      Seq(
        jooqScalaCodegen := codegenTask.value,
        jooqScalaCodegenStrategy := ScalaCodegenStrategy.IfAbsent,
        sourceGenerators += autoCodegenTask.taskValue,
        jooqScalaCodegenGeneratedSources / includeFilter := "*.scala",
        jooqScalaCodegenGeneratedSources := jooqScalaCodegenGeneratedSourcesFinder.value.get,
        jooqScalaCodegenGeneratedSourcesFinder := generatedSourcesFinderTask.value
      )
    )

  private def codegenTask =
    Def.taskDyn {
      if ((jooqScalaCodegen / skip).value) Def.task(Seq.empty[File])
      else
        Def.taskDyn {
          val directory = (Compile / sourceManaged).value / "jooqScala"
          val pkg = jooqScalaPackage.value
          val catalog = jooqScalaCatalog.value
          Def
            .sequential(
              (JooqScalaCodegen / run)
                .toTask(
                  s""" "$pkg" "$directory" "$catalog" """
                ),
              Def.task(
                jooqScalaCodegenGeneratedSourcesFinder.value.get
              )
            )
        }
    }

  private def autoCodegenTask =
    Def.taskDyn {
      if ((jooqScalaCodegen / skip).value) Def.task(Seq.empty[File])
      else
        Def.taskDyn {
          jooqScalaCodegenStrategy.value match {
            case ScalaCodegenStrategy.Always => jooqScalaCodegen
            case ScalaCodegenStrategy.IfAbsent =>
              Def.taskDyn {
                val files = jooqScalaCodegenGeneratedSourcesFinder.value.get
                if (files.isEmpty) jooqScalaCodegen else Def.task(files)
              }
            case ScalaCodegenStrategy.Never => Def.task(Seq.empty[File])
          }
        }
    }

  private def generatedSourcesFinderTask =
    Def.task {
      val directory = (Compile / sourceManaged).value / "jooqScala"

      directory.descendantsExcept(
        (jooqScalaCodegenGeneratedSources / includeFilter).value,
        (jooqScalaCodegenGeneratedSources / excludeFilter).value
      )
    }

}
