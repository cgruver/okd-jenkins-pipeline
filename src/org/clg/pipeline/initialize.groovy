package org.clg.pipeline

def start(def params) {

  openshift.withCluster() {
    openshift.withProject() {
      stage("Apply Pipeline Configs") {
        openshift.apply( openshift.process( "openshift//pipeline-objects" ))
      }
    }
  }
}

return this
