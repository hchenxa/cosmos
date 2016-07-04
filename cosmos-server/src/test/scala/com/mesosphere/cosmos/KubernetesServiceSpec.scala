package com.mesosphere.cosmos

import java.nio.charset.StandardCharsets
import java.util.Base64
import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.twitter.util.{Return, Throw}
import io.circe.Encoder
import io.circe.parse._
import org.scalatest.FreeSpec
import org.slf4j.LoggerFactory

final class KubernetesServiceSpec extends FreeSpec {

  private[this] val service: String =
    """
    |{
    |  "kind": "Service",
    |  "apiVersion": "v1",
    |  "metadata": {
    |    "name": "nginx",
    |    "namespace": "default",
    |    "selfLink": "/api/v1/namespaces/default/services/nginx",
    |    "uid": "efff5e95-3f2f-11e6-9096-fa163ebc2006",
    |    "resourceVersion": "1022682",
    |    "creationTimestamp": "2016-07-01T02:03:00Z",
    |    "labels": {
    |      "app": "nginx"
    |    }
    |  },
    |  "spec": {
    |    "ports": [
    |      {
    |        "protocol": "TCP",
    |        "port": 80,
    |        "targetPort": 80
    |      }
    |    ],
    |    "selector": {
    |      "app": "nginx"
    |    },
    |    "clusterIP": "192.168.3.4",
    |    "type": "ClusterIP",
    |    "sessionAffinity": "None"
    |  },
    |  "status": {
    |    "loadBalancer": {}
    |  }
    |}
    """.stripMargin
  
  private[this] val services: String =
    """
    |{
    |  "kind": "ServiceList",
    |  "apiVersion": "v1",
    |  "metadata": {
    |    "selfLink": "/api/v1/services",
    |    "resourceVersion": "1032790"
    |  },
    |  "items": [
    |    {
    |      "metadata": {
    |        "name": "kubernetes",
    |        "namespace": "default",
    |        "selfLink": "/api/v1/namespaces/default/services/kubernetes",
    |        "uid": "7d0ad35d-2d4b-11e6-81bc-fa163ebc2006",
    |        "resourceVersion": "8",
    |        "creationTimestamp": "2016-06-08T07:34:52Z",
    |        "labels": {
    |          "component": "apiserver",
    |          "provider": "kubernetes"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "name": "https",
    |            "protocol": "TCP",
    |            "port": 443,
    |            "targetPort": 443
    |          }
    |        ],
    |        "clusterIP": "192.168.3.1",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "nginx",
    |        "namespace": "default",
    |        "selfLink": "/api/v1/namespaces/default/services/nginx",
    |        "uid": "efff5e95-3f2f-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "1022682",
    |        "creationTimestamp": "2016-07-01T02:03:00Z",
    |        "labels": {
    |          "app": "nginx"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "protocol": "TCP",
    |            "port": 80,
    |            "targetPort": 80
    |          }
    |        ],
    |        "selector": {
    |          "app": "nginx"
    |        },
    |        "clusterIP": "192.168.3.4",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "heapster",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/services/heapster",
    |        "uid": "e1d237a9-3694-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "504550",
    |        "creationTimestamp": "2016-06-20T03:12:55Z",
    |        "labels": {
    |          "kubernetes.io/cluster-service": "true",
    |          "kubernetes.io/name": "Heapster"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "protocol": "TCP",
    |            "port": 80,
    |            "targetPort": 8082
    |          }
    |        ],
    |        "selector": {
    |          "k8s-app": "heapster"
    |        },
    |        "clusterIP": "192.168.3.135",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "kube-dns",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/services/kube-dns",
    |        "uid": "f0e32c9b-2d4b-11e6-81bc-fa163ebc2006",
    |        "resourceVersion": "102",
    |        "creationTimestamp": "2016-06-08T07:38:06Z",
    |        "labels": {
    |          "k8s-app": "kube-dns",
    |          "kubernetes.io/cluster-service": "true",
    |          "kubernetes.io/name": "KubeDNS"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "name": "dns",
    |            "protocol": "UDP",
    |            "port": 53,
    |            "targetPort": 53
    |          },
    |          {
    |            "name": "dns-tcp",
    |            "protocol": "TCP",
    |            "port": 53,
    |            "targetPort": 53
    |          }
    |        ],
    |        "selector": {
    |          "k8s-app": "kube-dns"
    |        },
    |        "clusterIP": "192.168.3.10",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "kubernetes-dashboard",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/services/kubernetes-dashboard",
    |        "uid": "f1053246-2d4b-11e6-81bc-fa163ebc2006",
    |        "resourceVersion": "113",
    |        "creationTimestamp": "2016-06-08T07:38:07Z",
    |        "labels": {
    |          "k8s-app": "kubernetes-dashboard",
    |          "kubernetes.io/cluster-service": "true"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "protocol": "TCP",
    |            "port": 80,
    |            "targetPort": 9090
    |          }
    |        ],
    |        "selector": {
    |          "k8s-app": "kubernetes-dashboard"
    |        },
    |        "clusterIP": "192.168.3.106",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "monitoring-grafana",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/services/monitoring-grafana",
    |        "uid": "e1bc8750-3694-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "504545",
    |        "creationTimestamp": "2016-06-20T03:12:55Z",
    |        "labels": {
    |          "kubernetes.io/cluster-service": "true",
    |          "kubernetes.io/name": "monitoring-grafana"
    |        }
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "protocol": "TCP",
    |            "port": 80,
    |            "targetPort": 3000
    |          }
    |        ],
    |        "selector": {
    |          "name": "influxGrafana"
    |        },
    |        "clusterIP": "192.168.3.66",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "monitoring-influxdb",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/services/monitoring-influxdb",
    |        "uid": "e1ede356-3694-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "504562",
    |        "creationTimestamp": "2016-06-20T03:12:55Z"
    |      },
    |      "spec": {
    |        "ports": [
    |          {
    |            "name": "http",
    |            "protocol": "TCP",
    |            "port": 8083,
    |            "targetPort": 8083
    |          },
    |          {
    |            "name": "api",
    |            "protocol": "TCP",
    |            "port": 8086,
    |            "targetPort": 8086
    |          }
    |        ],
    |        "selector": {
    |          "name": "influxGrafana"
    |        },
    |        "clusterIP": "192.168.3.82",
    |        "type": "ClusterIP",
    |        "sessionAffinity": "None"
    |      },
    |      "status": {
    |        "loadBalancer": {}
    |      }
    |    }
    |  ]
    |}
    """.stripMargin

  "Service should" - {

    "decode service" in {
      decode[KubernetesService](service) match {
        case Xor.Right(kubernetesService) => {
          assertResult("nginx")(kubernetesService.metadata.name)
        }
        case Xor.Left(parseError) => {
          val logger = LoggerFactory.getLogger("KubernetesServiceSpec")
          logger.error(s"Kubernetes service parseError: $parseError.toString()")
          throw new CirceError(parseError)
        }
      }
    }

    "decode services" in {
      decode[KubernetesServicesResponse](services) match {
        case Xor.Right(kubernetesServices) => {
          val svc = kubernetesServices.items.filter((service: KubernetesService) => {
              service.metadata.name == "nginx"
            }
          )
          assert(svc != null)
        }
        case Xor.Left(parseError) => {
          val logger = LoggerFactory.getLogger("KubernetesServiceSpec")
          logger.error(s"Kubernetes services parseError: $parseError.toString()")
          throw new CirceError(parseError)
        }
      }
    }
  }

}
