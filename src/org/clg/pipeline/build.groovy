package org.clg.pipeline

def start(def params) {

  stage("Launch Maven Agent") {
    node("maven") {
      git url: "${params.pipelineCodeGitUrl}", branch: "${params.pipelineCodeGitBranch}"
      build(params)
    }
  }
}

def build(def params) {
  openshift.withCluster() {
    openshift.withProject() {
      openshift.raw("label secret ${params.gitSecret} credential.sync.jenkins.openshift.io=true --overwrite")
      def namespace = openshift.project()
      stage('Checkout') {
        try {
          git url: "${params.gitUrl}", branch: "${params.gitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        } catch (Exception e) {
          sh "git config http.sslVerify false"
          git url: "${params.gitUrl}", branch: "${params.gitBranch}", credentialsId: "${namespace}-${params.gitSecret}"
        }
      }

      stage('Build From Source') {
        sh "mvn -B -Dmaven.wagon.http.ssl.insecure=true -s /maven-conf/settings.xml -P ocp -DappName=app package"
      }

      stage("Process OpenShift Config") {
        String[] requiredFields = ["readinessProbe", "livelinessProbe", "secretKeyRef", "configMapRef"]
	      String[] positiveIntFields = ["replicas"]
        def config = readYamlFile("./ocp/config.yml", "A config.yml file is required in the path ./ocp/")

	      def err = ""
	      requiredFields.each {
		      if(config["${it}"] == null || config["${it}"] == "") {
			      err << "${it} is a required field\n"
		      }
	      }

	      positiveIntFields.each {
		      if(config["${it}"] != null && config["${it}"] < 0) {
			      err.append("${it} must be a positive integer\n")
		      }
	      }

	      if(err.toString() != "") {
		      throw new RuntimeException(err.toString())
	      }
        def ocPatch = """ patch dc/${params.appName}  -p  '{"spec": {"template": {"spec": {"containers": [{"name": "${params.appName}", "envFrom": null}] }}}}'  """
        openshift.raw(ocPatch)
        ocPatch = """ patch dc/${params.appName}  -p  '{"spec": {"template": {"spec": {"containers": [{"name": "${params.appName}", "envFrom": [{"configMapRef": {"name": "${config.configMapRef}"}}, {"secretRef": {"name": "${config.secretKeyRef}"}}]}]}}}}'  """
        openshift.raw(ocPatch)
        openshift.raw("apply -f ./ocp/dev/${config.configMapRef}.yml")
        openshift.raw("apply -f ./ocp/dev/${config.secretKeyRef}.yml")
      }

			stage('Build & Deploy Image') {
        timeout(time:5, unit:'MINUTES'){
          def pom = readMavenPom file: "pom.xml"
          def appVersion = "${pom.version}"
          def commitVersion = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
          def commitHash = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
          openshift.raw("set env bc/${params.appName}-jvm-docker GIT_REF=${commitHash} GIT_URL=${params.gitUrl} GIT_BRANCH=${params.gitBranch} GIT_COMMIT=${commitVersion}")
          def bc = openshift.selector("bc/${params.appName}-jvm-docker")
          bc.startBuild("--from-dir='target'")
          bc.logs("-f")
          def newIsLabels = openshift.selector("is", "${params.appName}").object()
          newIsLabels.metadata.labels["lastest_commit"] = commitVersion
          newIsLabels.metadata.labels["committer_name"] = env.GIT_COMMITTER_NAME
          newIsLabels.metadata.labels["author"] = 'Jenkins'
          newIsLabels.metadata.labels["latest_version"] = appVersion
          openshift.apply(newIsLabels)
          def dc  = openshift.selector("dc", "${params.appName}")
          dc.rollout().latest()
          dc.rollout().status()
        }
      }
    }
  }
}

def readYamlFile(def filePath, String errMessage) {
	try {
		def data = readYaml file: filePath
    data?.metadata?.remove('namespace')
	  if(data?.metadata?.labels == null && data?.metadata != null) {
		  data?.metadata?.labels = [:]
	  }
		return data
	} catch(FileNotFoundException fnfe) {
		throw new RuntimeException(errMessage)
	}
}
return this
