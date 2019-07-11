# Talk: Managing an Akka Cluster on Kubernetes

This repo contains the slides and the sample code of the talk [Managing an Akka Cluster on Kubernetes](https://www.slideshare.net/markusjura/managing-an-akka-cluster-on-kubernetes-118274797).

## Content

* [Slides](slides/managing-akka-cluster-on-kubernetes-2018-08-15.pdf)
* [Sample Scala project](code)

## Demo instructions

1. Install minikube:
    ```
    brew cask install minikube httpie
    ```
2. Start minikube:
    ```
    minikube start
    ```    
3. Enable Kubernetes Ingress addon:
    ```
    minikube addons enable ingress
    ```
4. After a minute, verify that Ingress addon has been started:
    ```
    kubectl get pods -n kube-system | grep nginx-ingress-controller
    ```
5. In order that Kubernetes finds the locally published Docker image of our application run:
    ```
    eval $(minikube docker-env)
    ```
6. Publish the `trip` backend service to the local Docker registry:
    ```
    cd code
    sbt docker:publishLocal
    ```
7. Deploy the backend service, and create Kubernetes service and ingress resource:
    ```
    kubectl apply -f .deployment/outgoing.yml
    kubectl apply -f .deployment/deployment.yml
    ```
8. Verify that backend service is running:
    ```
    kubectl get pod

    NAME                    READY     STATUS    RESTARTS   AGE
    trip-646cddc7b7-46k82   1/1       Running   0          32m
    trip-646cddc7b7-6zf5k   1/1       Running   0          32m
    trip-646cddc7b7-zjtvd   1/1       Running   0          32m
    ```
9. Access backend service via minikube IP address:
    ```
    http POST $(minikube ip)/trip/offer origin=A destination=B

    HTTP/1.1 200 OK
    Connection: keep-alive
    Content-Length: 45
    Content-Type: application/json
    Date: Thu, 04 Oct 2018 05:32:02 GMT
    Server: nginx/1.13.12

    {
        "price": {
            "amount": "10.00",
            "currency": "EUR"
        }
    }
    ```
