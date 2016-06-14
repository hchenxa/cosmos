package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion
import io.circe.JsonObject

case class InstallKubernetesRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion] = None,
  options: Option[JsonObject] = None
)
