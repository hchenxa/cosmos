package com.mesosphere.cosmos.http

class MediaTypeOps(val mediaType: MediaType) extends AnyVal {
  def isCompatibleWith(other: MediaType): Boolean = {
    MediaTypeOps.compatible(mediaType, other)
  }
}

object MediaTypeOps {
  import scala.language.implicitConversions

  implicit def mediaTypeToMediaTypeOps(mt: MediaType): MediaTypeOps = {
    new MediaTypeOps(mt)
  }

  def compatibleIgnoringParameters(expected: MediaType, actual: MediaType): Boolean = {
    expected.`type` == actual.`type` && expected.subType == actual.subType
  }

  def compatible(expected: MediaType, actual: MediaType): Boolean = {
    val typesAndSubTypesCompatible = compatibleIgnoringParameters(expected, actual)

    val paramsCompatible = (expected.parameters, actual.parameters) match {
      case (None, _) => true
      case (Some(_), None) => false
      case (Some(l), Some(r)) => isLeftSubSetOfRight(l, r)
    }

    typesAndSubTypesCompatible && paramsCompatible
  }

  private[this] def isLeftSubSetOfRight(left: Map[String, String], right: Map[String, String]): Boolean = {
    left.forall { case (key, value) => right.get(key).contains(value) }
  }
}
