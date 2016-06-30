package com.mesosphere.cosmos.model.thirdparty.kubernetes

import io.circe.JsonObject

case class KubernetesError(message: String, details: Option[List[JsonObject]] = None)
