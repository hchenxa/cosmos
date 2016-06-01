package com.mesosphere.cosmos

/** Intended to be used only in tests.
  *
  * For some reason Shapeless fails to create a generic representation of this class if it is
  * defined in test scope or in the `com.mesosphere.cosmos.circe` package.
  */
sealed trait ExampleVersionedAdt
case object Version0 extends ExampleVersionedAdt
case class Version1(a: String) extends ExampleVersionedAdt
case class Version2(a: String, b: Int) extends ExampleVersionedAdt
case class Version3(b: Int, c: Double) extends ExampleVersionedAdt
