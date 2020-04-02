# OpenShift Ephemeral Jenkins Pipeline

This project represents an implementation of an Ephemeral Jenkins Pipeline for production use.  It is intentionally Ephemeral so that users don't have to deal with managing Jenkins.  There are two developer pipelines included, one for Spring Boot applications, and one for Quarkus JMV applications.

## Setting up the pipeline in your OpenShift cluster.

__*This has only been tested on OKD and OCP 3.11*__

This project is composed of the following components:

1. A custom Jenkins Maven Agent which included maven 3.6.3, (you can change this by modifying the `Dockerfile` before building the image).

1. A customized image for running java applications.  The image is based on the Red Hat Universal Base Image.  This image is very compact and therefore perfect for running Quarkus JVM or Spring Boot applications.

1. There are several OpenShift templates that will be installed into the `openshift` NameSpace of your cluster.

    | | |
    |-|-|
    | jenkinsEphemeralNew.yml | This template will replace the out-of-the-box Ephemeral Jenkins template that ships with OKD or OCP.  It includes optimizations for running Jenkins ephemeral. |
    | initialize-pipeline.yml | This template allows deveopers to create an instance of the ephemeral jenkins pipeline in their namespace.  It also creates a pipeline that will initialize two config maps from the pipeline-objects template |
    | pipeline-objects.yml | Creates two config maps.  1. A Pod Template for our customized Jenkins maven agent.  2. A default maven-settings.xml to control maven mirrors. |
    | quarkus-jvm-pipeline-dev.yml | The pipeline for Quarkus JVM applications |
    | springboot-jvm-pipeline-dev.yml | The pipeline for Spring Boot applications |

1. There are two groovy scripts that are used in pipelines.

    | | |
    |-|-|
    | initialize.groovy | This pipeline is run the first time a developer sets up their namespace for Jenkins builds.  It is also run by the developer anytime they need to consume changes to either the maven-settings.xml or the custom jenkins agent Pod Template |
    | build.groovy | This is the script that builds, packages, and deploys Quarkus JVM, or Spring Boot applications |

1. __Note:__ The DeploymentConfigs in the two pipelines include an init-container that creates a Java keystore from the certificates located at: `/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt`  There is an environment variable set for the application container that passes custom JAVA_OPTS to take advantage of the Java keystore.  By setting this up, any self-signed certs that you add to your OpenShift cluster are automatically trusted by your applications.  This is useful for things like WebSphere MQ, or Hashicorp Vault where you want TLS communication channels.

We're going to start by building a couple of container images.  So, If you have not cleaned up your local image cache in a while, do this.  

__Note: This will delete all the local containers on your system. So don't do this on a system with running containers, or if you want to keep your local cache!!!__  

If this is your local workstation, you may be amazed at the space that you free up.  ;-)

    docker system prune --all --force

Now mirror this repository and push it to your own local git server or GitHub account:

__Note: Don't leave your pipeline dependent on this GitHub project.  You never know when I will break it!__

    git clone --mirror https://github.com/cgruver/okd-jenkins-pipeline.git
    cd okd-jenkins-pipeline.git
    git push --mirror https://your.git.url.org/okd-jenkins-pipeline.git
    rm -rf okd-jenkins-pipeline.git
    git clone https://your.git.url.org/okd-jenkins-pipeline.git
    cd okd-jenkins-pipeline

Now we need to set some environment variables:

| | |
|-|-|
| IMAGE_REGISTRY | The URL of your OpenShift cluster image registry |
| MVN_MIRROR_ID | The maven id that you will assign to the mirror in maven-settings.xml |
| MVN_MIRROR_NAME | The human readable name of the mirror |
| MVN_MIRROR_URL | The URL to your local maven nexus i.e. Sonatype or Artifactory |
| GIT_URL | The url to your mirror of this project |
| GIT_BRANCH | the branch that you want your pipelines to pull the groovy scripts from i.e. stable |

    IMAGE_REGISTRY="docker-registry-default.your-cluster.your.domain.org"
    MVN_MIRROR_ID="your-maven-mirror"
    MVN_MIRROR_NAME="YOUR Maven Central"
    MVN_MIRROR_URL="https://nexus.your.domain.org:8443/repository/your-maven-mirror/"
    GIT_URL=https://your.git.url.org/okd-jenkins-pipeline.git
    GIT_BRANCH=master

