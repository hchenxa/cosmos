package com.mesosphere.cosmos

import java.nio.charset.StandardCharsets
import java.util.Base64
import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.thirdparty.kubernetes._
import com.twitter.util.{Return, Throw}
import io.circe.Encoder
import io.circe.parse._
import org.scalatest.FreeSpec
import org.slf4j.LoggerFactory

final class KubernetesRCSpec extends FreeSpec {

  private[this] val rcs: String =
    """
    |{
    |  "kind": "ReplicationControllerList",
    |  "apiVersion": "v1",
    |  "metadata": {
    |    "selfLink": "/api/v1/replicationcontrollers",
    |    "resourceVersion": "1032949"
    |  },
    |  "items": [
    |    {
    |      "metadata": {
    |        "name": "nginx",
    |        "namespace": "default",
    |        "selfLink": "/api/v1/namespaces/default/replicationcontrollers/nginx",
    |        "uid": "effb72cd-3f2f-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "1022681",
    |        "generation": 1,
    |        "creationTimestamp": "2016-07-01T02:03:00Z",
    |        "labels": {
    |          "app": "nginx"
    |        }
    |      },
    |      "spec": {
    |        "replicas": 1,
    |        "selector": {
    |          "app": "nginx"
    |        },
    |        "template": {
    |          "metadata": {
    |            "name": "nginx",
    |            "creationTimestamp": null,
    |            "labels": {
    |              "app": "nginx"
    |            }
    |          },
    |          "spec": {
    |            "containers": [
    |              {
    |                "name": "nginx",
    |                "image": "nginx",
    |                "resources": {
    |                  "requests": {
    |                    "cpu": "100m"
    |                  }
    |                },
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "Always",
    |                "securityContext": {
    |                  "privileged": false
    |                }
    |              }
    |            ],
    |            "restartPolicy": "Always",
    |            "terminationGracePeriodSeconds": 30,
    |            "dnsPolicy": "ClusterFirst",
    |            "securityContext": {}
    |          }
    |        }
    |      },
    |      "status": {
    |        "replicas": 1,
    |        "fullyLabeledReplicas": 1,
    |        "observedGeneration": 1
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "heapster",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/replicationcontrollers/heapster",
    |        "uid": "e1c6f00e-3694-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "504555",
    |        "generation": 1,
    |        "creationTimestamp": "2016-06-20T03:12:55Z",
    |        "labels": {
    |          "k8s-app": "heapster",
    |          "name": "heapster",
    |          "version": "v6"
    |        }
    |      },
    |      "spec": {
    |        "replicas": 1,
    |        "selector": {
    |          "k8s-app": "heapster",
    |          "version": "v6"
    |        },
    |        "template": {
    |          "metadata": {
    |            "creationTimestamp": null,
    |            "labels": {
    |              "k8s-app": "heapster",
    |              "version": "v6"
    |            }
    |          },
    |          "spec": {
    |            "containers": [
    |              {
    |                "name": "heapster",
    |                "image": "kubernetes/heapster:canary",
    |                "command": [
    |                  "/heapster",
    |                  "--source=kubernetes:https://kubernetes.default",
    |                  "--sink=influxdb:http://monitoring-influxdb:8086"
    |                ],
    |                "resources": {},
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "Always"
    |              }
    |            ],
    |            "restartPolicy": "Always",
    |            "terminationGracePeriodSeconds": 30,
    |            "dnsPolicy": "ClusterFirst",
    |            "securityContext": {}
    |          }
    |        }
    |      },
    |      "status": {
    |        "replicas": 1,
    |        "fullyLabeledReplicas": 1,
    |        "observedGeneration": 1
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "influxdb-grafana",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/replicationcontrollers/influxdb-grafana",
    |        "uid": "e1da7a5e-3694-11e6-9096-fa163ebc2006",
    |        "resourceVersion": "504569",
    |        "generation": 1,
    |        "creationTimestamp": "2016-06-20T03:12:55Z",
    |        "labels": {
    |          "name": "influxGrafana"
    |        }
    |      },
    |      "spec": {
    |        "replicas": 1,
    |        "selector": {
    |          "name": "influxGrafana"
    |        },
    |        "template": {
    |          "metadata": {
    |            "creationTimestamp": null,
    |            "labels": {
    |              "name": "influxGrafana"
    |            }
    |          },
    |          "spec": {
    |            "volumes": [
    |              {
    |                "name": "influxdb-storage",
    |                "emptyDir": {}
    |              },
    |              {
    |                "name": "grafana-storage",
    |                "emptyDir": {}
    |              }
    |            ],
    |            "containers": [
    |              {
    |                "name": "influxdb",
    |                "image": "kubernetes/heapster_influxdb:v0.5",
    |                "resources": {},
    |                "volumeMounts": [
    |                  {
    |                    "name": "influxdb-storage",
    |                    "mountPath": "/data"
    |                  }
    |                ],
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              },
    |              {
    |                "name": "grafana",
    |                "image": "gcr.io/google_containers/heapster_grafana:v2.6.0-2",
    |                "env": [
    |                  {
    |                    "name": "INFLUXDB_SERVICE_URL",
    |                    "value": "http://monitoring-influxdb:8086"
    |                  },
    |                  {
    |                    "name": "GF_AUTH_BASIC_ENABLED",
    |                    "value": "false"
    |                  },
    |                  {
    |                    "name": "GF_AUTH_ANONYMOUS_ENABLED",
    |                    "value": "true"
    |                  },
    |                  {
    |                    "name": "GF_AUTH_ANONYMOUS_ORG_ROLE",
    |                    "value": "Admin"
    |                  },
    |                  {
    |                    "name": "GF_SERVER_ROOT_URL",
    |                    "value": "/api/v1/proxy/namespaces/kube-system/services/monitoring-grafana/"
    |                  }
    |                ],
    |                "resources": {},
    |                "volumeMounts": [
    |                  {
    |                    "name": "grafana-storage",
    |                    "mountPath": "/var"
    |                  }
    |                ],
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              }
    |            ],
    |            "restartPolicy": "Always",
    |            "terminationGracePeriodSeconds": 30,
    |            "dnsPolicy": "ClusterFirst",
    |            "securityContext": {}
    |          }
    |        }
    |      },
    |      "status": {
    |        "replicas": 1,
    |        "fullyLabeledReplicas": 1,
    |        "observedGeneration": 1
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "kube-dns-v14",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/replicationcontrollers/kube-dns-v14",
    |        "uid": "f0d1cf41-2d4b-11e6-81bc-fa163ebc2006",
    |        "resourceVersion": "99",
    |        "generation": 1,
    |        "creationTimestamp": "2016-06-08T07:38:06Z",
    |        "labels": {
    |          "k8s-app": "kube-dns",
    |          "kubernetes.io/cluster-service": "true",
    |          "version": "v14"
    |        }
    |      },
    |      "spec": {
    |        "replicas": 1,
    |        "selector": {
    |          "k8s-app": "kube-dns",
    |          "version": "v14"
    |        },
    |        "template": {
    |          "metadata": {
    |            "creationTimestamp": null,
    |            "labels": {
    |              "k8s-app": "kube-dns",
    |              "kubernetes.io/cluster-service": "true",
    |              "version": "v14"
    |            }
    |          },
    |          "spec": {
    |            "containers": [
    |              {
    |                "name": "kubedns",
    |                "image": "gcr.io/google_containers/kubedns-amd64:1.3",
    |                "args": [
    |                  "--domain=cluster.local.",
    |                  "--dns-port=10053"
    |                ],
    |                "ports": [
    |                  {
    |                    "name": "dns-local",
    |                    "containerPort": 10053,
    |                    "protocol": "UDP"
    |                  },
    |                  {
    |                    "name": "dns-tcp-local",
    |                    "containerPort": 10053,
    |                    "protocol": "TCP"
    |                  }
    |                ],
    |                "resources": {
    |                  "limits": {
    |                    "cpu": "100m",
    |                    "memory": "200Mi"
    |                  },
    |                  "requests": {
    |                    "cpu": "100m",
    |                    "memory": "50Mi"
    |                  }
    |                },
    |                "livenessProbe": {
    |                  "httpGet": {
    |                    "path": "/healthz",
    |                    "port": 8080,
    |                    "scheme": "HTTP"
    |                  },
    |                  "initialDelaySeconds": 60,
    |                  "timeoutSeconds": 5,
    |                  "periodSeconds": 10,
    |                  "successThreshold": 1,
    |                  "failureThreshold": 5
    |                },
    |                "readinessProbe": {
    |                  "httpGet": {
    |                    "path": "/readiness",
    |                    "port": 8081,
    |                    "scheme": "HTTP"
    |                  },
    |                  "initialDelaySeconds": 30,
    |                  "timeoutSeconds": 5,
    |                  "periodSeconds": 10,
    |                  "successThreshold": 1,
    |                  "failureThreshold": 3
    |                },
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              },
    |              {
    |                "name": "dnsmasq",
    |                "image": "gcr.io/google_containers/dnsmasq:1.1",
    |                "args": [
    |                  "--cache-size=1000",
    |                  "--no-resolv",
    |                  "--server=127.0.0.1#10053"
    |                ],
    |                "ports": [
    |                  {
    |                    "name": "dns",
    |                    "containerPort": 53,
    |                    "protocol": "UDP"
    |                  },
    |                  {
    |                    "name": "dns-tcp",
    |                    "containerPort": 53,
    |                    "protocol": "TCP"
    |                  }
    |                ],
    |                "resources": {},
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              },
    |              {
    |                "name": "healthz",
    |                "image": "gcr.io/google_containers/exechealthz-amd64:1.0",
    |                "args": [
    |                  "-cmd=nslookup kubernetes.default.svc.cluster.local 127.0.0.1 \u003e/dev/null",
    |                  "-port=8080"
    |                ],
    |                "ports": [
    |                  {
    |                    "containerPort": 8080,
    |                    "protocol": "TCP"
    |                  }
    |                ],
    |                "resources": {
    |                  "limits": {
    |                    "cpu": "10m",
    |                    "memory": "20Mi"
    |                  },
    |                  "requests": {
    |                    "cpu": "10m",
    |                    "memory": "20Mi"
    |                  }
    |                },
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              }
    |            ],
    |            "restartPolicy": "Always",
    |            "terminationGracePeriodSeconds": 30,
    |            "dnsPolicy": "Default",
    |            "securityContext": {}
    |          }
    |        }
    |      },
    |      "status": {
    |        "replicas": 1,
    |        "fullyLabeledReplicas": 1,
    |        "observedGeneration": 1
    |      }
    |    },
    |    {
    |      "metadata": {
    |        "name": "kubernetes-dashboard-v1.1.0-beta2",
    |        "namespace": "kube-system",
    |        "selfLink": "/api/v1/namespaces/kube-system/replicationcontrollers/kubernetes-dashboard-v1.1.0-beta2",
    |        "uid": "f0f41068-2d4b-11e6-81bc-fa163ebc2006",
    |        "resourceVersion": "110",
    |        "generation": 1,
    |        "creationTimestamp": "2016-06-08T07:38:06Z",
    |        "labels": {
    |          "k8s-app": "kubernetes-dashboard",
    |          "kubernetes.io/cluster-service": "true",
    |          "version": "v1.1.0-beta2"
    |        }
    |      },
    |      "spec": {
    |        "replicas": 1,
    |        "selector": {
    |          "k8s-app": "kubernetes-dashboard"
    |        },
    |        "template": {
    |          "metadata": {
    |            "creationTimestamp": null,
    |            "labels": {
    |              "k8s-app": "kubernetes-dashboard",
    |              "kubernetes.io/cluster-service": "true",
    |              "version": "v1.1.0-beta2"
    |            }
    |          },
    |          "spec": {
    |            "containers": [
    |              {
    |                "name": "kubernetes-dashboard",
    |                "image": "gcr.io/google_containers/kubernetes-dashboard-amd64:v1.1.0-beta2",
    |                "ports": [
    |                  {
    |                    "containerPort": 9090,
    |                    "protocol": "TCP"
    |                  }
    |                ],
    |                "resources": {
    |                  "limits": {
    |                    "cpu": "100m",
    |                    "memory": "50Mi"
    |                  },
    |                  "requests": {
    |                    "cpu": "100m",
    |                    "memory": "50Mi"
    |                  }
    |                },
    |                "livenessProbe": {
    |                  "httpGet": {
    |                    "path": "/",
    |                    "port": 9090,
    |                    "scheme": "HTTP"
    |                  },
    |                  "initialDelaySeconds": 30,
    |                  "timeoutSeconds": 30,
    |                  "periodSeconds": 10,
    |                  "successThreshold": 1,
    |                  "failureThreshold": 3
    |                },
    |                "terminationMessagePath": "/dev/termination-log",
    |                "imagePullPolicy": "IfNotPresent"
    |              }
    |            ],
    |            "restartPolicy": "Always",
    |            "terminationGracePeriodSeconds": 30,
    |            "dnsPolicy": "ClusterFirst",
    |            "securityContext": {}
    |          }
    |        }
    |      },
    |      "status": {
    |        "replicas": 1,
    |        "fullyLabeledReplicas": 1,
    |        "observedGeneration": 1
    |      }
    |    }
    |  ]
    |}
    """.stripMargin
  private[this] val rc: String =
    """
    |{
    |  "kind": "ReplicationController",
    |  "apiVersion": "v1",
    |  "metadata": {
    |    "name": "nginx",
    |    "namespace": "default",
    |    "selfLink": "/api/v1/namespaces/default/replicationcontrollers/nginx",
    |    "uid": "effb72cd-3f2f-11e6-9096-fa163ebc2006",
    |    "resourceVersion": "1022681",
    |    "generation": 1,
    |    "creationTimestamp": "2016-07-01T02:03:00Z",
    |    "labels": {
    |      "app": "nginx"
    |    }
    |  },
    |  "spec": {
    |    "replicas": 1,
    |    "selector": {
    |      "app": "nginx"
    |    },
    |    "template": {
    |      "metadata": {
    |        "name": "nginx",
    |        "creationTimestamp": null,
    |        "labels": {
    |          "app": "nginx"
    |        }
    |      },
    |      "spec": {
    |        "containers": [
    |          {
    |            "name": "nginx",
    |            "image": "nginx",
    |            "resources": {
    |              "requests": {
    |                "cpu": "100m"
    |              }
    |            },
    |            "terminationMessagePath": "/dev/termination-log",
    |            "imagePullPolicy": "Always",
    |            "securityContext": {
    |              "privileged": false
    |            }
    |          }
    |        ],
    |        "restartPolicy": "Always",
    |        "terminationGracePeriodSeconds": 30,
    |        "dnsPolicy": "ClusterFirst",
    |        "securityContext": {}
    |      }
    |    }
    |  },
    |  "status": {
    |    "replicas": 1,
    |    "fullyLabeledReplicas": 1,
    |    "observedGeneration": 1
    |  }
    |}
    """.stripMargin

  "ReplicationController should" - {

    "decode replicationController" in {
      decode[KubernetesRC](rc) match {
        case Xor.Right(kubernetesRC) => {
          assertResult("nginx")(kubernetesRC.metadata.name)
        }
        case Xor.Left(parseError) => {
          val logger = LoggerFactory.getLogger("KubernetesRCSpec")
          logger.error(s"Kubernetes RC parseError: $parseError.toString()")
          throw new CirceError(parseError)
        }
      }
    }

    "decode replicationControllers" in {
      decode[KubernetesRCsResponse](rcs) match {
        case Xor.Right(kubernetesRCs) => {
          val _rc = kubernetesRCs.items.filter((rc: KubernetesRC) => {
              rc.metadata.name == "nginx"
            }
          )
          assert(_rc != null)
        }
        case Xor.Left(parseError) => {
          val logger = LoggerFactory.getLogger("KubernetesRCSpec")
          logger.error(s"Kubernetes RCs parseError: $parseError.toString()")
          throw new CirceError(parseError)
        }
      }
    }
  }

}
