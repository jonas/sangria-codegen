name := "test"
scalaVersion in ThisBuild := "2.12.3"

val StarWarsDir = file(sys.props("samples.dir")) / "starwars"

val server = project
  .enablePlugins(SangriaSchemagenPlugin)
  .settings(
    libraryDependencies += "org.sangria-graphql" %% "sangria" % "1.2.2",
    sangriaSchemagenSnippet in Compile := Some(
      "com.mediative.sangria.codegen.starwars.TestSchema.StarWarsSchema")
  )

val client = project
  .enablePlugins(SangriaCodegenPlugin)
  .settings(
    sangriaCodegenSchema in Compile := (sangriaSchemagen in (server, Compile)).value,
    resourceDirectories in (Compile, sangriaCodegen) += StarWarsDir,
    includeFilter in (Compile, sangriaCodegen) := "MultiQuery.graphql",
    name in (Compile, sangriaCodegen) := "MultiQueryApi"
  )

TaskKey[Unit]("check") := {
  val file     = (sangriaCodegen in (client, Compile)).value
  val expected = StarWarsDir / "MultiQuery.scala"

  assert(file.exists)

  val compare = IO.read(file).trim == IO.read(expected).trim
  if (!compare)
    s"diff -u $expected $file".!
  assert(compare, s"$file does not equal $expected")
}
