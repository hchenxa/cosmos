package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.twitter.util.Future
import io.circe.Json

trait PackageRunner[T] {

  /** Execute the package described by the given JSON configuration.
    *
    * @param renderedConfig the fully-specified configuration of the package to run
    * @return The response from Marathon/Kubernetes, if the request was successful.
    */
  def launch(renderedConfig: Json, option: Option[String] = None)(implicit session: RequestSession): Future[T]
}
