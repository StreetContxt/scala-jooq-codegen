import sbt.MessageOnlyException
val r = """^\w+(\.\w+)*$""".r

"db" match {
  case t @ r(_) => t.split('.').foldLeft("targetDir")(_ + "/" + _)
  case invalid =>
    throw new MessageOnlyException(
      s"invalid packageName format: $invalid"
    )

}
