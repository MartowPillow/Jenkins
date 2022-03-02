import groovy.json.JsonSlurperClassic

def Failed_stage = 'Start'

pipeline {
    agent any

    options {
        timeout(time: 35, unit: 'MINUTES') 
    }

    stages {

        stage ('Parameters') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    properties([buildDiscarder(logRotator(artifactNumToKeepStr: '2')),
                                parameters([
                                    booleanParam(defaultValue: true, description: 'Sends email notifications to commiters when a build fails or has new warnings', name: 'Notifications'),
                                    booleanParam(defaultValue: true, description: 'Performs a git clean', name: 'Clean'),
                                    choice(choices: ['v141', 'v142'], name: 'Platform_Toolset', description: 'Platform Toolset : v142 (VS 2019) or v141 (VS 2017)'),
                                    choice(choices: ['Release', 'Debug', "Clang_Release"], name: 'Configuration')
                                    ] )
                               ] )
                }
            }
        }

        stage ('CPD') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat "build/jenkinsfiles/steps/cpd.bat"
                }
            }
        }

        stage ('CCM') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat "build/jenkinsfiles/steps/ccm.bat"
                }
            }
        }

        stage ('ThirdPartySync') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat "Third_party_sync.bat"
                }
            }
        }

        stage ('Build') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat "build/jenkinsfiles/steps/build.bat PaleoScan_VS2017.sln ${params.Platform_Toolset} ${params.Configuration}"
                }
            }
        }

        stage ('Archive Artifacts') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''@ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Archive Artifacts step
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.'''
                    archiveArtifacts artifacts: 'paleoscantests/bin/*.exe'
                    archiveArtifacts artifacts: 'paleoscanproduct/bin/*.dll'
                    archiveArtifacts artifacts: 'paleoscanproduct/bin/*.exe'
                    archiveArtifacts artifacts: 'calcengine/bin/*.exe'
                }
            }
        }

        stage ('Results') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++ 
                    @ECHO                        Results step
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    '''
                    step([$class: 'LogParserPublisher', parsingRulesPath: 'C:\\Jenkins\\log_parser_rules.txt', useProjectRule: false])
                    recordIssues(tools: [msBuild(),cpd(pattern: "**/pmd_results.xml"),ccm(pattern: "**/ccm_results.xml")], trendChartType:"TOOLS_ONLY") 
                }
            }
        }

        stage ('Cleanup') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    if(params.Clean)
                    {
                        bat "build/jenkinsfiles/steps/cleanup.bat"
                    }
                }
            }
        }

    }

    post {
        failure {
            script {
                println "Failed stage : " + Failed_stage
                if (params.Notifications) {
                    if (Failed_stage != 'Start' && Failed_stage != 'Parameters' && Failed_stage != 'Post') {
                        def slurped_fail = new JsonSlurperClassic().parse("${BUILD_URL}api/json".toURL())
                        String changes = "List of changes in this build:\n\n"
                        if (slurped_fail.changeSets.items.msg[0]) {
                            for (int i = 0; i < slurped_fail.changeSets.items.msg[0].size(); i++) {
                                changes += "http://192.168.1.153/factory/paleoscan/commit/" + slurped_fail.changeSets.items.commitId[0][i] + '\n'
                                changes += '"' + slurped_fail.changeSets.items.msg[0][i] + '"'
                                changes += " by " + slurped_fail.changeSets.items.author[0][i].fullName + "\n\n"
                            }
                        }
                        else changes = "No changes in this build, it was manually triggered"

                        mail subject: 'Build fail',
                            body:   "The latest build of '${currentBuild.fullProjectName.replace("/"," > ").replace("%2F","/")}' failed at step '${Failed_stage}'.\n"+
                                    "You can check out the log of this build here : ${BUILD_URL}console \n\n" + changes,
                            to:emailextrecipients([requestor(),buildUser(),culprits()])
                        slurped_fail = null
                    }
                }
                if ( Failed_stage == 'Cleanup' ) {
                    println "Just a cleanup error ?"
                    currentBuild.result = "UNSTABLE"
                }
            }
        }

        success {
            script {
                Failed_stage = 'Post'
                if(currentBuild.number == 1) {
                    build wait: false, job: "MultiBranch_UnitTests"
                    sleep(15) //wait for scan to finish (only for first build)
                }
                build wait: false, job: "${currentBuild.fullProjectName.replace("_CI","_UnitTests")}"
                if (params.Notifications) {
                    def slurped = new JsonSlurperClassic().parse("${BUILD_URL}msbuild/api/json".toURL())
                    int nb_tot = slurped.totalSize
                    int nb_new = slurped.newSize
                    int nb_fix = slurped.fixedSize
                    echo "Total="+nb_tot.toString()
                    echo "New="+nb_new.toString()
                    echo "Fixed="+nb_fix.toString()
                    if (nb_new > nb_fix+1) {
                        currentBuild.result = "UNSTABLE"
                        mail subject: 'New warnings',
                            body: "${nb_new} new warnings were detected in the latest build of '${currentBuild.fullProjectName.replace("/"," > ").replace("%2F","/")}'.\n You can check them out here : ${BUILD_URL}msbuild/New .",
                            to:emailextrecipients([requestor(),buildUser(),culprits()])
                    }
                    slurped = null
                }
            }
        }
    }
}
