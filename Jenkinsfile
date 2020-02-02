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
	imageName   = "hello-world"
	devProject  = "pipeline-demo-dev"
	testProject = "pipeline-demo-test"

        buildConfigDev = "helloworld"
        isDev = "helloworld"
        
	// Tags
	devTag      = "0.0-0"
	testTag     = "0.0"
	
	// Blue-Green Settings
	destApp     = "helloworld-green"
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


        // Build the OpenShift Image in OpenShift and tag it.
	stage('Build and Tag OpenShift Image') {
	    steps {
	        echo "Building OpenShift container image ${imageName}:${devTag} in project ${devProject}."
		script {
		    openshift.withCluster() {
			openshift.withProject("${devProject}") {
			    openshift.selector("bc", "${buildConfigDev}").startBuild("--wait=true")
			    openshift.tag("${isDev}:latest", "${imageName}:${devTag}")
			}
		    }
		}
	    }
	}

        
    }
}
