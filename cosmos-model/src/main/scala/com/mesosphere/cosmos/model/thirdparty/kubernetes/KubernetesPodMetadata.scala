package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesPodMetadata(name: String, labels: Map[String, String])
