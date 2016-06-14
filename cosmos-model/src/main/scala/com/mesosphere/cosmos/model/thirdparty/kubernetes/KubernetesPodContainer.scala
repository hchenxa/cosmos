package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesPodContainer(name: String, image: String, ports: Map[String, Int])
