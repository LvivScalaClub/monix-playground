/** [[https://monix.io]] */
val MonixVersion = "3.0.0-RC2"
val MonixCirceVersion = "0.0.1"
val SttpVersion = "1.5.16"
val JSurferVersion = "1.4.3"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.7",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "monix-playground",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % MonixVersion,
      "io.monix" %% "monix-circe" % MonixCirceVersion,
      "com.github.jsurfer" % "jsurfer-jackson" % JSurferVersion,
      "com.softwaremill.sttp" %% "core" % SttpVersion,
      "com.softwaremill.sttp" %% "async-http-client-backend-monix" % SttpVersion
    )
  )
