package com.mesosphere.cosmos.model

import com.mesosphere.universe.PackageDetailsVersion
import io.circe.JsonObject

case class InstallMarathonRequest(
  packageName: String,
  packageVersion: Option[PackageDetailsVersion] = None,
  options: Option[JsonObject] = None,
  appId: Option[AppId] = None
) extends InstallRequest(packageName, packageVersion, options)