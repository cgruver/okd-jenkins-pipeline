FROM openshift/jenkins-slave-base-centos7:v3.11

ENV MAVEN_VERSION=3.6 \
    JAVA_HOME=/usr/lib/jvm/jre-openjdk \
    PATH=/opt/maven/bin:${PATH} \
    HOME=/home/build \
    MAVEN_OPTS='-Duser.home=/home/build'

RUN yum -y install java-1.8.0-openjdk-devel && \
    yum -y update && \
    yum -y clean all && \
    wget https://downloads.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz -O /tmp/maven.tar.gz && \
    tar -xzf /tmp/maven.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-3.6.3 /opt/maven && \
    rm -f /tmp/maven.tar.gz && \
    mkdir -p $HOME && \
    mkdir -p /maven-conf && \
    chown -R 1001:0 $HOME && \
    chmod -R g+rw $HOME && \
    chown -R 1001:0 /maven-conf && \
    chmod -R g+rw /maven-conf

VOLUME /maven-conf

USER 1001
