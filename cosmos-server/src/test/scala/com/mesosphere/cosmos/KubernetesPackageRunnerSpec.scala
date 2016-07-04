package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.handler.KubernetesInstallHandler
import io.circe.parse.parse
import io.circe.{Encoder, Json, JsonObject}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import org.slf4j.LoggerFactory

class KubernetesPackageRunnerSpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  "if both rc and service defined in kubernetes package" in {
    val kubePackage =
      """
        |{
        |  "id": "/nginx",
        |  "apps": [
        |    {
        |        "apiVersion": "v1",
        |        "kind": "Service",
        |        "metadata": {
        |            "name": "nginx",
        |            "labels": {
        |                "app": "nginx"
        |            }
        |        },
        |        "spec": {
        |            "ports": [
        |                {
        |                    "protocol": "TCP",
        |                    "port": 80,
        |                    "targetPort": 80
        |                }
        |            ],
        |            "selector": {
        |                "app": "nginx"
        |            }
        |        }
        |    },
        |    {
        |        "apiVersion": "v1",
        |        "kind": "ReplicationController",
        |        "metadata": {
        |            "name": "nginx",
        |            "labels": {
        |                "app": "nginx"
        |            }
        |        },
        |        "spec": {
        |            "replicas": 1,
        |            "selector": {
        |                "app": "nginx"
        |            },
        |            "template": {
        |                "metadata": {
        |                    "name": "nginx",
        |                    "labels": {"app": "nginx"}
        |                },
        |                "spec": {
        |                    "containers": [
        |                        {
        |                            "name": "nginx",
        |                            "image": "nginx:latest",
        |                            "resources": {
        |                              "requests": {
        |                                "cpu": "100m"
        |                              }
        |                            },
        |                            "imagePullPolicy": "Always",
        |                            "securityContext": {
        |                                "privileged": false
        |                            }
        |                        }
        |                    ],
        |                    "restartPolicy": "Always",
        |                    "terminationGracePeriodSeconds": 30,
        |                    "dnsPolicy": "ClusterFirst",
        |                    "securityContext": {}
        |                }
        |            }
        |        }
        |    }
        |   ]
        |}
      """.stripMargin

      val logger = LoggerFactory.getLogger("com.mesosphere.cosmos.KubernetesPackageRunnerSpec")

    try {
      val kubeObj = parse(kubePackage) match {
        case Xor.Right(json) => json
        case Xor.Left(parsingFailure) => throw new CirceError(parsingFailure)
      }
      
      val (rcs: List[Json], services: List[Json]) = KubernetesPackageRunner.getKubernetesObjects(kubeObj)
      
      rcs.foreach { rc => logger.info("Kubernetes rc: {}", rc) }
      services.foreach { svc => logger.info("Kubernetes service: {}", svc) }
      
      assertResult(1)(rcs.length)
      assertResult(1)(services.length)
    } catch {
      case e @ CirceError(err) => fail(err.getMessage)
    }
  }

}
