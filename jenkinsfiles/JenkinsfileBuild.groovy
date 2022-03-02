import groovy.json.JsonSlurperClassic

def Failed_stage = 'Start'

pipeline {
    agent any

    options {
        timeout(time: 35, unit: 'MINUTES') 
    }

    stages {

        stage ('Build') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Build Program
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    
                    g++ ./src/main.cpp -Wall -Wextra -o prog
                    '''
                }
            }
        }

        stage ('Execute') {
            steps {
                script {
                    Failed_stage = env.STAGE_NAME
                    bat '''
                    @ECHO.
                    @ECHO.
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO                        Execute Program
                    @ECHO ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
                    @ECHO.
                    
                    src\\prog
                    '''
                }
            }
        }

 

    }
}
