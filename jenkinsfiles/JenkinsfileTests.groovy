import groovy.json.JsonSlurperClassic

def Failed_stage = 'Start'

pipeline {
    agent any

    options {
        timeout(time: 25, unit: 'MINUTES')
    }

    stages {
        stage ('Build Tests') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Build Tests
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.

                    msbuild src\\tests\\tests.sln
                    '''                    
                }
            }
        }

        stage ('Run Tests') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    try {
                        bat ''' 
                        @ECHO.
                        @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                        @ECHO                        Run Tests
                        @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                        @ECHO.
                        
                        src\\tests\\Debug\\tests.exe --gtest_output=xml:testresults.xml
                        '''
                    }
                    catch (err) {
                        echo err.getMessage()
                    }
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
                            pattern: 'testresults.xml', 
                            skipNoTestFiles: false, 
                            stopProcessingIfError: true
                        )
                    ])
                }
            }
        }

    }
}
