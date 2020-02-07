def appName = "myproject"
def pipelineProject = "jenkins-pipeline-2"
def devProject = "dev-bash"
def testProject = "<TEST_PROJECT>"

pipeline {
    environment { 
        // Maven Command
	mvnCmd = "source /usr/local/bin/scl_enable && mvn"

	// Tags
	devTag      = "0.0-0"
	testTag     = "0.0"
	
	// Blue-Green Settings
	destApp     = "${appName}-green"
	activeApp   = ""
    }

    agent {
	kubernetes {
	    label "maven-skopeo-agent"
	    cloud "openshift"
	    inheritFrom "maven"
	    containerTemplate {
		name "jnlp"
		image "image-registry.openshift-image-registry.svc:5000/${pipelineProject}/jenkins-agent-appdev:latest"
		resourceRequestMemory "2Gi"
		resourceLimitMemory "2Gi"
		resourceRequestCpu "2"
		resourceLimitCpu "2"
	    }
	}
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
                    
                    // example of using passwords from secrets.
                    // withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${pipelineProject}-password-test", passwordVariable: 'PASSWORD']]) {
                    //     // available as an env variable, but will be masked if you try to print it out any which way
                    //     // note: single quotes prevent Groovy interpolation; expansion is by Bourne Shell, which is what you want
                    //     sh 'echo $PASSWORD'
                    //     // also available as a Groovy variable
                    //     echo PASSWORD
                    //     // or inside double quotes for string interpolation
                    //     echo "token is $PASSWORD"

                    //     sh "echo $PASSWORD | sed 's/foo/bar/g'"
                    // }

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
                echo "Copying files for image ${appName}:${devTag} in project ${devProject}."

                sh """
                ls target/*
                rm -rf oc-build && mkdir -p oc-build/deployments
                for t in \$(echo "jar;war;ear" | tr ";" "\\n"); do
                  cp -rfv ./target/*.\$t oc-build/deployments/ 2> /dev/null || echo "No \$t files"
                done
                """


	        echo "Building OpenShift container image ${appName}:${devTag} in project ${devProject}."
	        script {
	            openshift.withCluster() {
	            	openshift.withProject("${devProject}") {
                            def buildConfig = openshift.selector("bc", "${appName}")
	            	    def build = buildConfig.startBuild("--from-dir=oc-build","--wait=true")
                            openshift.tag("${appName}:latest", "${appName}:${devTag}")
	            	}
	            }
	        }

            }
	}

        // Build the httpd image and tag it
        stage('Build httpd Image') {
            steps {
                script {
                    echo "Copying files for httpd container image ${appName}-httpd:${devTag} in project ${devProject}."
                    sh """
                    mkdir -p httpd_files
                    echo 'another test' > ${WORKSPACE}/httpd_files/test.txt
                    curl -v -u admin:r3dh4t1! -k http://nexus-nexus.apps.cluster-ee65.sandbox1895.opentlc.com/repository/demo/test.txt --upload-file ${WORKSPACE}/httpd_files/test.txt
                    """

                    echo "Building Openshift httpd container image ${appName}-httpd:${devTag} in project ${devProject}."
                    
	            openshift.withCluster() {
	                openshift.withProject("${devProject}") {
                            def buildConfig = openshift.selector("bc", "${appName}-httpd")
	            	    def build = buildConfig.startBuild("--wait=true")
                            openshift.tag("${appName}-httpd:latest", "${appName}-httpd:${devTag}")
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
                            echo "Setting container image"
                            openshift.set("image", "dc/${appName}", "${appName}=image-registry.openshift-image-registry.svc:5000/${devProject}/${appName}:${devTag}")
                            echo "Finding DC"

                            //Setting an env value
                            // def dc = openshift.selector("dc",appName).object()
			    // dc.spec.template.spec.containers[0].env[0].value="${devTag} (${appName}-dev)"
			    // openshift.apply(dc)
                            openshift.selector("dc", appName).rollout()
                            
                            echo "Rollout complete"

                            echo "Starting watch"
                            def dc = openshift.selector("dc",appName).object()
                            def dc_version = dc.status.latestVersion
                            echo "Version of dc is ${dc_version}"
			    def rc = null
                            echo "Attempting to get rc ${appName}-${dc_version}"
                            sleep 1
                            while ( rc == null ) {
                                try {
                                    dc = openshift.selector("dc",appName).object()
                                    dc_version = dc.status.latestVersion
                                    echo "Version of dc is ${dc_version}"
                                    rc = openshift.selector("rc", "${appName}-${dc_version}").object()
                                    break
                                } catch( Exception e) {
                                    sleep 1
                                }
                            }

			    echo "Waiting for ReplicationController ${appName}-${dc_version} to be ready"
			    while (rc.spec.replicas != rc.status.readyReplicas) {
			        sleep 5
                                dc = openshift.selector("dc",appName).object()
                                dc_version = dc.status.latestVersion
                                echo "Version of dc is ${dc_version}"
			        rc = openshift.selector("rc", "${appName}-${dc_version}").object()
			    }
                        }
                    }
                }
            }
        }




        stage('Rollout Httpd') {
            steps {
                script {
                    openshift.withCluster() {
		        openshift.withProject("${devProject}") {
                            echo "Setting container image"
                            openshift.set("image", "dc/${appName}-httpd", "${appName}-httpd=image-registry.openshift-image-registry.svc:5000/${devProject}/${appName}-httpd:${devTag}")
                            echo "Finding DC"

                            openshift.selector("dc", "${appName}-httpd").rollout()
                            echo "Rollout complete"

                            echo "Starting watch"
                            def dc = openshift.selector("dc","${appName}-httpd").object()
                            def dc_version = dc.status.latestVersion
                            echo "Version of dc is ${dc_version}"
			    def rc = null
                            echo "Attempting to get rc ${appName}-httpd-${dc_version}"
                            sleep 1
                            while ( rc == null ) {
                                try {
                                    dc = openshift.selector("dc","${appName}-httpd").object()
                                    dc_version = dc.status.latestVersion
                                    echo "Version of dc is ${dc_version}"
                                    rc = openshift.selector("rc", "${appName}-httpd-${dc_version}").object()
                                    break
                                } catch( Exception e) {
                                    sleep 1
                                }
                            }

			    echo "Waiting for ReplicationController ${appName}-httpd-${dc_version} to be ready"
			    while (rc.spec.replicas != rc.status.readyReplicas) {
			        sleep 5
                                dc = openshift.selector("dc","${appName}-httpd").object()
                                dc_version = dc.status.latestVersion
                                echo "Version of dc is ${dc_version}"
			        rc = openshift.selector("rc", "${appName}-httpd-${dc_version}").object()
			    }
                        }
                    }
                }
            }
        }



        
    }
}
