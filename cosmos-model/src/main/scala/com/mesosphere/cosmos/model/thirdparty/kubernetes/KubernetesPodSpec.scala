package com.mesosphere.cosmos.model.thirdparty.kubernetes

case class KubernetesPodSpec(containers: Map[String, KubernetesPodContainer])
