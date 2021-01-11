package scx.sbt.jooq.scala.codegen

sealed trait ScalaCodegenStrategy

object ScalaCodegenStrategy {

  case object Always extends ScalaCodegenStrategy

  case object IfAbsent extends ScalaCodegenStrategy

  case object Never extends ScalaCodegenStrategy

}
