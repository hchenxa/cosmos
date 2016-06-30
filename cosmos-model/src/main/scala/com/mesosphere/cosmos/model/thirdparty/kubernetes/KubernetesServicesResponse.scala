package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesServicesResponse (
  services: List[KubernetesService]
)