Log into your OCP/OKD cluster and image registry:

    oc login -u you https://console.your.openshift.cluster.your.domain.org:8443
    docker login -p $(oc whoami -t) -u admin ${IMAGE_REGISTRY}

Build the container images for this pipeline:

    cd Maven-Agent
    docker build -t ${IMAGE_REGISTRY}/openshift/maven-3-6-agent:v3.11 .
    docker push ${IMAGE_REGISTRY}/openshift/maven-3-6-agent:v3.11
    cd ../Ubi-Minimal-Custom-Image
    docker build -t ${IMAGE_REGISTRY}/openshift/jdk-ubi-minimal:8.1 .
    docker push ${IMAGE_REGISTRY}/openshift/jdk-ubi-minimal:8.1
    cd ..

Create a temporary work space:

    mkdir -p work
    cp okd-templates/* work/
    cd work

Save a copy of the original `jenkins-ephemeral` template...  just in case you need to revert back.  __Note on the `sed` command.  If you are not on a Mac OS, then remove the empty `""` after `sed -i`.  Mac OS uses the BSD syntax for `sed`.

    oc get template jenkins-ephemeral -n openshift -o yaml --export > jenkinsEphemeralOriginal.yml
    sed -i "" "s|jenkins-ephemeral|jenkins-orig-ephemeral|g" ./jenkinsEphemeralOriginal.yml
    oc apply -f jenkinsEphemeralOriginal.yml -n openshift
    rm jenkinsEphemeralOriginal.yml

Patch the provided YAML files with your environment:

    for i in $(ls)
    do
      sed -i "" "s|%%MVN_MIRROR_ID%%|${MVN_MIRROR_ID}|g" ./${i}
      sed -i "" "s|%%MVN_MIRROR_NAME%%|${MVN_MIRROR_NAME}|g" ./${i}
      sed -i "" "s|%%MVN_MIRROR_URL%%|${MVN_MIRROR_URL}|g" ./${i}
      sed -i "" "s|%%GIT_URL%%|${GIT_URL}|g" ./${i}
      sed -i "" "s|%%GIT_BRANCH%%|${GIT_BRANCH}|g" ./${i}
    done

Apply everything: (We're putting everything into the `openshift` namespace so it will be globally available.)

    for i in $(ls)
    do
      oc apply -f ${i} -n openshift
    done

## Deploying Applications

1. Create a test project:

       oc new-project pipeline-test

1. Install the pipeline:

       oc process openshift//initialize-pipeline-dev | oc apply -f -

    You will need to wait for Jenkins to fully start up for the first time.

1. Initialize your namespace with the config maps for maven-settings.xml and the maven agent pod template:

       oc start-build initialize-pipeline --follow

1. Create a secret in your namespace with your git credentials for the repository where your test project is located.  You can do this from the OpenShift console, or create a yaml file.

Now, you are ready to create pipelines to build, package, and deploy Quarkus JVM or Spring Boot applications

Deploy a Quarkus JVM pipeline:

    oc process openshift//quarkus-jvm-pipeline-dev -p APP_NAME=your-app-name -p GIT_REPOSITORY=https://you@your-git.org/path/to/your/quarkus-app.git -p BUILD_SECRET=your-creds -p GIT_BRANCH=your-branch | oc apply -n pipeline-test -f -

Deploy a Spring Boot pipeline:

    oc process openshift//springboot-jvm-pipeline-dev -p APP_NAME=our-app-name -p GIT_REPOSITORY=https://you@your-git.org/path/to/your/spring-boot-app.git -p GIT_BRANCH=your-branch -p BUILD_SECRET=your-creds | oc apply -n pipeline-test -f -
