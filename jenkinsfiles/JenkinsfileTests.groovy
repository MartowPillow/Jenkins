import groovy.json.JsonSlurperClassic

def Failed_stage = 'Start'

pipeline {
    agent any

    options {
        timeout(time: 25, unit: 'MINUTES')
        skipDefaultCheckout true
    }

    stages {

        stage ('Parameters + Setup') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    properties([parameters([booleanParam(defaultValue: true, description: 'Sends email notifications to commiters when a build fails some tests', name: 'Notifications'),
                        choice(choices: ['2017'], name: 'VisualStudio_Version'), choice(choices: ['C:/Program Files (x86)/Microsoft Visual Studio/2017/Professional/MSBuild/15.0/Bin/MSBuild.exe', 'C:/Program Files (x86)/MSBuild/12.0/Bin/MSBuild.exe'], name: 'DEVENV_PATH')])])
                    bat 'set BUILD_ID=dontKillMe'
                }
            }
        }

        stage ('Copy Artifacts') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Copy Artifacts step
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    '''
                    try {
                        copyArtifacts filter: 'paleoscantests/bin/*',   projectName: "${currentBuild.fullProjectName.replace("_UnitTests","_CI")}", selector: upstream(fallbackToLastSuccessful: true)
                        copyArtifacts filter: 'paleoscanproduct/bin/*', projectName: "${currentBuild.fullProjectName.replace("_UnitTests","_CI")}", selector: upstream(fallbackToLastSuccessful: true)
                        copyArtifacts filter: 'calcengine/bin/*',       projectName: "${currentBuild.fullProjectName.replace("_UnitTests","_CI")}", selector: upstream(fallbackToLastSuccessful: true)
                    }
                    catch (Exception e) {
                        println("[Exception] " + e)
                        println("Building Multibranch_CI to create artifacts.")
                        build wait: false, job: "${currentBuild.fullProjectName.replace("_UnitTests","_CI")}"
                        currentBuild.result = 'ABORTED'
                        error('This job will be automatically built when the CI job is done.')
                    }
                }
            }
        }

        stage ('Tests') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat ''' 
                    @PUSHD "%WORKSPACE%"
                    SET QTDIR=J:/qt5-15_msvc
                    @SET PATH=%QTDIR%/qtbase/bin;paleoscanproduct\\bin;%WORKSPACE%/paleoscantests/bin;%PATH%
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Run GoogleTests
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    @ECHO ---- Test job 1 : PaleoScanTests ----
                    paleoscantests\\bin\\PaleoScanTests.exe --gtest_output=xml:testresults/testresult_paleoscan.xml
                    @ECHO.
                    @REM ECHO Current dir : %CD%
                    @REM ECHO.
                    @ECHO ---- Test job 2 : calcengineTest ----
                    @SET PATH=paleoscanproduct\\bin;%PATH%
                    calcengine\\bin\\calcengineTest.exe --gtest_output=xml:testresults/testresult_calcengine.xml
                    @POPD
                    '''
                }
            }
        }

        stage ('Results') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                          Results step
                    @ECHO +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    '''
                    xunit ([
                        GoogleTest (
                            deleteOutputFiles: true, 
                            failIfNotNew: true, 
                            pattern: 'testresults/testresult_*.xml', 
                            skipNoTestFiles: false, 
                            stopProcessingIfError: true
                        )
                    ])
                }
            }
        }

    }

    post {

        failure {
            script {
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
                        else {
                            changes = "No changes in this build, it was manually triggered"
                        }

                        mail subject: 'Build fail',
                            body:   "The latest build of '${currentBuild.fullProjectName.replace("/"," > ").replace("%2F","/")}' failed at step '${Failed_stage}'.\n"+
                                    "You can check out the log of this build here : ${BUILD_URL}console \n\n" + changes,
                            to:emailextrecipients([requestor(),buildUser(),culprits()])
                        slurped_fail = null
                    }
                }
            }
        }

        success {
            script {
                Failed_stage = 'Post'

                def slurped = new JsonSlurperClassic().parse("${BUILD_URL}testReport/api/json".toURL())
                int nb_fail = slurped.failCount
                def tests = slurped.suites.cases
                String failed_tests = "\n"
                if (tests) {
                    for (int i = 0; i < tests.size(); i++) {
                        for (int j = 0; j < tests[i].size(); j++) {
                            status = tests[i].status[j].toString()
                            if (status == "FAILED" || status == "REGRESSION") {
                                failed_tests += tests[i].className[j].toString() + " - " + tests[i].name[j].toString() + " : " + status + '\n'
                            }
                        }
                    }
                }
                if (nb_fail > 0) {
                    Failed_stage = 'Tests'

                    currentBuild.result = "UNSTABLE"
                    mail subject: 'Tests failed',
                        body: "${nb_fail} tests failed in the latest build of '${currentBuild.fullProjectName.replace("/"," > ").replace("%2F","/")}'.\n You can check them out here : ${BUILD_URL}testReport/ .\n\n List of failed tests: " + failed_tests,
                        to:emailextrecipients([requestor(),buildUser(),culprits()])
                }
                slurped = null

                if (params.Notifications && nb_fail > 0) {
                    mail subject: 'Tests failed',
                        body: "${nb_fail} tests failed in the latest build of '${currentBuild.fullProjectName.replace("/"," > ").replace("%2F","/")}'.\n You can check them out here : ${BUILD_URL}testReport/ .\n\n List of failed tests: " + failed_tests,
                        to:emailextrecipients([requestor(),buildUser(),culprits()])
                }
            }
        }

    }
}
