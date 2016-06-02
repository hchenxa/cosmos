package com.mesosphere.cosmos

/** This is used in [[com.mesosphere.cosmos.circe.EncodersDecodersSpec]], but Shapeless is unable
  * to find it if it's defined in that file, or the `com.mesosphere.cosmos.circe` package.
  */
sealed trait ExampleVersionedAdt
case object Version0 extends ExampleVersionedAdt
case class Version1(a: String) extends ExampleVersionedAdt
case class Version2(a: String, b: Int) extends ExampleVersionedAdt
case class Version3(b: Int, c: Double) extends ExampleVersionedAdt
