# OpenShift Jenkins Pipeline

## Initial Commits!  Not Ready yet.  Do no use.

Actually, it's not too bad at this point.  But, as you can see, there is not much documentation yet...  Just a lot of Groovy and YAML.

### Very terse docs...  more to follow:

If you have not cleaned up your local image cache in a while, do this.  __Note: This will delete all the local containers on your system. So don't do this on a system with running containers!!!__  If this is your local workstation, you may be amazed at the space that you free up.  ;-)

```
docker system prune --all --force
```

Clone this repo:

```
git clone https://github.com/cgruver/okd-jenkins-pipeline.git
cd okd-jenkins-pipeline
```

Set some environment variables:

```
IMAGE_REGISTRY="docker-registry-default.your.domain.org"
MVN_MIRROR_ID="your-maven-mirror"
MVN_MIRROR_NAME="YOUR Maven Central"
MVN_MIRROR_URL="https://nexus.your.domain.org:8443/repository/your-maven-mirror/"
```

Log into your OCP/OKD cluster:

```
oc login -u you https://console.your.openshift.cluster.your.domain.org:8443
docker login -p $(oc whoami -t) -u admin ${IMAGE_REGISTRY}
```

Build the container images for this pipeline:

```
cd Maven-Agent
docker build -t ${IMAGE_REGISTRY}/openshift/maven-3-6-agent:v3.11 .
docker push ${IMAGE_REGISTRY}/openshift/maven-3-6-agent:v3.11
cd ../Ubi-Minimal-Custom-Image
docker build -t ${IMAGE_REGISTRY}/openshift/clg-ubi-minimal:8.1 .
docker push ${IMAGE_REGISTRY}/openshift/clg-ubi-minimal:8.1
cd ..
```

Create a temporary work space:

```
mkdir -p work
cp okd-templates/* work/
cd work
```

Save a copy of the original `jenkins-ephemeral` template...  just in case you need to revert back.

```
oc get template jenkins-ephemeral -n openshift -o yaml --export > jenkinsEphemeralOriginal.yml
oc apply -f jenkinsEphemeralOriginal.yml -n openshift
```

Patch the YAML files with your environment:

```
sed -i "" "s|%%MVN_MIRROR_ID%%|${MVN_MIRROR_ID}|g" ./quarkus-jvm-pipeline-dev.yml
sed -i "" "s|%%MVN_MIRROR_NAME%%|${MVN_MIRROR_NAME}|g" ./quarkus-jvm-pipeline-dev.yml
sed -i "" "s|%%MVN_MIRROR_URL%%|${MVN_MIRROR_URL}|g" ./quarkus-jvm-pipeline-dev.yml
sed -i "" "s|%%MVN_MIRROR_ID%%|${MVN_MIRROR_ID}|g" ./springboot-jvm-pipeline-dev.yml
sed -i "" "s|%%MVN_MIRROR_NAME%%|${MVN_MIRROR_NAME}|g" ./springboot-jvm-pipeline-dev.yml
sed -i "" "s|%%MVN_MIRROR_URL%%|${MVN_MIRROR_URL}|g" ./springboot-jvm-pipeline-dev.yml
sed -i "" "s|jenkins-ephemeral|jenkins-orig-ephemeral|g" ./jenkinsEphemeralOriginal.yml
```

Apply everything:

```
oc apply -f jenkinsEphemeralNew.yml -n openshift
oc apply -f quarkus-jvm-pipeline-dev.yml -n openshift
oc apply -f springboot-jvm-pipeline-dev.yml -n openshift
oc apply -f bitbucket-secret.yml -n pipeline-test
```

Create a test project:

```
oc new-project pipeline-test
```

Deploy a Quarkus application:

```
oc process openshift//quarkus-jvm-pipeline-dev -p APP_NAME=your-app-name -p GIT_REPOSITORY=https://you@your-git.org/path/to/your/quarkus-app.git -p BUILD_SECRET=your-creds -p GIT_BRANCH=your-branch | oc apply -n pipeline-test -f -
```

Deploy a Spring Boot application:

```
oc process openshift//springboot-jvm-pipeline-dev -p APP_NAME=our-app-name -p GIT_REPOSITORY=https://you@your-git.org/path/to/your/spring-boot-app.git -p GIT_BRANCH=your-branch -p BUILD_SECRET=your-creds | oc apply -n pipeline-test -f -
```
