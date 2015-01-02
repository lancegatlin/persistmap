scalaVersion := "2.11.1"

organization := "org.lancegatlin"

name := "history"

version := "1.1.0"

scalacOptions ++= Seq("-feature","-unchecked", "-deprecation")

libraryDependencies ++= Seq(
  "net.s_mach" %% "concurrent" % "1.1.0",
  "net.s_mach" %% "datadiff" % "1.0.0",
  "joda-time" % "joda-time" % "2.6",
  "org.joda" % "joda-convert" % "1.7",
  "org.scalatest" % "scalatest_2.11" % "2.2.0" % "test"
)


//testOptions in Test += Tests.Argument("-l s_mach.concurrent.DelayAccuracyTest")

parallelExecution in Test := false