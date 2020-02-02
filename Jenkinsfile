library identifier: "pipeline-library@v1.5",
retriever: modernSCM(
  [
    $class: "GitSCMSource",
    remote: "https://github.com/redhat-cop/pipeline-library.git"
  ]
)


pipeline {
    agent {
	kubernetes {
	    label "maven-skopeo-agent"
	    cloud "openshift"
	    inheritFrom "maven"
	    containerTemplate {
		name "jnlp"
		image "image-registry.openshift-image-registry.svc:5000/jenkins-test-2/jenkins-agent-appdev:latest"
		resourceRequestMemory "2Gi"
		resourceLimitMemory "2Gi"
		resourceRequestCpu "2"
		resourceLimitCpu "2"
	    }
	}
    }

    environment { 
	// Define global variables
	// Set Maven command to always include Nexus Settings
	// NOTE: Somehow an inline pod template in a declarative pipeline
	//       needs the "scl_enable" before calling maven.
	mvnCmd = "source /usr/local/bin/scl_enable && mvn"

	// Images and Projects
	imageName   = "helloworld"
	devProject  = "pipeline-demo-dev"
	testProject = "pipeline-demo-test"

        appName = "helloworld"

	// Tags
	devTag      = "0.0-0"
	testTag     = "0.0"
	
	// Blue-Green Settings
	destApp     = "${appName}-green"
	activeApp   = ""
    }
    
    stages {
        stage('Checkout Source'){
            steps {
                checkout scm

                script {
                    def pom = readMavenPom file: 'pom.xml'
		    def version = pom.version
		    
		    // Set the tag for the development image: version + build number
		    devTag  = "${version}-" + currentBuild.number
		    // Set the tag for the production image: version
		    prodTag = "${version}"
                }
            }
        }

        // Using Maven run the unit tests
	stage('Unit Tests') {
	    steps {
		echo "Running Unit Tests"
		sh "${mvnCmd} test"
	    }
	}

        stage('Build'){
            steps {
                echo "Running Build"
                sh "${mvnCmd} -B clean install -DskipTests=true"
            }
        }

        // Build the OpenShift Image in OpenShift and tag it.
	stage('Build and Tag OpenShift Image') {
            steps{
                echo "Building OpenShift container image ${imageName}:${devTag} in project ${devProject}."

                sh """
                ls target/*
                rm -rf oc-build && mkdir -p oc-build/deployments
                for t in \$(echo "jar;war;ear" | tr ";" "\\n"); do
                  cp -rfv ./target/*.\$t oc-build/deployments/ 2> /dev/null || echo "No \$t files"
                done
                """


	        echo "Building OpenShift container image ${imageName}:${devTag} in project ${devProject}."
	        script {
	            openshift.withCluster() {
	            	openshift.withProject("${devProject}") {
                            def buildConfig = openshift.selector("bc", "${appName}")
	            	    def build = buildConfig.startBuild("--from-dir=oc-build","--wait=true")
                            openshift.tag("${appName}:latest", "${imageName}:${devTag}")
	            	}
	            }
	        }

            }
	}

        stage('Rollout Dev') {
            steps {
                script {
                    openshift.withCluster() {
		        openshift.withProject("${devProject}") {
                            openshift.set("image", "dc/${appName}", "tasks=image-registry.openshift-image-registry.svc:5000/${devProject}/${imageName}:${devTag}")
                            def dc = openshift.selector("dc",appName).object()
			    dc.spec.template.spec.containers[0].env[0].value="${devTag} (${appName}-dev)"
			    openshift.apply(dc)
                            openshift.selector("dc", appName).rollout().latest();

                            def dc_version = dc.status.latestVersion
			    def rc = openshift.selector("rc", "${appName}-${dc_version}").object()

			    echo "Waiting for ReplicationController ${appName}-${dc_version} to be ready"
			    while (rc.spec.replicas != rc.status.readyReplicas) {
			        sleep 5
			        rc = openshift.selector("rc", "${appName}-${dc_version}").object()
			    }
                        }
                    }
                }
            }
        }
    }
}
