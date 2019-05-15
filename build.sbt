/** [[https://monix.io]] */
val MonixVersion = "3.0.0-RC2"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.7",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "monix-playground",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % MonixVersion
    )
  )
