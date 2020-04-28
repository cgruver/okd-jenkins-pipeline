package org.clg.pipeline

def start(def params) {

  openshift.withCluster() {
    openshift.withProject() {
      openshift.raw("label secret ${params.gitSecret} credential.sync.jenkins.openshift.io=true --overwrite")
      def namespace = openshift.project()
      stage('Checkout') {
        try {
          git url: "${params.pipelineCodeGitUrl}", branch: "${params.pipelineCodeGitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        } catch (Exception e) {
          sh "git config http.sslVerify false"
          git url: "${params.pipelineCodeGitUrl}", branch: "${params.pipelineCodeGitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        }
      }
      stage("Apply Pipeline Configs") {
        openshift.apply( openshift.process( "openshift//pipeline-objects" ))
      }
    }
  }
}

return this
