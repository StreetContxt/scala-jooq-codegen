resolvers in ThisBuild += Resolver.sonatypeRepo("public")
resolvers in ThisBuild += Resolver.mavenLocal
resolvers in ThisBuild += Resolver.bintrayRepo("streetcontxt", "maven")
resolvers in ThisBuild += "Nexus" at "https://nexus.scx-it.net/nexus/content/repositories/releases"

organization in ThisBuild := "scx"
scalaVersion in ThisBuild := "2.12.10"

val jool = "org.jooq" % "jool" % "0.9.10"
val scxCodegen = "scx" % "jooq-codegen" % "1.4.0"
val postgresql = "org.postgresql" % "postgresql" % "42.2.5"

lazy val `scala-jooq-codegen-core` = (project in file("core"))
  .settings(
    publishTo := Some(
      Resolver.file("localtrix", file("/Users/angelo/repo/localtrix"))(
        Resolver.ivyStylePatterns
      )
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.3.22",
      "org.scalameta" %% "scalafmt-dynamic" % "2.7.2",
      "org.jooq" % "jooq" % "3.13.4",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.scala-lang" % "scala-reflect" % "2.12.8"
    )
  )

lazy val `scala-jooq-codegen-plugin` = (project in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(addSbtPlugin("com.github.kxbmap" % "sbt-slf4j-simple" % "0.2.0"))
  .settings(name := "sbt-scala-jooq-codegen")
  .settings(
    publishTo := Some(
      Resolver.file("localtrix", file("/Users/angelo/repo/localtrix"))(
        Resolver.ivyStylePatterns
      )
    )
  )
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % "4.3.22",
      "org.scalameta" %% "scalafmt-dynamic" % "2.7.2",
      "org.jooq" % "jooq" % "3.13.4",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.scala-lang" % "scala-reflect" % "2.12.8"
    )
  )
  .dependsOn(`scala-jooq-codegen-core`)
