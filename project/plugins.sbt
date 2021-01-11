import sbt.io.Path
import sbt.librarymanagement.ivy.Credentials

val nexusCredentials =
  if (sys.env.contains("NEXUS_USER"))
    Credentials(
      realm = "Sonatype Nexus Repository Manager",
      host = "nexus.scx-it.net",
      userName = sys.env("NEXUS_USER"),
      passwd = sys.env("NEXUS_PASSWORD")
    )
  else Credentials(Path.userHome / ".ivy2" / ".credentials")
credentials += nexusCredentials
resolvers += "Nexus" at "https://nexus.scx-it.net/nexus/content/repositories/releases"
resolvers += Resolver.bintrayRepo("streetcontxt", "maven")
